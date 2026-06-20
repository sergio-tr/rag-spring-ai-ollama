#!/usr/bin/env bash
# Strict local closure lane for HTTP integration tests.
# Requires backend and classifier to be already running on the configured URLs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

if [[ -z "${INTEGRATION_BACKEND_URL:-}" ]]; then
  BACKEND_URL="$(
    python3 - <<'PY'
import ssl
import time
import urllib.error
import urllib.request

candidates = [
    "https://127.0.0.1:8444",
    "http://127.0.0.1:9000",
]
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def probe(base: str) -> int | None:
    url = base.rstrip("/") + "/actuator/health/liveness"
    try:
        req = urllib.request.Request(url, method="GET")
        open_kwargs = {"timeout": 4}
        if base.startswith("https://"):
            open_kwargs["context"] = ctx
        with urllib.request.urlopen(req, **open_kwargs) as resp:
            return resp.getcode()
    except urllib.error.HTTPError as exc:
        return exc.code
    except (OSError, urllib.error.URLError, TimeoutError):
        return None

for attempt in range(8):
    for base in candidates:
        status = probe(base)
        if status is not None and 200 <= status < 300:
            print(base)
            raise SystemExit(0)
        if base.startswith("https://127.0.0.1:8444") and status in (502, 503, 504):
            print(base)
            raise SystemExit(0)
    time.sleep(2)

print("https://127.0.0.1:8444")
PY
  )"
else
  BACKEND_URL="${INTEGRATION_BACKEND_URL}"
fi
CLASSIFIER_URL="${INTEGRATION_CLASSIFIER_URL:-http://127.0.0.1:8000}"
PYTEST_LOG_PATH="${PYTEST_LOG_PATH:-/tmp/pytest-integration-closure.log}"

export INTEGRATION_STRICT="${INTEGRATION_STRICT:-1}"
export INTEGRATION_FAIL_ON_UNREACHABLE="${INTEGRATION_FAIL_ON_UNREACHABLE:-${INTEGRATION_STRICT}}"
export INTEGRATION_REQUIRE_CLASSIFIER="${INTEGRATION_REQUIRE_CLASSIFIER:-1}"
export INTEGRATION_REQUIRE_CLASSIFIER_MODEL="${INTEGRATION_REQUIRE_CLASSIFIER_MODEL:-1}"
export INTEGRATION_CHECK_OBS="${INTEGRATION_CHECK_OBS:-0}"
export INTEGRATION_USE_TESTCONTAINERS="${INTEGRATION_USE_TESTCONTAINERS:-0}"
export INTEGRATION_BACKEND_URL="${BACKEND_URL}"
export INTEGRATION_CLASSIFIER_URL="${CLASSIFIER_URL}"
if [[ "${BACKEND_URL}" == https://* ]]; then
  export INTEGRATION_HTTPX_VERIFY="${INTEGRATION_HTTPX_VERIFY:-0}"
fi

echo "[integration-closure] backend: ${INTEGRATION_BACKEND_URL}"
echo "[integration-closure] classifier: ${INTEGRATION_CLASSIFIER_URL}"
echo "[integration-closure] observability mode: ${INTEGRATION_CHECK_OBS}"
echo "[integration-closure] pytest log: ${PYTEST_LOG_PATH}"

python - <<'PY'
import os
import ssl
import sys
import urllib.error
import urllib.request

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

checks = [
    ("backend", os.environ["INTEGRATION_BACKEND_URL"].rstrip("/") + "/actuator/health/liveness"),
    ("classifier", os.environ["INTEGRATION_CLASSIFIER_URL"].rstrip("/") + "/health"),
]

for name, url in checks:
    try:
        req = urllib.request.Request(url, method="GET")
        open_kwargs = {"timeout": 5}
        if url.startswith("https://"):
            open_kwargs["context"] = ctx
        with urllib.request.urlopen(req, **open_kwargs) as response:
            status = response.getcode()
    except (OSError, urllib.error.URLError) as exc:
        print(f"error: required {name} service is unreachable at {url}: {exc}", file=sys.stderr)
        sys.exit(10)
    if status < 200 or status >= 300:
        print(f"error: required {name} service returned HTTP {status} at {url}", file=sys.stderr)
        sys.exit(11)
PY

python -m pip install -q -r tests/integration/requirements.txt

set -o pipefail
python -m pytest tests/integration -v -rs --tb=short --ignore=tests/integration/test_tc_postgres_smoke.py \
  2>&1 | tee "${PYTEST_LOG_PATH}"
pytest_status=${PIPESTATUS[0]}
if [[ "${pytest_status}" != "0" ]]; then
  exit "${pytest_status}"
fi

python - <<'PY'
import os
import re
import sys
from pathlib import Path

log_path = Path(os.environ.get("PYTEST_LOG_PATH", "/tmp/pytest-integration-closure.log"))
text = log_path.read_text(encoding="utf-8", errors="replace")

collected_matches = re.findall(r"collected\s+(\d+)\s+items", text)
if not collected_matches:
    print(f"error: could not parse pytest collected count from {log_path}", file=sys.stderr)
    sys.exit(20)
collected = int(collected_matches[-1])
if collected <= 0:
    print("error: pytest collected zero integration tests", file=sys.stderr)
    sys.exit(21)

summary_lines = [line for line in text.splitlines() if re.search(r"=+ .* in [0-9.]+s =+", line)]
summary = summary_lines[-1] if summary_lines else ""
if not summary:
    print(f"error: could not parse pytest summary from {log_path}", file=sys.stderr)
    sys.exit(22)

skipped_match = re.search(r"(\d+)\s+skipped", summary)
skipped = int(skipped_match.group(1)) if skipped_match else 0
if skipped == collected:
    print(f"error: false-green integration run: skipped == collected == {collected}", file=sys.stderr)
    sys.exit(23)

truthy = {"1", "true", "yes", "on"}
classifier_model_required = os.environ.get("INTEGRATION_REQUIRE_CLASSIFIER_MODEL", "").lower() in truthy

if os.environ.get("INTEGRATION_REQUIRE_CLASSIFIER", "").lower() in truthy:
    classifier_skip_lines = [
        line for line in text.splitlines()
        if "skipped" in line.lower() and "classifier" in line.lower()
    ]
    if not classifier_model_required:
        classifier_skip_lines = [
            line
            for line in classifier_skip_lines
            if "model not loaded" not in line.lower() and "model != loaded" not in line.lower()
        ]
    classifier_skip_lines = [
        line
        for line in classifier_skip_lines
        if "testobservabilitystack" not in line.lower() and "observability tests" not in line.lower()
    ]
    if classifier_skip_lines:
        print("error: classifier is required but classifier-related tests were skipped:", file=sys.stderr)
        for line in classifier_skip_lines:
            print(line, file=sys.stderr)
        sys.exit(24)

print(
    f"[integration-closure] guard passed: collected={collected}, skipped={skipped}, "
    f"classifier_required={os.environ.get('INTEGRATION_REQUIRE_CLASSIFIER')}"
)
PY
