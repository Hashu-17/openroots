-- V3: fields required by the device registration API

ALTER TABLE device
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(100) NOT NULL DEFAULT 'generic';

ALTER TABLE device
    ADD COLUMN IF NOT EXISTS registered_at TIMESTAMPTZ NOT NULL DEFAULT now();