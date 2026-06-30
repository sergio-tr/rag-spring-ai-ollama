#!/usr/bin/env bash
# DEV_REQUIRED local parity for PRs targeting dev (ci.yml → reusable-ci-core.yml).
# Does not run Sonar, Docker image build, or Gatling (MAIN_REQUIRED / NIGHTLY lanes).
#
# Usage:
#   .github/local/run-dev-pr-gate.sh
#   M1_EVIDENCE_DIR=/path/to/logs .github/local/run-dev-pr-gate.sh
#   DEV_GATE_SKIP_E2E=1 .github/local/run-dev-pr-gate.sh   # unit + integration only
#   DEV_GATE_RUN_API_SMOKE=1 .github/local/run-dev-pr-gate.sh  # needs Docker + Spring for API lane
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

EVIDENCE_DIR="${REPO_ROOT}/docs/evidence/m1-ci-e2e-gate"
LOG_ROOT="${M1_EVIDENCE_DIR:-${EVIDENCE_DIR}/logs/local-gate-$(date -u +%Y%m%dT%H%M%SZ)}"
RESULTS_TSV="${EVIDENCE_DIR}/results.tsv"
SUMMARY_MD="${EVIDENCE_DIR}/LOCAL_GATE_SUMMARY.md"
START_EPOCH="$(date +%s)"
FAIL_STEP=""

mkdir -p "${LOG_ROOT}" "${EVIDENCE_DIR}"

if [[ ! -f "${RESULTS_TSV}" ]] || ! grep -q '^step' "${RESULTS_TSV}" 2>/dev/null; then
  printf 'step\tstatus\tduration_seconds\tlog\tskips\n' > "${RESULTS_TSV}"
fi

log() { printf '[dev-pr-gate] %s\n' "$*"; }

count_skips() {
  local log_path="$1"
  if [[ ! -f "${log_path}" ]]; then
    echo "0"
    return
  fi
  {
    grep -Eo '(^|[[:space:]])[0-9]+[[:space:]]+skipped' "${log_path}" | grep -Eo '[0-9]+' || true
    grep -Eo 'Skipped:[[:space:]]+[0-9]+' "${log_path}" | grep -Eo '[0-9]+' || true
  } | awk '{s+=$1} END {print s+0}'
}

append_result() {
  printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$5" >> "${RESULTS_TSV}"
}

write_summary() {
  local status="$1"
  local total=$(($(date +%s) - START_EPOCH))
  {
    echo "# DEV PR gate (local)"
    echo
    echo "- Branch policy: work on \`eval-models-and-presets\`; PR target \`dev\`."
    echo "- Status: \`${status}\`"
    echo "- Duration (s): \`${total}\`"
    echo "- Logs: \`${LOG_ROOT}\`"
    echo "- Results: \`${RESULTS_TSV}\`"
    if [[ -n "${FAIL_STEP}" ]]; then
      echo "- Failed step: \`${FAIL_STEP}\`"
    fi
    echo
    echo "See \`PR_DEV_GATE.md\` and \`LOCAL_PARITY_GAPS.md\` in the same evidence folder."
  } > "${SUMMARY_MD}"
}

finish() {
  local code=$?
  if [[ "${code}" -eq 0 ]]; then
    write_summary "PASSED"
    log "PASSED — summary: ${SUMMARY_MD}"
  else
    write_summary "FAILED"
    log "FAILED at: ${FAIL_STEP:-unknown} — summary: ${SUMMARY_MD}"
  fi
  exit "${code}"
}
trap finish EXIT

run_step() {
  local name="$1"
  shift
  local log_path="${LOG_ROOT}/${name}.log"
  local start
  start="$(date +%s)"
  log "START ${name}"
  set +e
  (
    cd "${REPO_ROOT}"
    "$@"
  ) 2>&1 | tee "${log_path}"
  local status=${PIPESTATUS[0]}
  set -e
  local duration skips
  duration=$(($(date +%s) - start))
  skips="$(count_skips "${log_path}")"
  if [[ "${status}" -ne 0 ]]; then
    FAIL_STEP="${name}"
    append_result "${name}" "FAILED(${status})" "${duration}" "${log_path}" "${skips}"
    return "${status}"
  fi
  append_result "${name}" "PASSED" "${duration}" "${log_path}" "${skips}"
  log "PASS ${name} (${duration}s, skips=${skips})"
}

