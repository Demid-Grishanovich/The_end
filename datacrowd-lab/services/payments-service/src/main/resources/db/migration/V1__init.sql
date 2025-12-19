-- Placeholder migration for Stage 2 (service must start and Flyway must be able to run)

CREATE TABLE IF NOT EXISTS payments_bootstrap (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);
