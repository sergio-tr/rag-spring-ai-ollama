#!/usr/bin/env bash
# Fast local pre-push gate: cheap checks first, then a small Playwright smoke lane.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOG_ROOT="${REPO_ROOT}/.cursor/context/evidence/tests/pre-push-fast-${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
SUMMARY="${LOG_ROOT}/summary.md"
RESULTS_TSV="${LOG_ROOT}/results.tsv"
START_EPOCH="$(date +%s)"
FAIL_STEP=""

mkdir -p "${LOG_ROOT}"
printf 'step\tstatus\tduration_seconds\tlog\tskips\n' > "${RESULTS_TSV}"

log() {
  printf '[pre-push-fast] %s\n' "$*"
}

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
  local total
  total=$(($(date +%s) - START_EPOCH))
  {
    echo "# Pre-push Fast Summary"
    echo
    echo "- Status: \`${status}\`"
    echo "- Total duration seconds: \`${total}\`"
    echo "- Evidence directory: \`${LOG_ROOT}\`"
    if [[ -n "${FAIL_STEP}" ]]; then
      echo "- Failed step: \`${FAIL_STEP}\`"
    fi
    echo
    echo "| Step | Status | Duration (s) | Skips | Log |"
    echo "| --- | --- | ---: | ---: | --- |"
    tail -n +2 "${RESULTS_TSV}" | while IFS=$'\t' read -r step st dur log_path skips; do
      echo "| \`${step}\` | \`${st}\` | ${dur} | ${skips} | \`${log_path#${REPO_ROOT}/}\` |"
    done
  } > "${SUMMARY}"
}

finish() {
  local code=$?
  if [[ "${code}" -eq 0 ]]; then
    write_summary "PASSED"
    log "PASSED. Summary: ${SUMMARY}"
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

run_step "webapp-lint" bash -lc 'cd webapp && npm run lint'
run_step "webapp-typecheck" bash -lc 'cd webapp && npm run typecheck'
run_step "webapp-unit" bash -lc 'cd webapp && npm run test'
run_step "backend-unit" bash -lc 'cd rag-service && ./mvnw -B test'
run_step "classifier-pytest" bash -lc 'cd classifier-service && pytest tests/ -v'
run_step "e2e-fast-fail" bash -lc 'cd webapp && npm run test:e2e:ci-fast'
