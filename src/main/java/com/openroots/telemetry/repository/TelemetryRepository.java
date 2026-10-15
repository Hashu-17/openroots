package com.openroots.telemetry.repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TelemetryRepository {

    private final JdbcTemplate jdbcTemplate;

    public TelemetryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean save(UUID deviceId, String channelName, double value, Instant capturedAt) {
        int rows = jdbcTemplate.update("""
                INSERT INTO telemetry (sensor_id, value, recorded_at)
                SELECT id, ?, ?
                FROM sensor
                WHERE device_id = ? AND channel_name = ?
                ON CONFLICT (sensor_id, recorded_at)
                DO UPDATE SET value = EXCLUDED.value
                """, value, Timestamp.from(capturedAt), deviceId, channelName);
        return rows > 0;
    }

    public List<TelemetryPoint> findAll() {
        return query("""
                SELECT s.device_id, t.recorded_at, s.channel_name, t.value
                FROM telemetry t
                JOIN sensor s ON s.id = t.sensor_id
                ORDER BY t.recorded_at DESC
                """);
    }

    public List<TelemetryPoint> findByDevice(UUID deviceId) {
        return query("""
                SELECT s.device_id, t.recorded_at, s.channel_name, t.value
                FROM telemetry t
                JOIN sensor s ON s.id = t.sensor_id
                WHERE s.device_id = ?
                ORDER BY t.recorded_at DESC
                """, deviceId);
    }

    private List<TelemetryPoint> query(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new TelemetryPoint(
                resultSet.getObject("device_id", UUID.class),
                resultSet.getTimestamp("recorded_at").toInstant(),
                resultSet.getString("channel_name"),
                resultSet.getDouble("value")), parameters);
    }

    public record TelemetryPoint(
            UUID deviceId,
            Instant capturedAt,
            String channelName,
            double value) {
    }
}