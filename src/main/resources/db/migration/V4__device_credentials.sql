CREATE TABLE device_credential (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    secret_hash     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_device_credential_device ON device_credential(device_id);
