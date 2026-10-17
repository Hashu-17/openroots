package storage

import (
	"context"
	"fmt"
	"net"
	"net/url"
	"strconv"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/openroots/ingestion/internal/telemetry"
)

type Store struct {
	pool *pgxpool.Pool
}

func New(ctx context.Context, host, port, database, user, password string, maxConnections int) (*Store, error) {
	parsedPort, err := strconv.ParseUint(port, 10, 16)
	if err != nil {
		return nil, fmt.Errorf("parse database port: %w", err)
	}

	connectionURL := url.URL{
		Scheme: "postgres",
		User:   url.UserPassword(user, password),
		Host:   net.JoinHostPort(host, strconv.FormatUint(parsedPort, 10)),
		Path:   database,
	}
	query := url.Values{}
	query.Set("sslmode", "disable")
	connectionURL.RawQuery = query.Encode()

	poolConfig, err := pgxpool.ParseConfig(connectionURL.String())
	if err != nil {
		return nil, fmt.Errorf("parse database connection config: %w", err)
	}
	if maxConnections > 0 {
		poolConfig.MaxConns = int32(maxConnections)
	}

	pool, err := pgxpool.NewWithConfig(ctx, poolConfig)
	if err != nil {
		return nil, fmt.Errorf("open database pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("connect to TimescaleDB: %w", err)
	}
	return &Store{pool: pool}, nil
}

func (s *Store) Close() {
	s.pool.Close()
}

func (s *Store) InsertReadings(ctx context.Context, deviceID string, readings []telemetry.Reading) error {
	if len(readings) == 0 {
		return nil
	}
	var deviceUUID pgtype.UUID
	if err := deviceUUID.Scan(deviceID); err != nil {
		return fmt.Errorf("parse device UUID: %w", err)
	}

	tx, err := s.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("begin telemetry transaction: %w", err)
	}
	defer func() { _ = tx.Rollback(context.Background()) }()

	for _, reading := range readings {
		var sensorID pgtype.UUID
		if err := sensorID.Scan(reading.SensorID); err != nil {
			return fmt.Errorf("parse sensor UUID %s: %w", reading.SensorID, err)
		}
		_, err := tx.Exec(ctx, `
			INSERT INTO telemetry (sensor_id, value, recorded_at)
			VALUES ($1::uuid, $2, $3)
			ON CONFLICT (sensor_id, recorded_at)
			DO UPDATE SET value = EXCLUDED.value
		`, sensorID, reading.Value, reading.RecordedAt)
		if err != nil {
			return fmt.Errorf("upsert telemetry for sensor %s: %w", reading.SensorID, err)
		}
	}
	result, err := tx.Exec(ctx, `
		UPDATE device
		SET status = 'online', last_seen_at = now()
		WHERE id = $1::uuid
	`, deviceUUID)
	if err != nil {
		return fmt.Errorf("update device last-seen state: %w", err)
	}
	if result.RowsAffected() != 1 {
		return fmt.Errorf("update device last-seen state: device %s was not found", deviceID)
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit telemetry transaction: %w", err)
	}
	return nil
}
