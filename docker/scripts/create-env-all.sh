#!/usr/bin/env bash
# Creates default .env for each component (db, observability, rag-service, classifier-service, ollama, webapp). Use --force to overwrite.
# After copies, runs sync_env_from_examples.py to append any new keys from .env.example into existing .env files.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
# Use explicit bash so this works when helper scripts are not marked executable (e.g. fresh clones).
bash "$SCRIPT_DIR/create-env-db.sh" "$@"
bash "$SCRIPT_DIR/create-env-observability.sh" "$@"
bash "$SCRIPT_DIR/create-env-rag-service.sh" "$@"
bash "$SCRIPT_DIR/create-env-classifier-service.sh" "$@"
bash "$SCRIPT_DIR/create-env-ollama.sh" "$@"
bash "$SCRIPT_DIR/create-env-webapp.sh" "$@"
if ! python3 "$SCRIPT_DIR/sync_env_from_examples.py"; then
  echo "Warning: sync_env_from_examples.py failed (optional merge of new keys)." >&2
fi
echo "Done. Edit the .env files as needed. Run compose from docker/ with the appropriate --env-file options (see docker/scripts/README.md)."
