#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/../.env" ]; then
  export $(grep -v '^#' "$SCRIPT_DIR/../.env" | xargs)
fi

DB_NAME="${SPRING_DATASOURCE_DATABASE:-orbit}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-orbit}"
DB_PASS="${SPRING_DATASOURCE_PASSWORD:-orbit_secret}"

echo "Setting up Orbit database..."

psql postgres -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';" 2>/dev/null && echo "Created user $DB_USER" || echo "User $DB_USER already exists"
psql postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;" 2>/dev/null && echo "Created database $DB_NAME" || echo "Database $DB_NAME already exists"
psql postgres -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;" 2>/dev/null

echo "Done. Flyway migrations run automatically on first backend start."
