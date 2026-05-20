#!/usr/bin/env bash
# Full local CI parity: core, integration, e2e, performance, Sonar, Docker guards.
# Removes rag-ci* / sonar-ci-pg containers on exit (RAG_CI_STOP_CONTAINER=1 on ci-like scripts).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
LOG_ROOT="${REPO_ROOT}/.cursor/context/evidence/tests/full-ci-${RUN_ID}"
SUMMARY="${LOG_ROOT}/summary.md"
RESULTS_TSV="${LOG_ROOT}/results.tsv"
START_EPOCH="$(date +%s)"
FAILED_STEPS=()

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="${JAVA_HOME}/bin:${PATH}"
export RAG_CI_STOP_CONTAINER=1

mkdir -p "${LOG_ROOT}"
printf 'step\tstatus\tduration_seconds\tlog\n' > "${RESULTS_TSV}"

log() { printf '[full-ci] %s\n' "$*"; }

cleanup_containers() {
  log "Cleaning up CI containers..."
  local names
  names="$(docker ps -a --format '{{.Names}}' 2>/dev/null | grep -E '^(rag-ci|sonar-ci)' || true)"
  if [[ -n "${names}" ]]; then
    echo "${names}" | xargs -r docker rm -f >/dev/null 2>&1 || true
  fi
  docker network rm rag-ci >/dev/null 2>&1 || true
}

trap cleanup_containers EXIT

append_result() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "${RESULTS_TSV}"
}

run_step() {
  local name="$1"
  shift
  local log_path="${LOG_ROOT}/${name}.log"
  local start end duration status
  start="$(date +%s)"
  log "START ${name}"
  set +e
  (
    cd "${REPO_ROOT}"
    "$@"
  ) >"${log_path}" 2>&1
  status=$?
  set -e
  end="$(date +%s)"
  duration=$((end - start))
  if [[ "${status}" -ne 0 ]]; then
    FAILED_STEPS+=("${name}")
    append_result "${name}" "FAILED(${status})" "${duration}" "${log_path}"
    log "FAIL ${name} (${duration}s) — see ${log_path}"
  else
    append_result "${name}" "PASSED" "${duration}" "${log_path}"
    log "PASS ${name} (${duration}s)"
  fi
  return "${status}"
}

