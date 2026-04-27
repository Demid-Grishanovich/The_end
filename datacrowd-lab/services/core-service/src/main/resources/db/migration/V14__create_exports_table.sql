CREATE TABLE IF NOT EXISTS exports (
                                       id          UUID        PRIMARY KEY,
                                       project_id  UUID        NOT NULL,
                                       dataset_id  UUID        NOT NULL,
                                       format      VARCHAR(10) NOT NULL DEFAULT 'jsonl',
    status      VARCHAR(20) NOT NULL DEFAULT 'READY',
    file_path   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_exports_project_id ON exports (project_id);
CREATE INDEX IF NOT EXISTS idx_exports_dataset_id ON exports (dataset_id);