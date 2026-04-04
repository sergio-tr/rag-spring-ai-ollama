#!/usr/bin/env bash
# Fetch OpenAPI JSON from a running Spring Boot instance (springdoc must be enabled).
# Usage (repo root): ./rag-service/scripts/export-openapi.sh [BASE_URL] [OUTPUT_FILE]
# Default OUT: rag-service/target/openapi-export.json (resolved from repo root).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$HERE/../.." && pwd)"
BASE_URL="${1:-http://127.0.0.1:9000}"
BASE_URL="${BASE_URL%/}"
OUT="${2:-$ROOT_DIR/rag-service/target/openapi-export.json}"

mkdir -p "$(dirname "$OUT")"
if ! curl -sfS "$BASE_URL/v3/api-docs" -o "$OUT"; then
  echo "Failed to download OpenAPI from $BASE_URL/v3/api-docs" >&2
  echo "Ensure the backend is up and springdoc is not disabled for this profile." >&2
  exit 1
fi
echo "Wrote $OUT"
