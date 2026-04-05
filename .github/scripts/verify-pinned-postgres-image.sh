#!/usr/bin/env bash
# Fail if any tracked reference uses a non-canonical pgvector image tag.
# Canonical value must match .github/local/lib/common.sh (RAG_PLATFORM_POSTGRES_IMAGE).
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

PINNED="pgvector/pgvector:0.8.2-pg16-bookworm"
PATHSPECS=(':!.cursor' ':!.github/scripts/verify-pinned-postgres-image.sh' ':!*.plan.md')

# Disallow floating pg16 tag.
if git grep -nF 'pgvector/pgvector:pg16' -- . "${PATHSPECS[@]}" 2>/dev/null | grep -q .; then
  echo "::error::Forbidden postgres image tag (pg16 floating) still present in repository."
  git grep -nF 'pgvector/pgvector:pg16' -- . "${PATHSPECS[@]}" || true
  exit 1
fi

# Every pgvector image reference must use the pinned tag.
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  if [[ "$line" != *"$PINNED"* ]]; then
    echo "::error::Non-pinned pgvector image reference:"
    echo "$line"
    exit 1
  fi
done < <(git grep -n 'pgvector/pgvector:' -- . "${PATHSPECS[@]}" 2>/dev/null || true)

echo "Postgres image references OK (all use ${PINNED})."
