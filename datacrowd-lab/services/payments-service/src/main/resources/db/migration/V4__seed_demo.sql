-- Demo payments/ledger for reporting views
INSERT INTO payments (id, user_id, project_id, amount_cents, currency, status, stripe_session_id, stripe_payment_intent_id, created_at)
VALUES
    (uuid_generate_v4(), NULL, uuid_generate_v4(), 999, 'USD', 'SUCCEEDED', NULL, NULL, now()),
    (uuid_generate_v4(), NULL, uuid_generate_v4(), 499, 'USD', 'FAILED',    NULL, NULL, now())
    ON CONFLICT DO NOTHING;

-- One demo ledger entry (uses any existing payment id if present)
INSERT INTO ledger (id, project_id, type, amount_cents, ref_payment_id, created_at)
SELECT uuid_generate_v4(), p.project_id, 'DEPOSIT', p.amount_cents, p.id, now()
FROM payments p
    LIMIT 1
ON CONFLICT DO NOTHING;
