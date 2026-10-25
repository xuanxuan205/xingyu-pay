#!/bin/sh
set -eu

cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required: https://docs.docker.com/engine/install/"
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  chmod 600 .env
  echo "Created .env. Fill DB_PASSWORD, ADMIN_TOKEN, OPENAPI_SECRET, MAIL_* first."
  exit 0
fi

docker compose up -d --build
