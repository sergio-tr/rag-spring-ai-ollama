#!/usr/bin/env bash
# Final local closure lane: fail-fast CI reproduction with evidence logs.
#
# This script intentionally does not hide failures. Each step writes a log under
# .cursor/context/evidence/tests and the first failing step stops the run.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
EVIDENCE_DIR="${REPO_ROOT}/.cursor/context/evidence/tests"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
RUN_DIR="${EVIDENCE_DIR}/local-ci-${RUN_ID}"
SUMMARY="${RUN_DIR}/summary.md"
RESULTS_TSV="${RUN_DIR}/results.tsv"
FAIL_STEP=""
START_EPOCH="$(date +%s)"

RUN_INTEGRATION="${RUN_INTEGRATION:-1}"
RUN_E2E_FULLSTACK="${RUN_E2E_FULLSTACK:-1}"
RUN_PERFORMANCE="${RUN_PERFORMANCE:-1}"

mkdir -p "${RUN_DIR}"
printf 'step\tstatus\tduration_seconds\tlog\tskips\n' > "${RESULTS_TSV}"

log() {
  printf '[closure-ci] %s\n' "$*"
}

usage() {
  cat <<EOF
Usage: $0 [--no-integration] [--no-fullstack] [--no-performance]

Runs the local final closure CI lane and writes logs to:
  ${RUN_DIR}

Environment toggles:
  RUN_INTEGRATION=0      Skip strict integration.
  RUN_E2E_FULLSTACK=0    Skip fullstack Playwright.
  RUN_PERFORMANCE=0      Skip performance lane.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-integration) RUN_INTEGRATION=0; shift ;;
    --no-fullstack) RUN_E2E_FULLSTACK=0; shift ;;
    --no-performance) RUN_PERFORMANCE=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

append_result() {
  local step="$1"
  local status="$2"
  local duration="$3"
  local log_path="$4"
  local skips="$5"
  printf '%s\t%s\t%s\t%s\t%s\n' "${step}" "${status}" "${duration}" "${log_path}" "${skips}" >> "${RESULTS_TSV}"
}

count_skips() {
  local log_path="$1"
  if [[ ! -f "${log_path}" ]]; then
    echo "0"
    return
  fi
  local n
  n="$(
    {
      grep -Eo '(^|[[:space:]])[0-9]+[[:space:]]+skipped' "${log_path}" \
        | grep -Eo '[0-9]+' || true
      grep -Eo 'Skipped:[[:space:]]+[0-9]+' "${log_path}" \
        | grep -Eo '[0-9]+' || true
    } \
      | grep -Eo '[0-9]+' \
      | awk '{s+=$1} END {print s+0}' || true
  )"
  echo "${n:-0}"
}

write_summary() {
  local status="$1"
  local end_epoch
  end_epoch="$(date +%s)"
  local total=$((end_epoch - START_EPOCH))
  {
    echo "# Local Closure CI Summary"
    echo
    echo "- Run ID: \`${RUN_ID}\`"
    echo "- Status: \`${status}\`"
    echo "- Total duration seconds: \`${total}\`"
    echo "- Evidence directory: \`${RUN_DIR}\`"
    if [[ -n "${FAIL_STEP}" ]]; then
      echo "- Failed step: \`${FAIL_STEP}\`"
    fi
    echo
    echo "## Results"
    echo
    echo "| Step | Status | Duration (s) | Skips | Log |"
    echo "| --- | --- | ---: | ---: | --- |"
    tail -n +2 "${RESULTS_TSV}" | while IFS=$'\t' read -r step st dur log_path skips; do
      local rel="${log_path#${REPO_ROOT}/}"
      echo "| \`${step}\` | \`${st}\` | ${dur} | ${skips} | \`${rel}\` |"
    done
    echo
    echo "## Notes"
    echo
    echo "- The lane is fail-fast; later steps are not run after a failure."
    echo "- Optional skips are controlled only by explicit script flags or environment variables."
    echo "- Integration/fullstack/performance require Docker and free local ports used by the existing CI-like scripts."
  } > "${SUMMARY}"
}

