-- V4__task_batches_claim_and_total.sql
-- Align schema with TaskBatchEntity (claimed_by_user_id) and ensure total_tasks exists.

ALTER TABLE task_batches
    ADD COLUMN IF NOT EXISTS claimed_by_user_id uuid;

ALTER TABLE task_batches
    ADD COLUMN IF NOT EXISTS total_tasks int NOT NULL DEFAULT 0;

-- status column should exist already, but keep safe
ALTER TABLE task_batches
    ADD COLUMN IF NOT EXISTS status varchar(30) NOT NULL DEFAULT 'NEW';

-- created_at should exist
ALTER TABLE task_batches
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
