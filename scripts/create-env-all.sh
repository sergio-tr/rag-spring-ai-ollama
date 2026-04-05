#!/usr/bin/env bash
# Creates default .env for each component (db, observability, rag-service, classifier-service, ollama). Use --force to overwrite.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
./create-env-db.sh "$@"
./create-env-observability.sh "$@"
./create-env-rag-service.sh "$@"
./create-env-classifier-service.sh "$@"
./create-env-ollama.sh "$@"
echo "Done. Edit the .env files as needed. Run compose from docker/ with the appropriate --env-file options (see scripts/README.md)."
