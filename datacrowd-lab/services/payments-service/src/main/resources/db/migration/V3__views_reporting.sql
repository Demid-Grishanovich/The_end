-- Aggregated view for quick reporting (max requirement: VIEW)
CREATE OR REPLACE VIEW v_project_payments_summary AS
SELECT
    project_id,
    currency,
    count(*) AS payments_count,
    sum(amount_cents) FILTER (WHERE status IN ('SUCCEEDED')) AS total_succeeded_cents,
    sum(amount_cents) FILTER (WHERE status IN ('REFUNDED')) AS total_refunded_cents,
    max(created_at) AS last_payment_at
FROM payments
GROUP BY project_id, currency;

-- Ledger balance per project
CREATE OR REPLACE VIEW v_project_ledger_balance AS
SELECT
    project_id,
    sum(amount_cents) AS balance_cents,
    max(created_at) AS last_entry_at
FROM ledger
GROUP BY project_id;
