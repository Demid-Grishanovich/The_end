-- NOTE (diploma simplification):
-- DB usernames are fixed constants for deterministic init:
-- auth_user, core_user, payments_user.
-- If you change DB users in env, you must also update this file accordingly.

-- Safe for repeated runs: wrap each CREATE ROLE in a try/catch block
DO $do$
BEGIN
BEGIN CREATE ROLE app_read; EXCEPTION WHEN duplicate_object THEN NULL; END;
BEGIN CREATE ROLE app_write; EXCEPTION WHEN duplicate_object THEN NULL; END;
BEGIN CREATE ROLE admin; EXCEPTION WHEN duplicate_object THEN NULL; END;
END
$do$;

GRANT app_write TO auth_user;
GRANT app_write TO core_user;
GRANT app_write TO payments_user;
