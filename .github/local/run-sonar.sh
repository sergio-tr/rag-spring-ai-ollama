#!/usr/bin/env bash
# Local parity: Sonar job (same script body as historical ci-like-sonar.sh).
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ci-like-sonar.sh" "$@"
