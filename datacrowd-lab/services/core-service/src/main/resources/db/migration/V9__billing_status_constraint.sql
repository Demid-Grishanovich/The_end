-- Приводим существующие данные к валидным значениям
UPDATE projects
SET billing_status = 'UNPAID'
WHERE billing_status IS NULL
   OR billing_status NOT IN ('UNPAID', 'PAID');

-- Устанавливаем корректный тип, дефолт и NOT NULL
ALTER TABLE projects
ALTER COLUMN billing_status TYPE varchar(20),
    ALTER COLUMN billing_status SET DEFAULT 'UNPAID',
    ALTER COLUMN billing_status SET NOT NULL;

-- Добавляем колонку min_answer_seconds если её нет
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS min_answer_seconds int NOT NULL DEFAULT 3;