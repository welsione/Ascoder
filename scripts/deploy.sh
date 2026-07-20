#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example."
  echo "Please edit .env and set at least one LLM API key, then run this script again."
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed or not available on PATH." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "Docker Compose is not installed." >&2
  exit 1
fi

$COMPOSE up -d --build

APP_PORT=$(grep '^APP_PORT=' .env | tail -n 1 | cut -d '=' -f 2-)
APP_PORT=${APP_PORT:-5173}

echo "Ascoder is starting."
echo "Open: http://localhost:${APP_PORT}"
echo "Logs: $COMPOSE logs -f backend frontend"
