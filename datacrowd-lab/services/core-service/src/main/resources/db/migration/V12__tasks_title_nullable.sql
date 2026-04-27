-- V13__tasks_title_nullable.sql
-- title was NOT NULL from V1 but runner never sets it.
-- Tasks are identified by their payloadJson content, not a title.
-- Making title optional so bulk insert from runner works correctly.

ALTER TABLE tasks
    ALTER COLUMN title DROP NOT NULL;

-- Also make description nullable if not already (safety)
ALTER TABLE tasks
    ALTER COLUMN description DROP NOT NULL;
