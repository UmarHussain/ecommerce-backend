#!/usr/bin/env bash
set -euo pipefail
for service in keycloak user catalog inventory cart order payment; do
  password_name="${service^^}_DB_PASSWORD"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=role_name="${service}_app" --set=db_name="${service}db" --set=app_password="${!password_name}" <<'SQL'
CREATE ROLE :"role_name" LOGIN PASSWORD :'app_password';
CREATE DATABASE :"db_name" OWNER :"role_name";
REVOKE CONNECT ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO :"role_name";
SQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${service}db" --set=role_name="${service}_app" --set=schema_name="$service" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA :"schema_name" AUTHORIZATION :"role_name";
SQL
done