write_summary() {
  local end_epoch total
  end_epoch="$(date +%s)"
  total=$((end_epoch - START_EPOCH))
  local overall="PASSED"
  [[ ${#FAILED_STEPS[@]} -gt 0 ]] && overall="FAILED"
  {
    echo "# Full CI Local Run"
    echo
    echo "- Run ID: \`${RUN_ID}\`"
    echo "- Status: \`${overall}\`"
    echo "- Duration (s): \`${total}\`"
    echo "- Log directory: \`${LOG_ROOT}\`"
    if [[ ${#FAILED_STEPS[@]} -gt 0 ]]; then
      echo "- Failed steps: ${FAILED_STEPS[*]}"
    fi
    echo
    echo "| Step | Status | Duration (s) | Log |"
    echo "| --- | --- | ---: | --- |"
    tail -n +2 "${RESULTS_TSV}" | while IFS=$'\t' read -r step st dur log_path; do
      echo "| \`${step}\` | \`${st}\` | ${dur} | \`${log_path#${REPO_ROOT}/}\` |"
    done
  } > "${SUMMARY}"
}

cleanup_containers

run_step verify-pinned-postgres bash -lc 'bash ./.github/scripts/verify-pinned-postgres-image.sh' || true
run_step backend-ci-core bash -lc '.github/local/run-ci-core.sh --stop-after' || true
run_step classifier-pytest bash -lc 'cd classifier-service && python3.11 -m pip install -q -r requirements.txt && python3.11 -m pytest tests/ -v' || true

run_step webapp-install bash -lc 'cd webapp && npm install --no-audit --no-fund' || true
run_step webapp-lint bash -lc 'cd webapp && npm run lint' || true
run_step webapp-typecheck bash -lc 'cd webapp && npm run typecheck' || true
run_step webapp-guard-unit bash -lc 'cd webapp && npm run test:e2e:guard-unit' || true
run_step webapp-coverage bash -lc 'cd webapp && npm run test:coverage' || true
run_step webapp-build bash -lc 'cd webapp && npm run build' || true
run_step webapp-doc bash -lc 'cd webapp && npm run doc' || true

run_step compose-guard bash -lc '
  python3 -m pip install -q pyyaml
  bash ./docker/scripts/create-env-all.sh --force
  python3 docker/scripts/compose_guard.py --only-rules image_forbidden,yaml_error,build_invalid,build_missing_context,build_missing_dockerfile
  (cd docker && docker compose -f docker-compose.yml --profile logs --env-file ../observability/.env config -q)
  (cd docker && docker compose -f docker-compose.yml -f compose.obs.yml --profile observability --profile logs --profile infra --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env --env-file ../webapp/.env --env-file ../observability/.env config -q)
' || true

run_step integration-pytest bash -lc '
  INTEGRATION_STRICT=0 INTEGRATION_REQUIRE_CLASSIFIER=0 .github/local/run-integration-ci-like.sh --stop-after
' || true

run_step integration-classifier-required bash -lc '
  INTEGRATION_STRICT=1 INTEGRATION_FAIL_ON_UNREACHABLE=1 INTEGRATION_REQUIRE_CLASSIFIER=1 .github/local/run-integration-ci-like.sh --stop-after
' || true

run_step playwright-api bash -lc '
  export RAG_CI_STOP_CONTAINER=1
  .github/local/run-ci-core.sh --prepare-only
  cd rag-service && nohup ./mvnw -B -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e > /tmp/rag-api-smoke.log 2>&1 &
  echo $! > /tmp/rag-api-smoke.pid
  for i in $(seq 1 90); do curl -fsS http://127.0.0.1:9000/actuator/health >/dev/null 2>&1 && break; sleep 2; done
  curl -fsS http://127.0.0.1:9000/actuator/health
  cd webapp && npx playwright install --with-deps chromium && npm run test:api
  kill $(cat /tmp/rag-api-smoke.pid) 2>/dev/null || true
  docker rm -f rag-ci-postgres 2>/dev/null || true
' || true

run_step e2e-ci-fast bash -lc 'cd webapp && npx playwright install --with-deps chromium && npm run test:e2e:ci-fast' || true
run_step e2e-fullstack bash -lc 'RAG_CI_STOP_CONTAINER=1 .github/local/run-e2e-fullstack-ci-like.sh --stop-after' || true
run_step performance bash -lc 'RAG_CI_STOP_CONTAINER=1 .github/local/run-performance-ci-like.sh --stop-after' || true

if [[ -n "${SONAR_TOKEN:-}" ]]; then
  run_step sonar bash -lc 'RAG_CI_STOP_CONTAINER=1 .github/local/ci-like-sonar.sh' || true
else
  append_result sonar "SKIPPED(no SONAR_TOKEN)" 0 ""
  log "SKIP sonar (SONAR_TOKEN unset)"
fi

run_step docker-build-rag bash -lc 'docker build -q -f rag-service/Dockerfile rag-service' || true
run_step docker-build-classifier bash -lc 'docker build -q -f classifier-service/Dockerfile classifier-service' || true
run_step docker-build-webapp bash -lc 'docker build -q -f webapp/Dockerfile webapp --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:9000 --build-arg NEXT_PUBLIC_RAG_API_PREFIX=/api/v5 --build-arg NEXT_PUBLIC_TIMEZONE=UTC' || true

write_summary
log "Finished. Summary: ${SUMMARY}"
if [[ ${#FAILED_STEPS[@]} -gt 0 ]]; then
  exit 1
fi
exit 0
