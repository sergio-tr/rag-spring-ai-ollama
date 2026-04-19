#!/usr/bin/env bash
# Fail if any tracked pgvector reference uses a non-canonical tag, or if CI config drifts from
# [.github/ci/postgres-service-image.env](.github/ci/postgres-service-image.env).
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

CANONICAL_FILE="$ROOT/.github/ci/postgres-service-image.env"
if [[ ! -f "$CANONICAL_FILE" ]]; then
  echo "::error::Missing $CANONICAL_FILE"
  exit 1
fi

# shellcheck source=/dev/null
PINNED="$(grep -E '^POSTGRES_SERVICE_IMAGE=' "$CANONICAL_FILE" | head -1 | cut -d= -f2- | tr -d '\r')"
if [[ -z "$PINNED" ]]; then
  echo "::error::POSTGRES_SERVICE_IMAGE is empty in $CANONICAL_FILE"
  exit 1
fi

PATHSPECS=(':!.cursor' ':!.github/scripts/verify-pinned-postgres-image.sh' ':!*.plan.md')

# Disallow floating pg16 tag.
if git grep -nF 'pgvector/pgvector:pg16' -- . "${PATHSPECS[@]}" 2>/dev/null | grep -q .; then
  echo "::error::Forbidden postgres image tag (pg16 floating) still present in repository."
  git grep -nF 'pgvector/pgvector:pg16' -- . "${PATHSPECS[@]}" || true
  exit 1
fi

# Every explicit pgvector image reference must use the canonical pin (same as POSTGRES_SERVICE_IMAGE).
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  if [[ "$line" != *"$PINNED"* ]]; then
    echo "::error::Non-pinned pgvector image reference:"
    echo "$line"
    exit 1
  fi
done < <(git grep -n 'pgvector/pgvector:' -- . "${PATHSPECS[@]}" 2>/dev/null || true)

# Reusable workflow must define the same pin under env.POSTGRES_SERVICE_IMAGE for job services.
if ! grep -q "POSTGRES_SERVICE_IMAGE: ${PINNED}" "$ROOT/.github/workflows/reusable-ci-core.yml"; then
  echo "::error::reusable-ci-core.yml must set env POSTGRES_SERVICE_IMAGE to match .github/ci/postgres-service-image.env (${PINNED})."
  exit 1
fi

echo "Postgres image references OK (canonical ${PINNED})."
