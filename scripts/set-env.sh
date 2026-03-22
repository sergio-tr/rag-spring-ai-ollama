#!/usr/bin/env bash
# Interactive: create .env files for each component from .env.example (only if missing, unless the create-* script supports --force).
# Does not run Docker Compose — use ./scripts/up.sh dev|prod to start the stack.
# Run from repository root: ./scripts/set-env.sh
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=== Create environment files ==="
echo "Answer y to create each .env from its .env.example (only creates if file is missing)."
echo ""

prompt_create() {
  local name="$1"
  local script="$2"
  echo -n "Create $name? [y/N] "
  read -r answer
  case "${answer:-n}" in
    y|Y) "$SCRIPT_DIR/$script";;
    *) echo "Skipped $name.";;
  esac
}

prompt_create "db/.env" "create-env-db.sh"
prompt_create "observability/.env" "create-env-observability.sh"
prompt_create "rag-service/.env" "create-env-rag-service.sh"
prompt_create "classifier-service/.env" "create-env-classifier-service.sh"
prompt_create "ollama/.env" "create-env-ollama.sh"

echo ""
echo "Done. Start the stack with: ./scripts/up.sh dev   or   ./scripts/up.sh prod"
