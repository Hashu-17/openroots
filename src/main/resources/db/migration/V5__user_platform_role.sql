-- Platform-wide role, independent of per-project membership roles.
-- ADMIN may create projects and manage platform roles; USER is the default.
ALTER TABLE "user"
    ADD COLUMN platform_role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE "user"
    ADD CONSTRAINT chk_user_platform_role CHECK (platform_role IN ('USER', 'ADMIN'));
