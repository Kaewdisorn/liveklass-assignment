#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="docker/postgres-compose.yml"
SERVICE_NAME="postgres"
APP_CMD="./mvnw"
APP_ARGS="spring-boot:run"

# pick docker compose command
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "docker compose / docker-compose not found" >&2
  exit 1
fi

echo "Bringing up compose ($COMPOSE_FILE)..."
$DC -f "$COMPOSE_FILE" up -d

echo "Waiting for $SERVICE_NAME to become healthy (timeout 60s)..."
MAX_WAIT=60
SLEEP=2
elapsed=0
while true; do
  CID=$($DC -f "$COMPOSE_FILE" ps -q "$SERVICE_NAME" 2>/dev/null || true)
  if [ -n "$CID" ]; then
    status=$(docker inspect --format='{{.State.Health.Status}}' "$CID" 2>/dev/null || echo "unknown")
    if [ "$status" = "healthy" ]; then
      echo "$SERVICE_NAME is healthy."
      break
    fi
  fi
  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "Timed out waiting for $SERVICE_NAME to be healthy." >&2
    $DC -f "$COMPOSE_FILE" logs --no-color --tail=50 "$SERVICE_NAME"
    exit 1
  fi
  sleep $SLEEP
  elapsed=$((elapsed + SLEEP))
done

echo "Starting application: $APP_CMD $APP_ARGS"
exec $APP_CMD $APP_ARGS