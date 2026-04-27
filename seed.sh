#!/usr/bin/env bash
# Usage: ./seed.sh
# Inserts dummy courses and enrollments into the running liveklass-postgres container.
set -euo pipefail

COMPOSE_FILE="docker/postgres-compose.yml"
SEED_FILE="docker/seed.sql"
DB_USER="liveklass"
DB_NAME="liveklass"
CONTAINER="liveklass-postgres"

# pick docker compose command
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "docker compose / docker-compose not found" >&2
  exit 1
fi

# ensure container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "Container '$CONTAINER' is not running. Start it first (e.g. ./run-dev.sh)." >&2
  exit 1
fi

echo "Seeding database '$DB_NAME' from $SEED_FILE ..."
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$SEED_FILE"
echo "Done."
