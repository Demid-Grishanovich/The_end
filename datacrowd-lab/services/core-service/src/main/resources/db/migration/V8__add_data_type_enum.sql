UPDATE projects
SET data_type = 'TEXT'
WHERE data_type IS NULL
   OR data_type NOT IN ('TEXT', 'IMAGE', 'AUDIO', 'CODE', 'MATH');

ALTER TABLE projects
ALTER COLUMN data_type TYPE varchar(20),
    ALTER COLUMN data_type SET DEFAULT 'TEXT',
    ALTER COLUMN data_type SET NOT NULL;