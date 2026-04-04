#!/usr/bin/env bash
# Creates observability/.env from .env.example (OBS_INTERNAL_* ports + host + images).
# After a pull of the repo, check .env.example for new variables or use --force.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_EXAMPLE="$ROOT_DIR/observability/.env.example"
ENV_FILE="$ROOT_DIR/observability/.env"
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