finish() {
  local code=$?
  if [[ "${code}" -eq 0 ]]; then
    write_summary "PASSED"
  else
    write_summary "FAILED"
    log "FAILED at step: ${FAIL_STEP:-unknown}. Summary: ${SUMMARY}"
  fi
  exit "${code}"
}
trap finish EXIT

run_step() {
  local name="$1"
  shift
  local log_path="${RUN_DIR}/${name}.log"
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
  local end
  end="$(date +%s)"
  local duration=$((end - start))
  local skips
  skips="$(count_skips "${log_path}")"
  if [[ "${status}" -ne 0 ]]; then
    FAIL_STEP="${name}"
    append_result "${name}" "FAILED(${status})" "${duration}" "${log_path}" "${skips}"
    return "${status}"
  fi
  append_result "${name}" "PASSED" "${duration}" "${log_path}" "${skips}"
  log "PASS ${name} (${duration}s)"
}

run_step "backend-clean-verify" bash -lc '.github/local/run-ci-core.sh'
run_step "classifier-pytest" bash -lc 'cd classifier-service && pytest tests/ -v'
run_step "webapp-lint" bash -lc 'cd webapp && npm run lint'
run_step "webapp-typecheck" bash -lc 'cd webapp && npm run typecheck'
run_step "webapp-test-coverage" bash -lc 'cd webapp && npm run test:coverage'
run_step "webapp-build" bash -lc 'cd webapp && npm run build'

run_step "docker-compose-config-guard" bash -lc '
  set -euo pipefail
  python3 -m pip install --user -q pyyaml
  bash ./docker/scripts/create-env-all.sh --force
  python3 docker/scripts/compose_guard.py --only-rules image_forbidden,yaml_error,build_invalid,build_missing_context,build_missing_dockerfile
  (
    cd docker
    docker compose -f docker-compose.yml --profile logs --env-file ../observability/.env config -q
    docker compose -f docker-compose.yml -f compose.obs.yml \
      --profile observability --profile logs --profile infra \
      --env-file ../db/.env \
      --env-file ../classifier-service/.env \
      --env-file ../rag-service/.env \
      --env-file ../webapp/.env \
      --env-file ../observability/.env \
      config -q
    docker compose -f docker-compose.yml -f compose.prod.yml \
      --profile observability \
      --env-file ../db/.env \
      --env-file ../classifier-service/.env \
      --env-file ../rag-service/.env \
      --env-file ../webapp/.env \
      --env-file ../observability/.env \
      config -q
  )
'

if [[ "${RUN_INTEGRATION}" = "1" ]]; then
  run_step "integration-strict" bash -lc 'INTEGRATION_STRICT=1 INTEGRATION_FAIL_ON_UNREACHABLE=1 INTEGRATION_REQUIRE_CLASSIFIER=1 .github/local/run-integration-ci-like.sh'
else
  append_result "integration-strict" "SKIPPED_BY_FLAG" "0" "" "0"
fi

run_step "e2e-smoke" bash -lc 'cd webapp && npm run test:e2e'

if [[ "${RUN_E2E_FULLSTACK}" = "1" ]]; then
  run_step "e2e-fullstack" bash -lc '.github/local/run-e2e-fullstack-ci-like.sh'
else
  append_result "e2e-fullstack" "SKIPPED_BY_FLAG" "0" "" "0"
fi

if [[ "${RUN_PERFORMANCE}" = "1" ]]; then
  run_step "performance-ci-like" bash -lc '.github/local/run-performance-ci-like.sh'
else
  append_result "performance-ci-like" "SKIPPED_BY_FLAG" "0" "" "0"
fi

log "All closure CI steps passed. Summary: ${SUMMARY}"
