#!/usr/bin/env bash
# GitHub-hosted ubuntu-latest runners sometimes ship Microsoft apt mirrors that return
# invalid InRelease metadata (NOSPLIT). Disable them before apt-get update/install.
set -euo pipefail

if [ "${GITHUB_ACTIONS:-}" != "true" ]; then
  exit 0
fi

shopt -s nullglob
for f in /etc/apt/sources.list.d/*; do
  case "$f" in
    *.disabled-by-rag-ci) continue ;;
  esac
  if [ -f "$f" ] && grep -q 'packages.microsoft.com' "$f" 2>/dev/null; then
    echo "Disabling apt source: $f"
    sudo mv "$f" "${f}.disabled-by-rag-ci"
  fi
done
