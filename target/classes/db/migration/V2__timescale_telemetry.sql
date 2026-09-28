-- V2: physical schema tuning for the IoT domain
-- This migration assumes the base relational schema from V1 already exists.
-- It adds the time-series layer for telemetry and a few practical indexes.

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Convert telemetry into a hypertable for time-series workloads.
SELECT create_hypertable(
    'telemetry',
    'recorded_at',
    if_not_exists => TRUE,
    migrate_data => TRUE
);

-- Time-series access pattern: sensor + timebox
CREATE INDEX idx_project_member_project
    ON project_member(project_id);

CREATE INDEX IF NOT EXISTS idx_telemetry_sensor_time
    ON telemetry (sensor_id, recorded_at DESC);

-- Common dashboard/query pattern: recent readings globally
CREATE INDEX IF NOT EXISTS idx_telemetry_time
    ON telemetry (recorded_at DESC);

-- Supporting relational indexes for operational queries
CREATE INDEX IF NOT EXISTS idx_device_project
    ON device (project_id);

CREATE INDEX IF NOT EXISTS idx_device_group
    ON device (device_group_id);

CREATE INDEX IF NOT EXISTS idx_sensor_device
    ON sensor (device_id);

CREATE INDEX IF NOT EXISTS idx_command_device
    ON command (device_id, sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_device
    ON alert (device_id, triggered_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_user
    ON notification (user_id, read, sent_at DESC);

-- Optional: keep metadata tables explicit and consistent with the intended domain model
-- NOTE: These are not new business tables; they are schema hygiene indexes for the app's entity model.
