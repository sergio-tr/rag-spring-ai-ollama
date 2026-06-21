#!/usr/bin/env bash
# Stop demo Lab stack and clear in-flight Lab jobs. Safe runtime cleanup only (no git reset/clean).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"

DEMO_BASE="${RAG_DEMO_BASE_URL:-https://127.0.0.1:8444}"
LOGIN_EMAIL="${RAG_DEMO_LOGIN_EMAIL:-dev@local.test}"
LOGIN_PASSWORD="${RAG_DEMO_LOGIN_PASSWORD:-dev}"

cancel_active_lab_jobs() {
  if ! curl -skf "${DEMO_BASE}/actuator/health/liveness" >/dev/null 2>&1; then
    echo "[lab-demo-down] demo stack not reachable at ${DEMO_BASE}; skipping job cancel"
    return 0
  fi
  local token
  token="$(
    curl -sk -X POST "${DEMO_BASE}/api/v5/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" \
      | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || true
  )"
  if [[ -z "${token}" ]]; then
    echo "[lab-demo-down] could not login; skipping job cancel" >&2
    return 0
  fi
  local jobs_json
  jobs_json="$(curl -sk -H "Authorization: Bearer ${token}" "${DEMO_BASE}/api/v5/lab/jobs/active" 2>/dev/null || echo '[]')"
  python3 - <<PY "${jobs_json}" "${DEMO_BASE}" "${token}"
import json, sys, urllib.request, ssl, time
jobs = json.loads(sys.argv[1])
base, token = sys.argv[2], sys.argv[3]
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def cancel_job(jid: str) -> None:
    url = f"{base}/api/v5/lab/jobs/{jid}/cancel"
    req = urllib.request.Request(url, method="POST", headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
        print(f"[lab-demo-down] cancelled job {jid} (HTTP {resp.status})")

for job in jobs:
    jid = job.get("jobId")
    if jid:
        cancel_job(jid)

for _ in range(5):
    active_req = urllib.request.Request(
        f"{base}/api/v5/lab/jobs/active",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(active_req, context=ctx, timeout=30) as resp:
        remaining = json.loads(resp.read().decode())
    if not remaining:
        break
    for job in remaining:
        jid = job.get("jobId")
        if jid:
            cancel_job(jid)
    time.sleep(1)
PY
}

stop_ci_test_postgres() {
  local name="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-postgres}"
  if docker ps -a --format '{{.Names}}' | grep -qx "${name}"; then
    echo "[lab-demo-down] stopping CI/test Postgres container ${name}"
    docker stop "${name}" >/dev/null 2>&1 || true
  fi
}

cancel_active_lab_jobs
stop_ci_test_postgres
exec "${HERE}/down.sh" dev --rag --proxy --classifier "$@"
