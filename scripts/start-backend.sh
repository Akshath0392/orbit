#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
BACKEND="$ROOT/backend"

# Parse args
RUN_TESTS=false
REBUILD=false
for arg in "$@"; do
  [ "$arg" = "--test" ]    && RUN_TESTS=true
  [ "$arg" = "--rebuild" ] && REBUILD=true
  [ "$arg" = "--build" ]   && REBUILD=true
done

# Load .env if present
if [ -f "$ROOT/.env" ]; then
  export $(grep -v '^#' "$ROOT/.env" | xargs)
fi

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/orbit}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-orbit}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-orbit_secret}"

JAR="$BACKEND/target/orbit-backend-1.0.0.jar"

if $RUN_TESTS; then
  echo "Running backend tests..."
  (cd "$BACKEND" && mvn test -q)
  echo "Backend tests passed."
fi

if $REBUILD || [ ! -f "$JAR" ]; then
  $REBUILD && echo "Rebuilding backend..." || echo "Building backend..."
  (cd "$BACKEND" && mvn clean package -DskipTests -q)
fi

echo "Starting Orbit backend on port 8080..."
exec java -jar "$JAR"
