ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS task_quota int NOT NULL DEFAULT 0;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NULL;

CREATE INDEX IF NOT EXISTS payments_updated_at_idx ON payments(updated_at);
