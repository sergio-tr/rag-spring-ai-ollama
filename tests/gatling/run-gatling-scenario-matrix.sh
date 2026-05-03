#!/usr/bin/env bash
# Run several Gatling simulations sequentially with conservative defaults (no LLM firestorm).
# Usage: from repo root, with Spring reachable at GATLING_BASE_URL.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export GATLING_BASE_URL="${GATLING_BASE_URL:-http://localhost:9000}"
cd "$ROOT/tests/gatling"
chmod +x ./gradlew 2>/dev/null || true

run() {
  local sim="$1"
  shift
  echo "=== Gatling: $sim ==="
  env "$@" ./gradlew --no-daemon gatlingRun --simulation "$sim"
}

run simulations.ActuatorHealthSimulation GATLING_HEALTH_USERS=4 GATLING_HEALTH_DURATION_SEC=8
run simulations.ActuatorThroughputTiersSimulation \
  GATLING_TIER1_RPS=1 GATLING_TIER2_RPS=4 GATLING_TIER3_RPS=2 \
  GATLING_TIER1_SEC=10 GATLING_TIER2_SEC=12 GATLING_TIER3_SEC=8
run simulations.ProductAuthenticatedSimulation GATLING_PRODUCT_VUS=3 GATLING_PRODUCT_ITERATION_SEC=12
run simulations.ProductUnauthenticatedSimulation GATLING_UNAUTH_VUS=3 GATLING_UNAUTH_ITERATION_SEC=10
run simulations.AuthLoginNegativeSimulation GATLING_AUTH_NEG_VUS=2 GATLING_AUTH_NEG_ITERATION_SEC=8
run simulations.AdminApiSimulation GATLING_ADMIN_API_VUS=2

# Realistic mix (auth + admin/product) — uncomment when Ollama capacity is acceptable:
# run simulations.MixedRealisticSmokeSimulation \
#   GATLING_MIX_SMOKE_VUS=2 GATLING_MIX_SMOKE_DURATION_SEC=45 GATLING_MIX_SMOKE_RAMP_SEC=8

echo "Done. Reports under tests/gatling/build/reports/gatling/"
