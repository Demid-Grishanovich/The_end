#!/usr/bin/env bash
set -e

echo "==> Initializing multiple DBs/users..."

# -------------------------------------------------
# 1. USERS / ROLES
# -------------------------------------------------
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$AUTH_DB_USER') THEN
    CREATE ROLE $AUTH_DB_USER LOGIN PASSWORD '$AUTH_DB_PASSWORD';
  END IF;

  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$CORE_DB_USER') THEN
    CREATE ROLE $CORE_DB_USER LOGIN PASSWORD '$CORE_DB_PASSWORD';
  END IF;

  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$PAYMENTS_DB_USER') THEN
    CREATE ROLE $PAYMENTS_DB_USER LOGIN PASSWORD '$PAYMENTS_DB_PASSWORD';
  END IF;
END
\$\$;
EOSQL

# -------------------------------------------------
# 2. DATABASES (ВАЖНО: БЕЗ DO / БЕЗ ТРАНЗАКЦИЙ)
# -------------------------------------------------
create_db () {
  local db="$1"
  local owner="$2"

  echo "==> Creating database $db (if not exists)"
  psql --username "$POSTGRES_USER" --dbname postgres -tc \
    "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1 \
    || psql --username "$POSTGRES_USER" --dbname postgres \
       -c "CREATE DATABASE $db OWNER $owner;"
}

create_db "$AUTH_DB" "$AUTH_DB_USER"
create_db "$CORE_DB" "$CORE_DB_USER"
create_db "$PAYMENTS_DB" "$PAYMENTS_DB_USER"

# -------------------------------------------------
# 3. GRANTS
# -------------------------------------------------
grant_db () {
  local db="$1"
  local usr="$2"

  echo "==> Granting privileges on $db to $usr"
  psql --username "$POSTGRES_USER" --dbname "$db" <<-EOSQL
    GRANT CONNECT, TEMPORARY ON DATABASE $db TO $usr;
    GRANT USAGE, CREATE ON SCHEMA public TO $usr;

    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO $usr;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO $usr;
    GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO $usr;

    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $usr;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $usr;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO $usr;
EOSQL
}

grant_db "$AUTH_DB" "$AUTH_DB_USER"
grant_db "$CORE_DB" "$CORE_DB_USER"
grant_db "$PAYMENTS_DB" "$PAYMENTS_DB_USER"

echo "==> Multi-DB init completed successfully."
