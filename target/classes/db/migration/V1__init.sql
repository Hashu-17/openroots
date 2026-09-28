-- Phase 3: PostgreSQL schema derived from the domain model

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE "user" (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Many-to-many: users <-> projects, with role for future auth (Phase 4)
CREATE TABLE project_member (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    project_id      UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    role            VARCHAR(50) NOT NULL DEFAULT 'owner',
    UNIQUE (user_id, project_id)
);

CREATE TABLE device_group (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL
);

CREATE TABLE gateway (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    protocol        VARCHAR(50)
);

CREATE TABLE firmware (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version         VARCHAR(50) NOT NULL,
    binary_url      TEXT,
    released_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE device (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    device_group_id     UUID REFERENCES device_group(id) ON DELETE SET NULL,
    gateway_id          UUID REFERENCES gateway(id) ON DELETE SET NULL,
    firmware_id         UUID REFERENCES firmware(id) ON DELETE SET NULL,
    name                VARCHAR(255) NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'offline',
    last_seen_at        TIMESTAMPTZ
);

CREATE INDEX idx_device_project ON device(project_id);
CREATE INDEX idx_device_group ON device(device_group_id);

CREATE TABLE sensor (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    channel_name    VARCHAR(255) NOT NULL,
    unit            VARCHAR(50),

    UNIQUE (device_id, channel_name)  -- each device has unique channel names
);

CREATE INDEX idx_sensor_device ON sensor(device_id);

-- High-volume, append-only. Consider partitioning by recorded_at later (Phase 3 note).
CREATE TABLE telemetry (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    sensor_id       UUID NOT NULL REFERENCES sensor(id) ON DELETE CASCADE,
    value           DOUBLE PRECISION NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (sensor_id, recorded_at)  -- composite PK for time-series uniqueness
);

CREATE INDEX idx_telemetry_sensor_time ON telemetry(sensor_id, recorded_at DESC);

CREATE TABLE command (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    type            VARCHAR(100) NOT NULL,
    payload         JSONB,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_command_device ON command(device_id);

CREATE TABLE alert (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    rule            TEXT NOT NULL,
    severity        VARCHAR(50) NOT NULL DEFAULT 'info' CHECK (severity IN ('info', 'low', 'medium', 'high', 'critical')),
    triggered_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_device ON alert(device_id);

CREATE TABLE notification (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id        UUID NOT NULL REFERENCES alert(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    channel         VARCHAR(50) NOT NULL DEFAULT 'in_app',
    read            BOOLEAN NOT NULL DEFAULT false,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_user ON notification(user_id, read);
