-- Добавляем колонку password_hash, если её ещё нет
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Если раньше была колонка password, переносим данные (если есть)
UPDATE users
SET password_hash = "password"
WHERE password_hash IS NULL AND "password" IS NOT NULL;

-- Делаем password_hash обязательной
ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

-- Удаляем старую колонку password, если она есть
ALTER TABLE users
DROP COLUMN IF EXISTS "password";
