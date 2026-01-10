-- V7__datasets_zip_manifest.sql
-- Adds optional support for dataset.zip + manifest.jsonl (multimedia datasets)

ALTER TABLE datasets
    ADD COLUMN IF NOT EXISTS source_type varchar(50) NOT NULL DEFAULT 'FILE',
    ADD COLUMN IF NOT EXISTS manifest_path text;

UPDATE datasets SET source_type = 'FILE' WHERE source_type IS NULL OR source_type = '';

CREATE INDEX IF NOT EXISTS idx_datasets_source_type ON datasets(source_type);
