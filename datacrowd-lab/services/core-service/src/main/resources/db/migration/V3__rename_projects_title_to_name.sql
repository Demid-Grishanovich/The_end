-- V3__rename_projects_title_to_name.sql
-- Fix schema mismatch: ProjectEntity expects "name", but V1 created "title"

DO $$
BEGIN
    -- если колонка title есть, а name нет -> переименовываем
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects' AND column_name = 'title'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects' AND column_name = 'name'
    ) THEN
ALTER TABLE projects RENAME COLUMN title TO name;
END IF;

    -- если колонка name уже есть, но вдруг nullable — подстрахуемся
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects' AND column_name = 'name'
    ) THEN
        -- на всякий случай: если были NULL (не должно быть), заполним
UPDATE projects SET name = 'Untitled project'
WHERE name IS NULL OR btrim(name) = '';

ALTER TABLE projects ALTER COLUMN name SET NOT NULL;
END IF;
END $$;
