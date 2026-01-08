-- Block 7: Worker flow + review flow
-- Only indexes (safe to run multiple times)

CREATE INDEX IF NOT EXISTS idx_tasks_status_locked
    ON tasks (status, locked_by_user_id, locked_at);

CREATE INDEX IF NOT EXISTS idx_answers_status_created
    ON answers (status, created_at);

CREATE INDEX IF NOT EXISTS idx_reviews_answer_id
    ON reviews (answer_id);
