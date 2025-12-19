-- CORE
CREATE USER core_user WITH PASSWORD 'core_pass';
CREATE DATABASE core_db OWNER core_user;

-- AUTH
CREATE USER auth_user WITH PASSWORD 'auth_pass';
CREATE DATABASE auth_db OWNER auth_user;

-- PAYMENTS
CREATE USER payments_user WITH PASSWORD 'payments_pass';
CREATE DATABASE payments_db OWNER payments_user;

GRANT ALL PRIVILEGES ON DATABASE core_db TO core_user;
GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;
GRANT ALL PRIVILEGES ON DATABASE payments_db TO payments_user;
