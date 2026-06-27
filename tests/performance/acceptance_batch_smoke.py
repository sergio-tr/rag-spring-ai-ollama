#!/usr/bin/env python3
"""
Lightweight acceptance-batch performance smoke (Stage A/B/C).

Measures sequential API chat latency per question and batch wall-clock duration.
Does not score answers — only HTTP status, terminal job status, and timing.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from common import now_iso, quantile

DEMO_BEST_PRESET_ID = "cafe0001-0001-4001-8001-000000000003"

STAGE_A_IDS = ["FD-CD-01", "FD-FL-01", "FD-FL-03", "FD-GF-03", "FD-GF-06"]

STAGE_B_IDS = [
    "FD-CD-02",
    "FD-CD-03",
    "FD-CE-01",
    "FD-CE-02",
    "FD-FP-01",
    "FD-FP-02",
    "FD-GD-01",
    "FD-GD-02",
    "FD-GF-01",
    "FD-GF-02",
    "FD-GF-03",
    "FD-SM-01",
    "FD-SM-02",
    "FD-BQ-01",
    "FD-BQ-02",
    "FD-FL-01",
    "FD-FL-02",
    "FD-CL-01",
    "FD-MEM-01",
    "FD-MEM-02",
    "FD-ISO-01",
]

STAGE_C_EXTRA_IDS = ["FD-CD-01", "FD-CD-04", "FD-BQ-03", "FD-FL-03", "FD-GF-04", "FD-GF-05", "FD-GF-06", "FD-GF-07"]

STAGE_C_IDS = sorted(set(STAGE_B_IDS + STAGE_C_EXTRA_IDS), key=lambda x: (x.split("-")[1], x))


@dataclass
class QueryTiming:
    stage: str
    question_id: str
    question: str
    post_status: int | None
    job_status: str | None
    duration_ms: float
    error: str | None = None


def _env(name: str, default: str | None = None) -> str | None:
    v = os.environ.get(name)
    return v.strip() if v and v.strip() else default


def _request(
    base: str,
    method: str,
    path: str,
    timeout_s: float,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> tuple[int | None, Any, float, str | None]:
    url = base.rstrip("/") + (path if path.startswith("/") else f"/{path}")
    headers = {"Accept": "application/json"}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            ms = (time.perf_counter() - start) * 1000.0
            try:
                payload: Any = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                payload = raw
            return resp.status, payload, ms, None
    except urllib.error.HTTPError as e:
        ms = (time.perf_counter() - start) * 1000.0
        return e.code, None, ms, f"HTTPError:{e.code}"
    except Exception as e:  # noqa: BLE001
        ms = (time.perf_counter() - start) * 1000.0
        return None, None, ms, f"{type(e).__name__}:{e}"


def _login(base: str, product: str, timeout_s: float) -> tuple[str | None, str | None]:
    email = _env("PERF_EMAIL", "dev@local.test")
    password = _env("PERF_PASSWORD", "dev")
    code, payload, _ms, err = _request(
        base, "POST", f"{product}/auth/login", timeout_s, body={"email": email, "password": password}
    )
    if code != 200 or not isinstance(payload, dict):
        return None, err or f"login status {code}"
    token = payload.get("accessToken")
    if not isinstance(token, str):
        return None, "missing accessToken"
    return token, None


def _setup_conversation(base: str, product: str, token: str, timeout_s: float) -> tuple[str | None, str | None]:
    code, payload, _ms, err = _request(
        base, "POST", f"{product}/projects", timeout_s, token, {"name": f"perf-smoke-{int(time.time())}"}
    )
    if code != 201 or not isinstance(payload, dict):
        return None, err or f"create project {code}"
    project_id = payload.get("id")
    code, _, _ms, err = _request(base, "PUT", f"{product}/projects/{project_id}/activate", timeout_s, token)
    if code != 200:
        return None, err or f"activate {code}"
    code, payload, _ms, err = _request(
        base, "POST", f"{product}/projects/{project_id}/conversations", timeout_s, token, {}
    )
    if code != 201 or not isinstance(payload, dict):
        return None, err or f"create conversation {code}"
    conv_id = payload.get("id")
    code, _, _ms, err = _request(
        base,
        "PATCH",
        f"{product}/conversations/{conv_id}",
        timeout_s,
        token,
        {"presetId": DEMO_BEST_PRESET_ID},
    )
    if code != 200:
        return None, err or f"patch preset {code}"
    return conv_id, None


def _poll_job(
    base: str, product: str, token: str, job_id: str, poll_timeout_s: float, poll_interval_s: float
) -> tuple[str | None, float, str | None]:
    path = f"{product}/lab/jobs/{job_id}"
    deadline = time.perf_counter() + poll_timeout_s
    total_ms = 0.0
    last_status: str | None = None
    while time.perf_counter() < deadline:
        code, payload, ms, err = _request(base, "GET", path, timeout_s=min(15.0, poll_timeout_s), token=token)
        total_ms += ms
        if err or code != 200:
            return None, total_ms, err or f"poll {code}"
        if isinstance(payload, dict) and payload.get("terminal") is True:
            last_status = str(payload.get("status") or "")
            return last_status, total_ms, None
        time.sleep(poll_interval_s)
    return last_status, total_ms, "poll-timeout"


def _ask(
    base: str,
    product: str,
    token: str,
    conversation_id: str,
    question: str,
    timeout_s: float,
    poll_timeout_s: float,
    poll_interval_s: float,
) -> tuple[int | None, str | None, float, str | None]:
    start = time.perf_counter()
    code, payload, post_ms, err = _request(
        base,
        "POST",
        f"{product}/conversations/{conversation_id}/messages",
        timeout_s,
        token,
        {"content": question, "llmModel": None},
    )
    if err or code not in {200, 202}:
        return code, None, (time.perf_counter() - start) * 1000.0, err or f"post {code}"
    job_id = payload.get("jobId") if isinstance(payload, dict) else None
    if not isinstance(job_id, str):
        return code, None, (time.perf_counter() - start) * 1000.0, "missing jobId"
    job_status, _poll_ms, poll_err = _poll_job(base, product, token, job_id, poll_timeout_s, poll_interval_s)
    total_ms = (time.perf_counter() - start) * 1000.0
    return code, job_status, total_ms, poll_err


def _load_questions(csv_path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    with csv_path.open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            out[row["questionId"]] = row["question"]
    return out


def _run_batch(
    stage: str,
    ids: list[str],
    questions: dict[str, str],
    base: str,
    product: str,
    token: str,
    timeout_s: float,
    poll_timeout_s: float,
    poll_interval_s: float,
    fresh_conversation_per_query: bool,
) -> tuple[list[QueryTiming], float]:
    results: list[QueryTiming] = []
    batch_start = time.perf_counter()
    conversation_id: str | None = None
    for qid in ids:
        if fresh_conversation_per_query or conversation_id is None:
            conversation_id, err = _setup_conversation(base, product, token, timeout_s)
            if err:
                results.append(
                    QueryTiming(stage, qid, questions.get(qid, ""), None, None, 0.0, f"setup:{err}")
                )
                continue
        question = questions.get(qid, qid)
        post_code, job_status, ms, err = _ask(
            base, product, token, conversation_id or "", question, timeout_s, poll_timeout_s, poll_interval_s
        )
        is_5xx = post_code is not None and post_code >= 500
        ok_job = job_status in {"SUCCEEDED", "DONE"}
        results.append(
            QueryTiming(
                stage,
                qid,
                question,
                post_code,
                job_status,
                ms,
                err if err or is_5xx or not ok_job else None,
            )
        )
    return results, (time.perf_counter() - batch_start) * 1000.0


def _evaluation_timeout_probe(base: str, product: str, token: str, timeout_s: float) -> dict[str, Any]:
    probes: list[dict[str, Any]] = []
    # Fast JSON error path (not a timeout, but validates endpoint responsiveness).
    code, _payload, ms, err = _request(
        base,
        "POST",
        f"{product}/lab/benchmarks/LLM_JUDGE_QA/runs",
        timeout_s,
        token,
        {"datasetId": "00000000-0000-4000-8000-000000000001", "runKind": "PRODUCT_EXPLORATION", "name": "perf-timeout-probe"},
    )
    probes.append({"name": "benchmark_invalid_dataset", "status_code": code, "duration_ms": ms, "error": err})
    # Artificial client timeout against lab/status.
    short_timeout = min(0.001, timeout_s)
    _code, _payload, short_ms, short_err = _request(base, "GET", f"{product}/lab/status", short_timeout, token)
    probes.append(
        {
            "name": "client_timeout_lab_status",
            "status_code": _code,
            "duration_ms": short_ms,
            "error": short_err,
            "expected_timeout": short_err is not None,
        }
    )
    return {"probes": probes}


def _write_latency_csv(path: Path, rows: list[QueryTiming], batches: dict[str, float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["kind", "stage", "question_id", "post_status", "job_status", "duration_ms", "error"])
        w.writerow(["single", "single", "FD-GF-02", "", "", batches.get("single_ms", ""), ""])
        for stage, wall in batches.items():
            if stage.endswith("_wall_ms"):
                w.writerow(["batch_wall", stage.replace("_wall_ms", ""), "", "", "", wall, ""])
        for r in rows:
            w.writerow(["query", r.stage, r.question_id, r.post_status or "", r.job_status or "", f"{r.duration_ms:.1f}", r.error or ""])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-base-url", default=_env("PERF_BACKEND_BASE_URL", "http://127.0.0.1:9000"))
    parser.add_argument("--product-prefix", default=_env("PERF_PRODUCT_PREFIX", "/api/v5"))
    parser.add_argument("--questions-csv", default="rag-service/src/main/resources/evaluation/functional-defense-subset/functional_subset_questions.csv")
    parser.add_argument("--timeout-s", type=float, default=30.0)
    parser.add_argument("--poll-timeout-s", type=float, default=180.0)
    parser.add_argument("--poll-interval-s", type=float, default=0.8)
    parser.add_argument("--skip-stage-c", action="store_true")
    parser.add_argument("--output-json", required=True)
    parser.add_argument("--output-csv", default=None)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    csv_path = Path(args.questions_csv)
    if not csv_path.is_absolute():
        csv_path = repo_root / csv_path
    questions = _load_questions(csv_path)
    product = args.product_prefix.rstrip("/")
    base = args.backend_base_url

    token, login_err = _login(base, product, args.timeout_s)
    if login_err:
        print(f"login failed: {login_err}", file=sys.stderr)
        return 2

    health_code, _, _, _ = _request(base, "GET", "/actuator/health", args.timeout_s)
    if health_code != 200:
        print(f"health not OK: {health_code}", file=sys.stderr)
        return 2

    all_rows: list[QueryTiming] = []
    batches: dict[str, float] = {}

    conv_id, setup_err = _setup_conversation(base, product, token or "", args.timeout_s)
    if setup_err:
        print(f"setup failed: {setup_err}", file=sys.stderr)
        return 2
    single_q = questions.get("FD-GF-02", "¿Quién fue el presidente en el acta del 25/02/2026?")
    _post, job_status, single_ms, single_err = _ask(
        base, product, token or "", conv_id or "", single_q, args.timeout_s, args.poll_timeout_s, args.poll_interval_s
    )
    batches["single_ms"] = single_ms
    all_rows.append(QueryTiming("single", "FD-GF-02", single_q, _post, job_status, single_ms, single_err))

    for stage_name, ids in [("stage_a", STAGE_A_IDS), ("stage_b", STAGE_B_IDS)]:
        rows, wall = _run_batch(
            stage_name,
            ids,
            questions,
            base,
            product,
            token or "",
            args.timeout_s,
            args.poll_timeout_s,
            args.poll_interval_s,
            fresh_conversation_per_query=True,
        )
        all_rows.extend(rows)
        batches[f"{stage_name}_wall_ms"] = wall

    if not args.skip_stage_c:
        rows, wall = _run_batch(
            "stage_c",
            STAGE_C_IDS,
            questions,
            base,
            product,
            token or "",
            args.timeout_s,
            args.poll_timeout_s,
            args.poll_interval_s,
            fresh_conversation_per_query=True,
        )
        all_rows.extend(rows)
        batches[f"stage_c_wall_ms"] = wall

    eval_probes = _evaluation_timeout_probe(base, product, token or "", args.timeout_s)

    health_after, _, _, _ = _request(base, "GET", "/actuator/health", args.timeout_s)
    failures = [r for r in all_rows if r.error or (r.post_status is not None and r.post_status >= 500)]
    durations = sorted(r.duration_ms for r in all_rows if r.duration_ms > 0)

    report = {
        "generatedAt": now_iso(),
        "kind": "acceptance_batch_smoke",
        "backendBaseUrl": base,
        "singleQueryMs": single_ms,
        "batchWallMs": batches,
        "queryCount": len(all_rows),
        "failures": len(failures),
        "fivexxCount": sum(1 for r in all_rows if r.post_status is not None and r.post_status >= 500),
        "kpisMs": {
            "p50": quantile(durations, 0.50) if durations else None,
            "p95": quantile(durations, 0.95) if durations else None,
            "max": max(durations) if durations else None,
        },
        "healthBefore": health_code,
        "healthAfter": health_after,
        "evaluationProbes": eval_probes,
        "rows": [asdict(r) for r in all_rows],
        "failedRows": [asdict(r) for r in failures],
    }

    out = Path(args.output_json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.output_csv:
        _write_latency_csv(Path(args.output_csv), all_rows, batches)
    print(json.dumps({"failures": len(failures), "batchWallMs": batches, "kpisMs": report["kpisMs"]}, indent=2))
    return 1 if failures or health_after != 200 else 0


if __name__ == "__main__":
    raise SystemExit(main())
