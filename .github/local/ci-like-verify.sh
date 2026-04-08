#!/usr/bin/env bash
# Deprecated name — use run-ci-core.sh.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-ci-core.sh" "$@"
