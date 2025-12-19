-- V2__init_payments_schema.sql
-- Real payments schema (Stage 3.3) + optional ledger
-- Align with core-service style: UUID + uuid-ossp + timestamptz

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1) payments
CREATE TABLE IF NOT EXISTS payments (
                                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- user_id from auth-service (no cross-service FK by design)
    user_id UUID NULL,

    -- project_id from core-service (no cross-service FK by design)
    project_id UUID NOT NULL,

    amount_cents INT NOT NULL CHECK (amount_cents > 0),
    currency VARCHAR(3) NOT NULL CHECK (char_length(currency) = 3),

    -- Minimal set of statuses for MVP; can be extended later
    status VARCHAR(32) NOT NULL CHECK (
                                          status IN ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELED', 'REFUNDED')
    ),

    stripe_session_id VARCHAR(255) NULL,
    stripe_payment_intent_id VARCHAR(255) NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

-- Uniques for Stripe ids (nullable -> unique works as expected in Postgres: multiple NULL allowed)
CREATE UNIQUE INDEX IF NOT EXISTS payments_stripe_session_id_uq
    ON payments (stripe_session_id);

CREATE UNIQUE INDEX IF NOT EXISTS payments_stripe_payment_intent_id_uq
    ON payments (stripe_payment_intent_id);

-- Useful indexes
CREATE INDEX IF NOT EXISTS payments_user_id_idx ON payments (user_id);
CREATE INDEX IF NOT EXISTS payments_project_id_idx ON payments (project_id);
CREATE INDEX IF NOT EXISTS payments_status_idx ON payments (status);
CREATE INDEX IF NOT EXISTS payments_created_at_idx ON payments (created_at);


-- 2) ledger (optional, but recommended by Plan 3.3 for balance/deposit)
-- amount_cents here can be positive/negative depending on type
CREATE TABLE IF NOT EXISTS ledger (
                                      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    project_id UUID NOT NULL,

    type VARCHAR(32) NOT NULL CHECK (
                                        type IN ('DEPOSIT', 'CHARGE', 'REFUND', 'ADJUSTMENT')
    ),

    amount_cents INT NOT NULL CHECK (amount_cents <> 0),

    ref_payment_id UUID NULL REFERENCES payments(id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS ledger_project_id_idx ON ledger (project_id);
CREATE INDEX IF NOT EXISTS ledger_type_idx ON ledger (type);
CREATE INDEX IF NOT EXISTS ledger_created_at_idx ON ledger (created_at);
CREATE INDEX IF NOT EXISTS ledger_ref_payment_id_idx ON ledger (ref_payment_id);


-- 3) cleanup old placeholder table (safe)
DROP TABLE IF EXISTS payments_bootstrap;
