#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_JAR="$SCRIPT_DIR/../backend/target/orbit-backend-1.0.0.jar"
FRONTEND_DIR="$SCRIPT_DIR/../frontend"
SIDECAR_DIR="$SCRIPT_DIR/../services/snapshot-sidecar"

# Parse args
RUN_TESTS=false
REBUILD=false
WITH_SIDECAR=false
for arg in "$@"; do
  [ "$arg" = "--test" ]         && RUN_TESTS=true
  [ "$arg" = "--rebuild" ]      && REBUILD=true
  [ "$arg" = "--build" ]        && REBUILD=true
  [ "$arg" = "--with-sidecar" ] && WITH_SIDECAR=true
  [ "$arg" = "--snapshots" ]    && WITH_SIDECAR=true
done

cleanup() {
  echo ""
  echo "Stopping Orbit..."
  kill "$BACKEND_PID" "$FRONTEND_PID" "$SIDECAR_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" "$SIDECAR_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Load .env if present. Use `set -a` so every assignment is auto-exported,
# and `source` (not xargs) so values with spaces, '=' or special chars work.
if [ -f "$SCRIPT_DIR/../.env" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$SCRIPT_DIR/../.env"
  set +a
fi

# --- Configurable hosts & ports (env-driven, with sensible local defaults) ---
ORBIT_HOST="${ORBIT_HOST:-127.0.0.1}"
ORBIT_BACKEND_PORT="${ORBIT_BACKEND_PORT:-8080}"
ORBIT_FRONTEND_PORT="${ORBIT_FRONTEND_PORT:-3000}"
ORBIT_SIDECAR_PORT="${ORBIT_SIDECAR_PORT:-3001}"

# Derived URLs — override these directly in .env to point at a non-local host
# (e.g. an ngrok tunnel, a staging FE, or a containerised sidecar).
ORBIT_BACKEND_URL="${ORBIT_BACKEND_URL:-http://${ORBIT_HOST}:${ORBIT_BACKEND_PORT}}"
ORBIT_FRONTEND_URL="${ORBIT_FRONTEND_URL:-http://${ORBIT_HOST}:${ORBIT_FRONTEND_PORT}}"
ORBIT_PUBLIC_BASE_URL="${ORBIT_PUBLIC_BASE_URL:-${ORBIT_FRONTEND_URL}}"
SNAPSHOT_SIDECAR_URL="${SNAPSHOT_SIDECAR_URL:-http://${ORBIT_HOST}:${ORBIT_SIDECAR_PORT}}"

# Kill anything already on these ports
echo "Freeing ports ${ORBIT_BACKEND_PORT} and ${ORBIT_FRONTEND_PORT}..."
lsof -ti :${ORBIT_BACKEND_PORT} | xargs kill -9 2>/dev/null || true
lsof -ti :${ORBIT_FRONTEND_PORT} | xargs kill -9 2>/dev/null || true
if $WITH_SIDECAR; then
  echo "Freeing port ${ORBIT_SIDECAR_PORT} (snapshot sidecar)..."
  lsof -ti :${ORBIT_SIDECAR_PORT} | xargs kill -9 2>/dev/null || true
fi
sleep 1

# Install frontend deps if missing
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
  echo "Installing frontend deps..."
  npm install --prefix "$FRONTEND_DIR" --silent
fi

# Run tests before starting (--test flag)
if $RUN_TESTS; then
  echo "Running backend tests..."
  (cd "$SCRIPT_DIR/../backend" && mvn test -q)
  echo "Backend tests passed."

  echo "Running frontend tests..."
  npm test --prefix "$FRONTEND_DIR"
  echo "Frontend tests passed."
fi

# Build backend jar if missing or --rebuild requested
if $REBUILD || [ ! -f "$BACKEND_JAR" ]; then
  $REBUILD && echo "Rebuilding backend..." || echo "Building backend (first time, ~1 min)..."
  (cd "$SCRIPT_DIR/../backend" && mvn clean package -DskipTests -q)
fi

# --- Start snapshot sidecar (Playwright) — opt-in via --with-sidecar ---
if $WITH_SIDECAR; then
  if [ ! -d "$SIDECAR_DIR/node_modules" ]; then
    echo "Installing snapshot-sidecar deps (one-off, pulls Chromium ~300 MB)..."
    (cd "$SIDECAR_DIR" && npm install --silent)
  fi
  echo "Starting snapshot sidecar on ${SNAPSHOT_SIDECAR_URL} ..."
  PORT=${ORBIT_SIDECAR_PORT} npm --prefix "$SIDECAR_DIR" start > /tmp/orbit-snapshot-sidecar.log 2>&1 &
  SIDECAR_PID=$!
  # Tell the backend to use the sidecar instead of the mock renderer.
  export SNAPSHOT_RENDERER="${SNAPSHOT_RENDERER:-http}"
  export SNAPSHOT_SIDECAR_URL
  export ORBIT_FRONTEND_URL
  export ORBIT_PUBLIC_BASE_URL
fi

# --- Start backend ---
echo "Starting backend on ${ORBIT_BACKEND_URL} ..."
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/orbit}" \
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-orbit}" \
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-orbit_secret}" \
SERVER_PORT="${ORBIT_BACKEND_PORT}" \
java -jar "$BACKEND_JAR" > /tmp/orbit-backend.log 2>&1 &
BACKEND_PID=$!

