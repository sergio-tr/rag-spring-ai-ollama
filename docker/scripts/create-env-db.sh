#!/usr/bin/env bash
# Creates db/.env from db/.env.example if missing. Use --force to overwrite.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_EXAMPLE="$ROOT_DIR/db/.env.example"
ENV_FILE="$ROOT_DIR/db/.env"
cd "$ROOT_DIR"
if [ ! -f "$ENV_EXAMPLE" ]; then
  echo "Error: $ENV_EXAMPLE not found." >&2
  exit 1
fi
if [ "$1" = "--force" ] || [ ! -f "$ENV_FILE" ]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "Created $ENV_FILE from $ENV_EXAMPLE. Edit with your values if needed."
else
  echo "$ENV_FILE already exists. Use --force to overwrite."
fi
