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
