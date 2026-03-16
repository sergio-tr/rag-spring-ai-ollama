#!/usr/bin/env bash
# Creates default .env for each component (db, observability, rag-service, classifier-service). Use --force to overwrite.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
./create-env-db.sh "$@"
./create-env-observability.sh "$@"
./create-env-rag-service.sh "$@"
./create-env-classifier-service.sh "$@"
echo "Done. Edit the .env files as needed. Run compose from docker/ with --env-file ../db/.env (and --env-file ../observability/.env for obs)."