record_skipped() {
  local name="$1"
  local reason="$2"
  append_result "${name}" "SKIPPED(${reason})" "0" "n/a" "0"
  log "SKIP ${name} (${reason})"
}

require_docker() {
  if ! docker info >/dev/null 2>&1; then
    log "ERROR: Docker is required for this step (integration/E2E). Start Docker or set DEV_GATE_SKIP_E2E=1."
    return 1
  fi
}

run_step "compose-structural-guard" bash -lc \
  'python3 -m pip install -q pyyaml && bash ./docker/scripts/create-env-all.sh --force && python3 docker/scripts/compose_guard.py --only-rules image_forbidden,yaml_error,build_invalid,build_missing_context,build_missing_dockerfile'

run_step "backend-verify" "${SCRIPT_DIR}/run-ci-core.sh" --stop-after

run_step "classifier-pytest" bash -lc 'cd classifier-service && pip install -q -r requirements.txt && pytest tests/ -v'

run_step "webapp-npm-install" bash -lc 'cd webapp && npm install --no-audit --no-fund'
run_step "webapp-lint" bash -lc 'cd webapp && npm run lint'
run_step "webapp-typecheck" bash -lc 'cd webapp && npm run typecheck'
run_step "webapp-e2e-guard" bash -lc 'cd webapp && npm run test:e2e:guard-unit'
run_step "webapp-test-coverage" bash -lc 'cd webapp && npm run test:coverage'
run_step "webapp-build" bash -lc 'cd webapp && npm run build'
run_step "webapp-doc" bash -lc 'cd webapp && npm run doc'

if require_docker; then
  run_step "integration-strict" bash -lc \
    'INTEGRATION_STRICT=1 INTEGRATION_FAIL_ON_UNREACHABLE=1 PYTEST_LOG_PATH=/tmp/pytest-integration-dev-gate.log .github/local/run-integration-ci-like.sh'

  run_step "integration-classifier-required" bash -lc \
    'INTEGRATION_STRICT=1 INTEGRATION_FAIL_ON_UNREACHABLE=1 INTEGRATION_REQUIRE_CLASSIFIER=1 RAG_CI_REUSE_POSTGRES=1 PYTEST_LOG_PATH=/tmp/pytest-integration-classifier-dev-gate.log .github/local/run-integration-ci-like.sh --reuse-postgres'
elif [[ "${DEV_GATE_SKIP_INTEGRATION:-0}" == "1" ]]; then
  record_skipped "integration-strict" "DEV_GATE_SKIP_INTEGRATION"
  record_skipped "integration-classifier-required" "DEV_GATE_SKIP_INTEGRATION"
else
  record_skipped "integration-strict" "docker-unavailable"
  record_skipped "integration-classifier-required" "docker-unavailable"
  FAIL_STEP="docker-unavailable-integration"
  exit 1
fi

if [[ "${DEV_GATE_SKIP_E2E:-0}" != "1" ]]; then
  if require_docker; then
    run_step "e2e-smoke-ci-fast" bash -lc 'cd webapp && npm run test:e2e:ci-fast'
    if [[ "${DEV_GATE_RUN_API_SMOKE:-0}" == "1" ]]; then
      run_step "playwright-api-smoke" bash -lc 'cd webapp && npm run test:api'
    else
      record_skipped "playwright-api-smoke" "local-default;see-LOCAL_PARITY_GAPS"
    fi
    run_step "e2e-fullstack-critical" bash -lc '.github/local/run-e2e-fullstack-ci-like.sh'
  else
    record_skipped "e2e-smoke-ci-fast" "docker-unavailable"
    record_skipped "playwright-api-smoke" "docker-unavailable"
    record_skipped "e2e-fullstack-critical" "docker-unavailable"
    FAIL_STEP="docker-unavailable-e2e"
    exit 1
  fi
else
  log "DEV_GATE_SKIP_E2E=1 — skipping Playwright lanes"
  record_skipped "e2e-smoke-ci-fast" "DEV_GATE_SKIP_E2E"
  record_skipped "playwright-api-smoke" "DEV_GATE_SKIP_E2E"
  record_skipped "e2e-fullstack-critical" "DEV_GATE_SKIP_E2E"
fi

# Tear down CI Postgres container if the backend step left it running.
docker rm -f "${RAG_CI_POSTGRES_CONTAINER:-rag-ci-pg}" >/dev/null 2>&1 || true