# Wait for backend to be ready
echo -n "Waiting for backend"
for i in $(seq 1 30); do
  if curl -s "${ORBIT_BACKEND_URL}/api/v1/auth/login" -X POST \
      -H "Content-Type: application/json" \
      -d '{"email":"x","password":"x"}' 2>/dev/null | grep -q "error\|token"; then
    echo " ready"
    break
  fi
  echo -n "."
  sleep 2
done

# --- Optional demo data (SEED_DEMO_DATA=true) ---
# Applied after the backend is up so Flyway has created/updated the schema.
# The seed file is idempotent (guarded inserts) — re-runs are no-ops.
if [ "${SEED_DEMO_DATA:-false}" = "true" ]; then
  echo "Loading demo dataset (SEED_DEMO_DATA=true)..."
  PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-orbit_secret}" \
  psql -h "${SPRING_DATASOURCE_HOST:-localhost}" -p "${SPRING_DATASOURCE_PORT:-5432}" \
       -U "${SPRING_DATASOURCE_USERNAME:-orbit}" -d "${SPRING_DATASOURCE_DATABASE:-orbit}" \
       -v ON_ERROR_STOP=1 -q -f "$SCRIPT_DIR/seed-demo.sql" \
    && echo "Demo dataset loaded." \
    || echo "WARNING: demo dataset load failed (see above)."
fi

# --- Start frontend dev server (with /api proxy) ---
echo "Starting frontend dev server on ${ORBIT_FRONTEND_URL} ..."
PORT=${ORBIT_FRONTEND_PORT} npm --prefix "$FRONTEND_DIR" run dev > /tmp/orbit-frontend.log 2>&1 &
FRONTEND_PID=$!

# Wait for Vite to be ready
for i in $(seq 1 15); do
  sleep 1
  if grep -q "Local:" /tmp/orbit-frontend.log 2>/dev/null; then
    break
  fi
done

echo ""
echo "  ┌──────────────────────────────────────────────────┐"
echo "  │  Orbit is running                                 │"
echo "  │                                                   │"
printf "  │  App:     %-40s│\n" "${ORBIT_FRONTEND_URL}"
printf "  │  API:     %-40s│\n" "${ORBIT_BACKEND_URL}"
printf "  │  Swagger: %-40s│\n" "${ORBIT_BACKEND_URL}/swagger-ui.html"
if $WITH_SIDECAR; then
printf "  │  Sidecar: %-40s│\n" "${SNAPSHOT_SIDECAR_URL}"
fi
echo "  │                                                   │"
echo "  │  Login:   admin@orbit.io / see backend log for the generated password  │"
echo "  └──────────────────────────────────────────────────┘"
echo ""
echo "  Logs:  tail -f /tmp/orbit-backend.log"
echo "         tail -f /tmp/orbit-frontend.log"
if $WITH_SIDECAR; then
echo "         tail -f /tmp/orbit-snapshot-sidecar.log"
fi
echo ""
echo "  Press Ctrl+C to stop."

wait "$BACKEND_PID" "$FRONTEND_PID" ${SIDECAR_PID:+"$SIDECAR_PID"}
