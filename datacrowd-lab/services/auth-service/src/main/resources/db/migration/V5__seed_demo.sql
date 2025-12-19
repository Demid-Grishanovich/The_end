-- Demo users (password hashes generated elsewhere; keep as placeholders if you don't want real users)
-- If you already have unique constraints, ON CONFLICT keeps it safe.

INSERT INTO users (id, email, username, password_hash, role, status, created_at, updated_at)
VALUES
    (uuid_generate_v4(), 'demo_admin@example.com', 'demo_admin', '$2a$10$placeholderhashplaceholderhashplaceholderha', 'ADMIN', 'ACTIVE', now(), now()),
    (uuid_generate_v4(), 'demo_user@example.com',  'demo_user',  '$2a$10$placeholderhashplaceholderhashplaceholderha', 'USER',  'ACTIVE', now(), now())
    ON CONFLICT DO NOTHING;
