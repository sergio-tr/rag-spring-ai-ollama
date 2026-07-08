#!/usr/bin/env python3
"""
Infra micro-probe: simple GET (default /actuator/health) latency percentiles.

Not a RAG benchmark - use for cold/warm JVM or reverse-proxy baselines.
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import time
import urllib.request
from concurrent.futures import Future, ThreadPoolExecutor, as_completed
from dataclasses import dataclass

import math

from common import now_iso, quantile


@dataclass(frozen=True)
class ProbeResult:
    ok: bool
    status_code: int | None
    duration_ms: float
    error: str | None = None


def _run_get(backend_base_url: str, path: str, timeout_s: float) -> ProbeResult:
    base = backend_base_url.rstrip("/")
    p = path if path.startswith("/") else f"/{path}"
    url = f"{base}{p}"
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, method="GET", headers={"Accept": "application/json,*/*"})
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            status = getattr(resp, "status", None)  # type: ignore[attr-defined]
            _ = resp.read()
            duration_ms = (time.perf_counter() - start) * 1000.0
            ok = status == 200
            return ProbeResult(ok=ok, status_code=status, duration_ms=duration_ms, error=None if ok else "non-200")
    except Exception as e:
        duration_ms = (time.perf_counter() - start) * 1000.0
        return ProbeResult(ok=False, status_code=None, duration_ms=duration_ms, error=f"{type(e).__name__}: {e}")


def _parse_args() -> argparse.Namespace:
    default_path = os.environ.get("ACTUATOR_HEALTH_PATH", "/actuator/health")
    p = argparse.ArgumentParser()
    p.add_argument("--backend-base-url", default="http://localhost:9000")
    p.add_argument("--path", default=default_path, help="GET path (default ACTUATOR_HEALTH_PATH or /actuator/health).")
    p.add_argument("--repetitions", type=int, default=20)
    p.add_argument("--warmup", type=int, default=2)
    p.add_argument("--concurrency", type=int, default=4)
    p.add_argument("--timeout-s", type=float, default=30.0)
    p.add_argument("--output-json", default=None)
    p.add_argument(
        "--max-error-rate",
        type=float,
        default=float(os.environ.get("PERF_INFRA_MAX_ERROR_RATE", "0")),
        help="Fail when measured error rate is above this fraction (default: 0).",
    )
    p.add_argument(
        "--max-p95-ms",
        type=float,
        default=float(os.environ.get("PERF_INFRA_MAX_P95_MS", "2000")),
        help="Fail when measured p95 latency is above this threshold in ms (default: 2000).",
    )
    return p.parse_args()


def _json_float(x: float) -> float | None:
    return None if math.isnan(x) else x


def _threshold_failures(
    measured_error_rate: float,
    max_error_rate: float,
    measured_p95: float,
    max_p95_ms: float,
) -> list[str]:
    failures: list[str] = []
    if measured_error_rate > max_error_rate:
        failures.append(f"errorRate {measured_error_rate:.4f} > maxErrorRate {max_error_rate:.4f}")
    if not math.isnan(measured_p95) and measured_p95 > max_p95_ms:
        failures.append(f"p95 {measured_p95:.2f}ms > maxP95Ms {max_p95_ms:.2f}ms")
    return failures


def _run_probe(args: argparse.Namespace) -> tuple[list[ProbeResult], list[float]]:
    all_tasks = [True for _ in range(args.warmup)] + [False for _ in range(args.repetitions)]
    ok_durations: list[float] = []
    results: list[ProbeResult] = []

    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as pool:
        future_warmup: dict[Future, bool] = {}
        for is_warmup in all_tasks:
            fut = pool.submit(_run_get, args.backend_base_url, args.path, args.timeout_s)
            future_warmup[fut] = is_warmup

        for fut in as_completed(future_warmup):
            is_warmup = future_warmup[fut]
            res = fut.result()
            results.append(res)
            if (not is_warmup) and res.ok:
                ok_durations.append(res.duration_ms)
    return results, ok_durations


def main() -> int:
    args = _parse_args()
    results, ok_durations = _run_probe(args)
    total = args.warmup + args.repetitions
    ok_count = sum(1 for r in results if r.ok)
    sorted_ok = sorted(ok_durations)
    measured_total = max(0, args.repetitions)
    measured_errors = measured_total - len(ok_durations)
    measured_error_rate = measured_errors / measured_total if measured_total else 0.0
    measured_p95 = quantile(sorted_ok, 0.95)
    threshold_failures = _threshold_failures(
        measured_error_rate,
        args.max_error_rate,
        measured_p95,
        args.max_p95_ms,
    )

    report = {
        "schemaVersion": "1.0",
        "kind": "infra_probe",
        "generatedAt": now_iso(),
        "host": socket.gethostname(),
        "backendBaseUrl": args.backend_base_url,
        "path": args.path,
        "repetitions": args.repetitions,
        "warmup": args.warmup,
        "concurrency": args.concurrency,
        "timeoutSeconds": args.timeout_s,
        "totalRequests": total,
        "okRequests": ok_count,
        "measuredRequests": measured_total,
        "measuredOkRequests": len(ok_durations),
        "measuredErrorRate": measured_error_rate,
        "thresholds": {
            "maxErrorRate": args.max_error_rate,
            "maxP95Ms": args.max_p95_ms,
            "passed": not threshold_failures,
            "failures": threshold_failures,
        },
        "kpisMs": {
            "p50": _json_float(quantile(sorted_ok, 0.50)),
            "p95": _json_float(measured_p95),
            "p99": _json_float(quantile(sorted_ok, 0.99)),
            "avg": _json_float(sum(sorted_ok) / len(sorted_ok)) if sorted_ok else None,
        },
    }

    if args.output_json:
        report["requests"] = [r.__dict__ for r in results]
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"OK: results written to {args.output_json}")
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))

    if threshold_failures:
        for failure in threshold_failures:
            print(f"THRESHOLD_FAILED: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
