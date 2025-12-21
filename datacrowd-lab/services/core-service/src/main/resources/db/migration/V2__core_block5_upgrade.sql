-- V2__core_block5_upgrade.sql
-- Добавляем поля и таблицы, нужные для Блока 5 (Plan.txt)

-- 1) projects: owner + billing/quota + status + reviewers_count + data_type
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS owner_user_id uuid,
    ADD COLUMN IF NOT EXISTS status varchar(50) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS reviewers_count int NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS data_type varchar(50) NOT NULL DEFAULT 'GENERIC',
    ADD COLUMN IF NOT EXISTS billing_status varchar(50) NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN IF NOT EXISTS task_quota int NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_projects_owner_user_id ON projects(owner_user_id);

-- 2) datasets: source_path + status + total_items
ALTER TABLE datasets
    ADD COLUMN IF NOT EXISTS source_path text,
    ADD COLUMN IF NOT EXISTS status varchar(50) NOT NULL DEFAULT 'NEW',
    ADD COLUMN IF NOT EXISTS total_items int NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_datasets_project_id ON datasets(project_id);

-- 3) task_batches
CREATE TABLE IF NOT EXISTS task_batches (
                                            id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id uuid NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    status varchar(50) NOT NULL DEFAULT 'NEW',
    claimed_by_user_id uuid NULL,
    created_at timestamptz NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_task_batches_dataset_id ON task_batches(dataset_id);

-- 4) tasks: batch_id + payload_json + lock поля
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS batch_id uuid REFERENCES task_batches(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS payload_json text,
    ADD COLUMN IF NOT EXISTS locked_by_user_id uuid NULL,
    ADD COLUMN IF NOT EXISTS locked_at timestamptz NULL;

CREATE INDEX IF NOT EXISTS idx_tasks_batch_id ON tasks(batch_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
