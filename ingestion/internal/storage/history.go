package storage

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgtype"
)

type HistoryCursor struct {
	RecordedAt time.Time
	SensorID   string
}

type HistoryReading struct {
	DeviceID   string    `json:"deviceId"`
	SensorID   string    `json:"sensorId"`
	Channel    string    `json:"channelName"`
	Value      float64   `json:"value"`
	RecordedAt time.Time `json:"recordedAt"`
}

func (s *Store) History(
	ctx context.Context,
	deviceID string,
	from time.Time,
	to time.Time,
	after *HistoryCursor,
	limit int,
) ([]HistoryReading, error) {
	var deviceUUID pgtype.UUID
	if err := deviceUUID.Scan(deviceID); err != nil {
		return nil, fmt.Errorf("parse device UUID: %w", err)
	}

	query := `
		SELECT s.device_id, s.id, s.channel_name, t.value, t.recorded_at
		FROM telemetry t
		JOIN sensor s ON s.id = t.sensor_id
		WHERE s.device_id = $1::uuid
		  AND t.recorded_at >= $2
		  AND t.recorded_at < $3
	`
	args := []any{deviceUUID, from, to}
	if after != nil {
		var sensorUUID pgtype.UUID
		if err := sensorUUID.Scan(after.SensorID); err != nil {
			return nil, fmt.Errorf("parse history cursor sensor UUID: %w", err)
		}
		query += " AND (t.recorded_at, s.id) > ($4, $5::uuid)\n"
		args = append(args, after.RecordedAt, sensorUUID)
	}
	args = append(args, limit)
	query += fmt.Sprintf(" ORDER BY t.recorded_at ASC, s.id ASC LIMIT $%d", len(args))

	rows, err := s.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query telemetry history: %w", err)
	}
	defer rows.Close()

	readings := make([]HistoryReading, 0, limit)
	for rows.Next() {
		var deviceID pgtype.UUID
		var sensorID pgtype.UUID
		var channel string
		var value float64
		var recordedAt time.Time
		if err := rows.Scan(&deviceID, &sensorID, &channel, &value, &recordedAt); err != nil {
			return nil, fmt.Errorf("scan telemetry history row: %w", err)
		}
		readings = append(readings, HistoryReading{
			DeviceID:   deviceID.String(),
			SensorID:   sensorID.String(),
			Channel:    channel,
			Value:      value,
			RecordedAt: recordedAt.UTC(),
		})
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate telemetry history rows: %w", err)
	}
	return readings, nil
}
