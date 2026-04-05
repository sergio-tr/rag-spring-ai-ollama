#!/usr/bin/env bash
# Strip CR (\r) from all *.sh under the repo so WSL/bash shebangs work (fix: env: 'bash\r': No such file).
# Run from repo root (WSL/Linux): bash ./docker/scripts/normalize-sh-lf.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
count=0
while IFS= read -r -d '' f; do
  tmp="$(mktemp)"
  tr -d '\r' < "$f" > "$tmp" && mv "$tmp" "$f"
  count=$((count + 1))
done < <(find "$ROOT" -name '*.sh' -type f -print0)
echo "Normalized line endings (removed CR) in $count shell scripts under $ROOT"
