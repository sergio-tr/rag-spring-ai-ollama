#!/usr/bin/env bash
# Run local parity for a dev-equivalent PR gate: core → integration → e2e → sonar (if SONAR_TOKEN set).
set -euo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "==> run-ci-core"
"${D}/run-ci-core.sh" "$@"
echo "==> run-integration"
"${D}/run-integration.sh"
echo "==> run-e2e-fullstack"
"${D}/run-e2e-fullstack.sh"
if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "WARN: SONAR_TOKEN unset - skipping run-sonar.sh (required in CI)."
else
  echo "==> run-sonar"
  "${D}/run-sonar.sh"
fi
