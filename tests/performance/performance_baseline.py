#!/usr/bin/env python3
"""
Baseline de performance para el endpoint backend:
  GET /api/v4/query?question=...

Mide latencias y reporta p95/p99 (y otros KPIs) sobre un dataset controlado.

Uso ejemplo:
  python tests/performance/performance_baseline.py \
    --backend-base-url http://localhost:9000 \
    --repetitions 5 \
    --concurrency 1 \
    --questions '["¿Cuántas actas hay?","¿Qué actas tratan la luz?"]'
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


DEFAULT_QUESTIONS = [
    "¿Cuántas actas hay en el corpus?",
    "¿En qué secciones se menciona la palabra 'acta'?",
    "¿Qué actas tratan el tema de la 'luz'?",
    "¿Cómo se explica el proceso en las actas más recientes?",
    "¿Cuáles son las entidades principales mencionadas en las actas?",
    "¿Hay actas que contengan la palabra 'universidad'?",
]


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _quantile(sorted_values: list[float], q: float) -> float:
    # q in [0,1]. Simple implementation based on indices.
    if not sorted_values:
        return float("nan")
    if len(sorted_values) == 1:
        return sorted_values[0]
    idx = int(round(q * (len(sorted_values) - 1)))
    idx = max(0, min(idx, len(sorted_values) - 1))
    return sorted_values[idx]


@dataclass(frozen=True)
class RequestResult:
    question: str
    ok: bool
    status_code: int | None
    duration_ms: float
    error: str | None = None


def _http_get_json(url: str, timeout_s: float) -> tuple[int | None, Any, float, str | None]:
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, method="GET", headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            status = getattr(resp, "status", None)  # type: ignore[attr-defined]
            body = resp.read().decode("utf-8", errors="replace")
            try:
                return status, json.loads(body), (time.perf_counter() - start) * 1000.0, None
            except json.JSONDecodeError:
                # There may be non-JSON responses if the backend cannot complete the RAG.
                return status, body, (time.perf_counter() - start) * 1000.0, "non-json-response"
    except Exception as e:
        duration_ms = (time.perf_counter() - start) * 1000.0
        return None, None, duration_ms, f"{type(e).__name__}: {e}"


def _run_one(backend_base_url: str, question: str, timeout_s: float) -> RequestResult:
    encoded = urllib.parse.quote_plus(question)
    url = f"{backend_base_url}/api/v4/query?question={encoded}"
    status_code, _payload, duration_ms, error = _http_get_json(url, timeout_s=timeout_s)
    ok = status_code == 200
    return RequestResult(
        question=question,
        ok=ok,
        status_code=status_code,
        duration_ms=duration_ms,
        error=None if ok else error,
    )


def _load_questions_from_file(path: str) -> list[str]:
    lines: list[str] = []
    with open(path, "r", encoding="utf-8") as f:
        for raw in f:
            s = raw.strip()
            if not s or s.startswith("#"):
                continue
            lines.append(s)
    return lines


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--backend-base-url", default="http://localhost:9000", help="Ej: http://localhost:9000")
    p.add_argument("--endpoint-path", default="/api/v4/query", help="Solo informativo (se usa el /api/v4/query fijo).")
    p.add_argument("--questions-file", default=None, help="Ruta a un txt con 1 pregunta por linea.")
    p.add_argument("--questions", default=None, help="JSON array de preguntas. Ej: '[\"q1\",\"q2\"]'")
    p.add_argument("--repetitions", type=int, default=5, help="Repeticiones por cada pregunta.")
    p.add_argument("--warmup", type=int, default=1, help="Repeticiones warmup por cada pregunta (no incluidas en KPIs).")
    p.add_argument("--concurrency", type=int, default=1, help="Concurrencia para enviar requests.")
    p.add_argument("--timeout-s", type=float, default=180.0, help="Timeout por request (segundos).")
    p.add_argument("--output-json", default=None, help="Ruta de salida JSON. Si no se indica, se genera en stdout.")
    args = p.parse_args()

    if args.questions_file and args.questions:
        print("Usa solo uno: --questions-file o --questions", file=sys.stderr)
        return 2

    questions: list[str]
    if args.questions_file:
        questions = _load_questions_from_file(args.questions_file)
    elif args.questions:
        questions = json.loads(args.questions)
    else:
        questions = DEFAULT_QUESTIONS

    if not questions:
        print("Dataset de preguntas vacio.", file=sys.stderr)
        return 2

    # Warmup + measurement
    all_requests: list[str] = []
    for q in questions:
        all_requests.extend([q] * (args.warmup + args.repetitions))

    total = len(all_requests)
    warmup_count = len(questions) * args.warmup
    ok_durations: list[float] = []
    all_results: list[RequestResult] = []

    start_all = time.perf_counter()
    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as pool:
        futures = []
        for q in all_requests:
            futures.append(pool.submit(_run_one, args.backend_base_url, q, args.timeout_s))

        for fut in as_completed(futures):
            res: RequestResult = fut.result()
            all_results.append(res)
            if len(all_results) > warmup_count and res.ok:
                ok_durations.append(res.duration_ms)

    ok_count = sum(1 for r in all_results if r.ok)

    sorted_ok = sorted(ok_durations)
    p95 = _quantile(sorted_ok, 0.95)
    p99 = _quantile(sorted_ok, 0.99)
    p50 = _quantile(sorted_ok, 0.50)
    avg = (sum(sorted_ok) / len(sorted_ok)) if sorted_ok else float("nan")

    report = {
        "generatedAt": _now_iso(),
        "host": socket.gethostname(),
        "backendBaseUrl": args.backend_base_url,
        "questionsCount": len(questions),
        "repetitions": args.repetitions,
        "warmup": args.warmup,
        "concurrency": args.concurrency,
        "timeoutSeconds": args.timeout_s,
        "totalRequests": total,
        "okRequests": ok_count,
        "kpisMs": {
            "p50": p50,
            "p95": p95,
            "p99": p99,
            "avg": avg,
        },
    }

    if args.output_json:
        report["requests"] = [r.__dict__ for r in all_results]
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"OK: resultados guardados en {args.output_json}")
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

