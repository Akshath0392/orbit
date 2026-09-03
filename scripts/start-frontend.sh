#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$SCRIPT_DIR/../frontend"

# Parse args
RUN_TESTS=false
REBUILD=false
for arg in "$@"; do
  [ "$arg" = "--test" ]    && RUN_TESTS=true
  [ "$arg" = "--rebuild" ] && REBUILD=true
done

if [ ! -d "$FRONTEND/node_modules" ]; then
  echo "Installing dependencies..."
  npm install --prefix "$FRONTEND" --silent
fi

if $RUN_TESTS; then
  echo "Running frontend tests..."
  npm test --prefix "$FRONTEND"
  echo "Frontend tests passed."
fi

if [ ! -d "$FRONTEND/dist" ] || $REBUILD; then
  echo "Building frontend..."
  npm run build --prefix "$FRONTEND"
fi

echo "Serving Orbit frontend at http://localhost:3000"
exec npx --prefix "$FRONTEND" serve -s "$FRONTEND/dist" -l 3000
