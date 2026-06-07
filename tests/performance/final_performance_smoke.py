#!/usr/bin/env python3
"""
Final-scope performance smoke.

Measures a bounded set of product endpoints and writes an evidence JSON report.
It is a latency/usability smoke, not a production scalability benchmark.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import shlex
import socket
import sys
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from typing import Any

from common import now_iso, quantile


@dataclass
class StepResult:
    name: str
    method: str
    path: str
    status: str
    status_code: int | None
    duration_ms: float | None
    error: str | None = None
    skip_reason: str | None = None


def _env(name: str, default: str | None = None) -> str | None:
    value = os.environ.get(name)
    if value is None or not value.strip():
        return default
    return value.strip()


def _request_json(
    base_url: str,
    method: str,
    path: str,
    timeout_s: float,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> tuple[int | None, Any, float, str | None]:
    url = base_url.rstrip("/") + (path if path.startswith("/") else f"/{path}")
    headers = {"Accept": "application/json,*/*"}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            duration_ms = (time.perf_counter() - start) * 1000.0
            try:
                payload: Any = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                payload = raw
            return getattr(resp, "status", None), payload, duration_ms, None
    except urllib.error.HTTPError as e:
        duration_ms = (time.perf_counter() - start) * 1000.0
        return e.code, None, duration_ms, f"HTTPError: {e.code}"
    except Exception as e:  # noqa: BLE001 - evidence report must capture infrastructure failures.
        duration_ms = (time.perf_counter() - start) * 1000.0
        return None, None, duration_ms, f"{type(e).__name__}: {e}"


def _record(
    steps: list[StepResult],
    name: str,
    method: str,
    path: str,
    expected: set[int],
    base_url: str,
    timeout_s: float,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> Any:
    code, payload, ms, err = _request_json(base_url, method, path, timeout_s, token=token, body=body)
    ok = code in expected and err is None
    steps.append(
        StepResult(
            name=name,
            method=method,
            path=path,
            status="passed" if ok else "failed",
            status_code=code,
            duration_ms=ms,
            error=None if ok else (err or f"unexpected-status-{code}"),
        )
    )
    return payload


def _skip(steps: list[StepResult], name: str, method: str, path: str, reason: str) -> None:
    steps.append(
        StepResult(
            name=name,
            method=method,
            path=path,
            status="skipped",
            status_code=None,
            duration_ms=None,
            skip_reason=reason,
        )
    )


def _resolve_token(base_url: str, product_prefix: str, timeout_s: float) -> tuple[str | None, str | None]:
    token = _env("PERF_BEARER_TOKEN")
    if token:
        return token, None
    email = _env("PERF_EMAIL")
    password = _env("PERF_PASSWORD")
    if not email or not password:
        return None, "PERF_BEARER_TOKEN or PERF_EMAIL/PERF_PASSWORD not set"
    code, payload, _ms, err = _request_json(
        base_url,
        "POST",
        f"{product_prefix.rstrip('/')}/auth/login",
        timeout_s,
        body={"email": email, "password": password},
    )
    if code != 200 or err or not isinstance(payload, dict):
        return None, err or f"login failed with status {code}"
    resolved = payload.get("accessToken")
    if not isinstance(resolved, str) or not resolved:
        return None, "login response did not contain accessToken"
    return resolved, None


def _poll_job(
    steps: list[StepResult],
    base_url: str,
    token: str,
    path: str,
    timeout_s: float,
    poll_seconds: float,
) -> None:
    deadline = time.perf_counter() + timeout_s
    last_payload: Any = None
    last_code: int | None = None
    total_ms = 0.0
    while time.perf_counter() < deadline:
        code, payload, ms, err = _request_json(base_url, "GET", path, timeout_s=min(10.0, timeout_s), token=token)
        last_code = code
        last_payload = payload
        total_ms += ms
        if err or code != 200:
            steps.append(StepResult("lab_job_status", "GET", path, "failed", code, total_ms, err or "non-200"))
            return
        if isinstance(payload, dict) and payload.get("terminal") is True:
            status = str(payload.get("status") or "")
            passed = status in {"SUCCEEDED", "DONE"}
            steps.append(
                StepResult(
                    "lab_job_status",
                    "GET",
                    path,
                    "passed" if passed else "failed",
                    code,
                    total_ms,
                    None if passed else f"terminal status {status}",
                )
            )
            return
        time.sleep(poll_seconds)
    status = last_payload.get("status") if isinstance(last_payload, dict) else None
    steps.append(
        StepResult(
            "lab_job_status",
            "GET",
            path,
            "failed",
            last_code,
            total_ms,
            f"poll-timeout lastStatus={status}",
        )
    )


def _kpis(steps: list[StepResult]) -> dict[str, Any]:
    values = sorted(s.duration_ms for s in steps if s.status == "passed" and s.duration_ms is not None)
    if not values:
        return {"p50": None, "p95": None, "p99": None, "avg": None}
    return {
        "p50": quantile(values, 0.50),
        "p95": quantile(values, 0.95),
        "p99": quantile(values, 0.99),
        "avg": sum(values) / len(values),
    }


def _threshold_failures(steps: list[StepResult], max_error_rate: float, max_p95_ms: float) -> list[str]:
    measured = [s for s in steps if s.status != "skipped"]
    failed = [s for s in measured if s.status == "failed"]
    error_rate = len(failed) / len(measured) if measured else 0.0
    failures: list[str] = []
    if error_rate > max_error_rate:
        failures.append(f"errorRate {error_rate:.4f} > maxErrorRate {max_error_rate:.4f}")
    p95 = _kpis(steps)["p95"]
    if isinstance(p95, (int, float)) and not math.isnan(float(p95)) and float(p95) > max_p95_ms:
        failures.append(f"p95 {float(p95):.2f}ms > maxP95Ms {max_p95_ms:.2f}ms")
    return failures


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-base-url", default=_env("PERF_BACKEND_BASE_URL", "http://127.0.0.1:9000"))
    parser.add_argument("--product-prefix", default=_env("PERF_PRODUCT_PREFIX", "/api/v5"))
    parser.add_argument("--timeout-s", type=float, default=float(_env("PERF_TIMEOUT_SECONDS", "30") or "30"))
    parser.add_argument("--poll-timeout-s", type=float, default=float(_env("PERF_POLL_TIMEOUT_SECONDS", "60") or "60"))
    parser.add_argument("--poll-interval-s", type=float, default=float(_env("PERF_POLL_INTERVAL_SECONDS", "2") or "2"))
    parser.add_argument("--max-error-rate", type=float, default=float(_env("PERF_MAX_ERROR_RATE", "0") or "0"))
    parser.add_argument("--max-p95-ms", type=float, default=float(_env("PERF_MAX_P95_MS", "3000") or "3000"))
    parser.add_argument("--require-product", action="store_true")
    parser.add_argument("--enable-lab-start", action="store_true")
    parser.add_argument("--output-json", required=True)
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    started = time.perf_counter()
    product = args.product_prefix.rstrip("/")
    steps: list[StepResult] = []

    _record(
        steps,
        "health_liveness",
        "GET",
        "/actuator/health/liveness",
        {200},
        args.backend_base_url,
        args.timeout_s,
    )
    _record(
        steps,
        "actuator_metrics",
        "GET",
        "/actuator/prometheus",
        {200},
        args.backend_base_url,
        args.timeout_s,
    )

    token, token_error = _resolve_token(args.backend_base_url, product, args.timeout_s)
    project_id = _env("PERF_PROJECT_ID")
    conversation_id = _env("PERF_CONVERSATION_ID")
    dataset_id = _env("PERF_DATASET_ID")
    lab_kind = _env("PERF_LAB_KIND", "LLM_JUDGE_QA") or "LLM_JUDGE_QA"

    if token_error:
        _skip(steps, "lab_status", "GET", f"{product}/lab/status", token_error)
    else:
        _record(steps, "lab_status", "GET", f"{product}/lab/status", {200}, args.backend_base_url, args.timeout_s, token=token)

    product_missing = bool(token_error or not project_id)
    if product_missing:
        reason = token_error if token_error else "PERF_PROJECT_ID not set"
        _skip(steps, "document_status", "GET", f"{product}/projects/{{projectId}}/documents", reason)
    else:
        _record(
            steps,
            "document_status",
            "GET",
            f"{product}/projects/{project_id}/documents",
            {200},
            args.backend_base_url,
            args.timeout_s,
            token=token,
        )

    if token_error or not conversation_id:
        reason = token_error if token_error else "PERF_CONVERSATION_ID not set"
        _skip(steps, "chat_request", "POST", f"{product}/conversations/{{conversationId}}/messages", reason)
    else:
        payload = _record(
            steps,
            "chat_request",
            "POST",
            f"{product}/conversations/{conversation_id}/messages",
            {200, 202},
            args.backend_base_url,
            args.timeout_s,
            token=token,
            body={"content": _env("PERF_CHAT_PROMPT", "Performance smoke: respond with a short status.")},
        )
        if isinstance(payload, dict):
            poll_path = payload.get("pollPath")
            job_id = payload.get("jobId")
            if isinstance(poll_path, str) and poll_path:
                _poll_job(steps, args.backend_base_url, token or "", poll_path, args.poll_timeout_s, args.poll_interval_s)
            elif isinstance(job_id, str) and job_id:
                _poll_job(steps, args.backend_base_url, token or "", f"{product}/lab/jobs/{job_id}", args.poll_timeout_s, args.poll_interval_s)

    if not args.enable_lab_start:
        _skip(
            steps,
            "lab_run_start",
            "POST",
            f"{product}/lab/benchmarks/{{kind}}/runs",
            "disabled by default; pass --enable-lab-start with PERF_DATASET_ID to start a real Lab run",
        )
    elif token_error or not dataset_id:
        reason = token_error if token_error else "PERF_DATASET_ID not set"
        _skip(steps, "lab_run_start", "POST", f"{product}/lab/benchmarks/{lab_kind}/runs", reason)
    else:
        body: dict[str, Any] = {"datasetId": dataset_id, "runKind": "PRODUCT_EXPLORATION", "name": "performance-smoke"}
        if project_id:
            body["projectId"] = project_id
        payload = _record(
            steps,
            "lab_run_start",
            "POST",
            f"{product}/lab/benchmarks/{lab_kind}/runs",
            {202},
            args.backend_base_url,
            args.timeout_s,
            token=token,
            body=body,
        )
        if isinstance(payload, dict):
            poll_path = payload.get("pollPath")
            job_id = payload.get("jobId")
            if isinstance(poll_path, str) and poll_path:
                _poll_job(steps, args.backend_base_url, token or "", poll_path, args.poll_timeout_s, args.poll_interval_s)
            elif isinstance(job_id, str) and job_id:
                _poll_job(steps, args.backend_base_url, token or "", f"{product}/lab/jobs/{job_id}", args.poll_timeout_s, args.poll_interval_s)

    failures = _threshold_failures(steps, args.max_error_rate, args.max_p95_ms)
    skipped = [s for s in steps if s.status == "skipped"]
    product_step_names = {"document_status", "chat_request", "lab_run_start"}
    product_steps = [s for s in steps if s.name in product_step_names]
    product_skipped = [s for s in product_steps if s.status == "skipped"]
    product_failed = [s for s in product_steps if s.status == "failed"]
    product_flow_complete = bool(product_steps) and not product_skipped and not product_failed
    if args.require_product and skipped:
        failures.append("requireProduct=true but product-scoped steps were skipped")

    report = {
        "schemaVersion": "1.0",
        "kind": "final_performance_smoke",
        "generatedAt": now_iso(),
        "host": socket.gethostname(),
        "backendBaseUrl": args.backend_base_url,
        "productPrefix": product,
        "command": " ".join(shlex.quote(part) for part in sys.argv),
        "durationSeconds": time.perf_counter() - started,
        "scope": "bounded latency/usability smoke; not production scalability evidence",
        "evidenceMode": "PRODUCT_FLOW" if product_flow_complete else "INFRA_ONLY",
        "productFlowComplete": product_flow_complete,
        "thresholds": {
            "maxErrorRate": args.max_error_rate,
            "maxP95Ms": args.max_p95_ms,
            "passed": not failures,
            "failures": failures,
        },
        "summary": {
            "passed": sum(1 for s in steps if s.status == "passed"),
            "failed": sum(1 for s in steps if s.status == "failed"),
            "skipped": len(skipped),
            "kpisMs": _kpis(steps),
        },
        "steps": [asdict(s) for s in steps],
        "limitations": [
            "Smoke uses a small endpoint sample and does not support production scalability claims.",
            "Chat and Lab measurements require PERF_* credentials/IDs and may be skipped when unavailable.",
        ],
    }

    os.makedirs(os.path.dirname(args.output_json) or ".", exist_ok=True)
    with open(args.output_json, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(json.dumps({k: report[k] for k in ("kind", "durationSeconds", "summary", "thresholds")}, indent=2))
    print(f"OK: results written to {args.output_json}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
