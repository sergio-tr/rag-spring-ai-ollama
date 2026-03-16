#!/usr/bin/env bash
# If .env does not exist, copy .env.example and remind to edit.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example. Please edit .env with your values."
else
  echo ".env already exists."
fi
