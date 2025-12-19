-- Add updated_at if missing
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

-- Trigger function to keep updated_at fresh
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_set_updated_at ON users;

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Masking PII via VIEW
CREATE OR REPLACE VIEW v_users_masked AS
SELECT
    id,
    username,
    role,
    status,
    created_at,
    updated_at,
    (left(email, 2) || '****@' || split_part(email, '@', 2)) AS email_masked
FROM users;
