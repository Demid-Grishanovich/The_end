-- === username ===
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(64);

UPDATE users
SET username = COALESCE(NULLIF(username, ''), split_part(email, '@', 1))
WHERE username IS NULL OR username = '';

ALTER TABLE users
    ALTER COLUMN username SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS users_username_uq ON users(username);

-- === password_hash ===
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='users' AND column_name='password'
    ) THEN
        EXECUTE 'UPDATE users SET password_hash = password WHERE password_hash IS NULL';
EXECUTE 'ALTER TABLE users DROP COLUMN password';
END IF;
END $$;

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

-- === created_at ===
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE users
SET created_at = NOW()
WHERE created_at IS NULL;

-- === updated_at ===
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE users
SET updated_at = NOW()
WHERE updated_at IS NULL;

-- === status ===
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

UPDATE users
SET status = 'ACTIVE'
WHERE status IS NULL OR status = '';

-- === role/email (на всякий случай) ===
ALTER TABLE users
    ALTER COLUMN role SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS users_email_uq ON users(email);
