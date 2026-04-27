CREATE TABLE IF NOT EXISTS worker_profiles (
                                               worker_id   UUID PRIMARY KEY,
                                               trust_score INTEGER NOT NULL DEFAULT 100
);

-- Seed profiles for any workers who already have answers in the system
INSERT INTO worker_profiles (worker_id, trust_score)
SELECT DISTINCT user_id, 100
FROM answers
WHERE user_id IS NOT NULL
    ON CONFLICT (worker_id) DO NOTHING;