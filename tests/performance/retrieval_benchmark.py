#!/usr/bin/env python3
"""
RAG micro-benchmark (retrieval / LLM families): low concurrency, latency + estimated tokens.

- product_chat: optional PUT project RAG config, then POST chat message + poll lab job (requires BENCHMARK_* env).

Does not replace Gatling load tests. See docs/performance/README.md and API_RESPONSE_AUDIT.md.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import Future, ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, NamedTuple

try:
    import yaml
except ImportError:
    yaml = None  # type: ignore[assignment]

from common import (
    HistoricalQueryRequestRecord,
    aggregate_token_estimates,
    base_report_v1,
    build_summary,
    cost_usd_estimate,
    estimate_tokens_chars_over_4,
    load_pricing,
    now_iso,
)

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_SCENARIOS_DIR = SCRIPT_DIR / "scenarios"

class _ProductChatResult(NamedTuple):
    question: str
    ok: bool
    status_code: int | None
    duration_ms: float
    error: str | None
    job_status: str | None
    answer_preview: str | None
    token_estimate: dict[str, Any] | None


DEFAULT_QUESTIONS = [
    "¿Cuántas actas hay en el corpus?",
    "¿En qué secciones se menciona la palabra 'acta'?",
    "¿Qué actas tratan el tema de la 'luz'?",
]


def _http_json(
    url: str,
    method: str,
    timeout_s: float,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int | None, Any, float, str | None]:
    start = time.perf_counter()
    h = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        h.setdefault("Content-Type", "application/json")
    h.setdefault("Accept", "application/json")
    try:
        req = urllib.request.Request(url, data=data, method=method, headers=h)
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            status = getattr(resp, "status", None)  # type: ignore[attr-defined]
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                return status, json.loads(raw), (time.perf_counter() - start) * 1000.0, None
            except json.JSONDecodeError:
                return status, raw, (time.perf_counter() - start) * 1000.0, "non-json-response"
    except Exception as e:
        duration_ms = (time.perf_counter() - start) * 1000.0
        return None, None, duration_ms, f"{type(e).__name__}: {e}"


def _historical_query_get(
    backend_base: str,
    query_path: str,
    question: str,
    extra_params: dict[str, Any],
    timeout_s: float,
) -> HistoricalQueryRequestRecord:
    base = backend_base.rstrip("/")
    path = query_path if query_path.startswith("/") else f"/{query_path}"
    params: dict[str, str] = {"question": question}
    for k, v in (extra_params or {}).items():
        if v is None or v == "":
            continue
        params[str(k)] = str(v)
    qs = urllib.parse.urlencode(params)
    url = f"{base}{path}?{qs}"
    status, payload, duration_ms, err = _http_json(url, "GET", timeout_s)
    ok = status == 200 and err is None
    answer = None
    qt = None
    used_tool = None
    preview = None
    if ok and isinstance(payload, dict) and payload.get("success") is True:
        data = payload.get("data")
        if isinstance(data, dict):
            answer = data.get("answer")
            if isinstance(answer, str):
                preview = answer[:500]
            qt = data.get("queryType")
            used_tool = data.get("usedTool")
    elif ok:
        ok = False
        err = err or "unexpected-envelope"
    te = None
    if answer is not None:
        te = estimate_tokens_chars_over_4(question, answer)
    return HistoricalQueryRequestRecord(
        question=question,
        ok=ok,
        status_code=status,
        duration_ms=duration_ms,
        error=None if ok else (err or "non-200"),
        answer_preview=preview,
        query_type=qt if isinstance(qt, str) else None,
        used_tool=used_tool if isinstance(used_tool, bool) else None,
        token_estimate=te,
    )


def _product_chat_once(
    backend_base: str,
    product_base: str,
    conversation_id: str,
    question: str,
    token: str,
    llm_model: str | None,
    timeout_s: float,
    poll_interval_s: float,
) -> _ProductChatResult:
    base = backend_base.rstrip("/")
    pb = product_base if product_base.startswith("/") else f"/{product_base}"
    post_url = f"{base}{pb}/conversations/{conversation_id}/messages"
    body: dict[str, Any] = {"content": question}
    if llm_model:
        body["llmModel"] = llm_model
    headers = {"Authorization": f"Bearer {token}"}
    status, payload, post_ms, err = _http_json(post_url, "POST", timeout_s, body=body, headers=headers)
    if status not in (200, 202) or not isinstance(payload, dict):
        return _ProductChatResult(
            question=question,
            ok=False,
            status_code=status,
            duration_ms=post_ms,
            error=err or f"post-failed-{status}",
            job_status=None,
            answer_preview=None,
            token_estimate=None,
        )
    job_id = payload.get("jobId")
    if not job_id:
        return _ProductChatResult(
            question=question,
            ok=False,
            status_code=status,
            duration_ms=post_ms,
            error="missing-jobId",
            job_status=None,
            answer_preview=None,
            token_estimate=None,
        )
    poll_url = f"{base}{pb}/lab/jobs/{job_id}"
    deadline = time.perf_counter() + timeout_s
    total_ms = post_ms
    last_status = None
    answer = None
    first_poll = True
    while time.perf_counter() < deadline:
        if not first_poll:
            time.sleep(poll_interval_s)
        first_poll = False
        st, job_body, poll_ms, perr = _http_json(poll_url, "GET", min(30.0, timeout_s), headers=headers)
        total_ms += poll_ms
        if st != 200 or not isinstance(job_body, dict):
            return _ProductChatResult(
                question=question,
                ok=False,
                status_code=st,
                duration_ms=total_ms,
                error=perr or "poll-error",
                job_status=last_status,
                answer_preview=None,
                token_estimate=None,
            )
        last_status = job_body.get("status")
        if job_body.get("terminal") is True:
            ok = last_status == "SUCCEEDED"
            res = job_body.get("result")
            if isinstance(res, dict):
                ans = res.get("answer")
                if isinstance(ans, str):
                    answer = ans
            te = estimate_tokens_chars_over_4(question, answer) if answer is not None else None
            return _ProductChatResult(
                question=question,
                ok=ok,
                status_code=st,
                duration_ms=total_ms,
                error=None if ok else (job_body.get("errorMessage") or last_status),
                job_status=str(last_status) if last_status is not None else None,
                answer_preview=answer[:500] if isinstance(answer, str) else None,
                token_estimate=te,
            )
    return _ProductChatResult(
        question=question,
        ok=False,
        status_code=status,
        duration_ms=total_ms,
        error="poll-timeout",
        job_status=last_status,
        answer_preview=None,
        token_estimate=None,
    )


def _put_project_config(
    backend_base: str,
    product_base: str,
    project_id: str,
    token: str,
    rag_config: dict[str, Any],
    timeout_s: float,
) -> tuple[bool, str | None]:
    if not rag_config:
        return True, None
    base = backend_base.rstrip("/")
    pb = product_base if product_base.startswith("/") else f"/{product_base}"
    url = f"{base}{pb}/config/project/{project_id}"
    status, _payload, _ms, err = _http_json(
        url,
        "PUT",
        timeout_s,
        body=rag_config,
        headers={"Authorization": f"Bearer {token}"},
    )
    if status == 200 and err is None:
        return True, None
    return False, err or f"http-{status}"


def _load_scenario(name_or_path: str) -> dict[str, Any]:
    p = Path(name_or_path)
    if not p.is_file():
        p = DEFAULT_SCENARIOS_DIR / f"{name_or_path}.yaml"
    if not p.is_file():
        p = DEFAULT_SCENARIOS_DIR / f"{name_or_path}.yml"
    if not p.is_file():
        raise FileNotFoundError(f"Scenario not found: {name_or_path}")
    if yaml is None:
        raise RuntimeError("PyYAML is required: pip install -r tests/performance/requirements.txt")
    with p.open("r", encoding="utf-8") as f:
        raw = yaml.safe_load(f)
    if not isinstance(raw, dict):
        raise ValueError("Scenario root must be a mapping")
    return raw


def _load_questions(path: str) -> list[str]:
    lines: list[str] = []
    with open(path, "r", encoding="utf-8") as f:
        for raw in f:
            s = raw.strip()
            if not s or s.startswith("#"):
                continue
            lines.append(s)
    return lines


def main(default_family: str | None = None) -> int:
    default_product = os.environ.get("RAG_PRODUCT_BASE_PATH", "/api/v5")
    fam_default = default_family or "retrieval"
    p = argparse.ArgumentParser(description="RAG micro-benchmark (schema v1)")
    p.add_argument("--backend-base-url", default=os.environ.get("BENCHMARK_BASE_URL", "http://localhost:9000"))
    p.add_argument(
        "--family",
        choices=("retrieval", "llm"),
        default=os.environ.get("BENCHMARK_FAMILY", fam_default),
        help="retrieval: emphasize path latency; llm: same HTTP path, emphasize token estimates in report.",
    )
    p.add_argument("--scenario", default="baseline", help="Name of scenarios/*.yaml (without dir) or path to YAML")
    p.add_argument("--query-path", default="", help="Unused (historical_query transport removed by migration plan).")
    p.add_argument("--product-base-path", default=default_product)
    p.add_argument("--questions-file", default=None)
    p.add_argument("--questions", default=None, help="JSON array of question strings")
    p.add_argument("--repetitions", type=int, default=5)
    p.add_argument("--warmup", type=int, default=1)
    p.add_argument("--concurrency", type=int, default=1, help="Keep low (<=8); not a load test")
    p.add_argument("--timeout-s", type=float, default=180.0)
    p.add_argument("--poll-interval-s", type=float, default=0.75, help="Product job polling interval")
    p.add_argument("--output-json", default=None)
    p.add_argument("--pricing-yaml", default=None, help="Optional pricing table for rough USD (see pricing.example.yaml)")
    args = p.parse_args()

    scenario = _load_scenario(args.scenario)
    transport = str(scenario.get("transport") or "product_chat").strip().lower()
    if transport not in ("historical_query", "product_chat"):
        print("Invalid scenario transport", transport, file=sys.stderr)
        return 2

    historical_cfg = scenario.get("historical_query") if isinstance(scenario.get("historical_query"), dict) else {}
    query_path = str(historical_cfg.get("query_path") or args.query_path)
    query_params = historical_cfg.get("query_params") if isinstance(historical_cfg.get("query_params"), dict) else {}

    product_cfg = scenario.get("product_chat") if isinstance(scenario.get("product_chat"), dict) else {}
    product_base = str(product_cfg.get("product_base_path") or args.product_base_path)
    rag_config = product_cfg.get("rag_config") if isinstance(product_cfg.get("rag_config"), dict) else {}
    post_llm = product_cfg.get("post_message", {})
    llm_model = post_llm.get("llmModel") if isinstance(post_llm, dict) else None

    if args.questions_file and args.questions:
        print("Use only one of: --questions-file or --questions", file=sys.stderr)
        return 2
    if args.questions_file:
        questions = _load_questions(args.questions_file)
    elif args.questions:
        questions = json.loads(args.questions)
    else:
        questions = DEFAULT_QUESTIONS
    if not questions:
        print("Empty question dataset.", file=sys.stderr)
        return 2

    token = os.environ.get("BENCHMARK_BEARER_TOKEN", "").strip()
    project_id = os.environ.get("BENCHMARK_PROJECT_ID", "").strip()
    conversation_id = os.environ.get("BENCHMARK_CONVERSATION_ID", "").strip()

    if transport == "product_chat":
        if not token or not project_id or not conversation_id:
            print(
                "product_chat requires BENCHMARK_BEARER_TOKEN, BENCHMARK_PROJECT_ID, BENCHMARK_CONVERSATION_ID",
                file=sys.stderr,
            )
            return 2
        ok_prelude, prelude_err = _put_project_config(
            args.backend_base_url, product_base, project_id, token, rag_config, args.timeout_s
        )
        if not ok_prelude:
            print(f"Prelude PUT config failed: {prelude_err}", file=sys.stderr)
            return 1

    all_tasks: list[tuple[str, bool]] = []
    for q in questions:
        for _ in range(args.warmup):
            all_tasks.append((q, True))
        for _ in range(args.repetitions):
            all_tasks.append((q, False))

    t0 = time.perf_counter()
    ok_durations: list[float] = []
    record_rows: list[dict[str, Any]] = []

    def run_one(q: str) -> tuple[float | None, dict[str, Any]]:
        if transport == "historical_query":
            rec = _historical_query_get(args.backend_base_url, query_path, q, query_params, args.timeout_s)
            row = {
                "question": rec.question,
                "ok": rec.ok,
                "statusCode": rec.status_code,
                "durationMs": rec.duration_ms,
                "error": rec.error,
                "queryType": rec.query_type,
                "usedTool": rec.used_tool,
                "tokenEstimate": rec.token_estimate,
            }
            return (rec.duration_ms if rec.ok else None, row)
        rec = _product_chat_once(
            args.backend_base_url,
            product_base,
            conversation_id,
            q,
            token,
            str(llm_model) if llm_model else None,
            args.timeout_s,
            args.poll_interval_s,
        )
        row = {
            "question": rec.question,
            "ok": rec.ok,
            "statusCode": rec.status_code,
            "durationMs": rec.duration_ms,
            "error": rec.error,
            "jobStatus": rec.job_status,
            "tokenEstimate": rec.token_estimate,
        }
        return (rec.duration_ms if rec.ok else None, row)

    with ThreadPoolExecutor(max_workers=max(1, min(args.concurrency, 8))) as pool:
        future_warmup: dict[Future, bool] = {}
        for q, is_warmup in all_tasks:
            fut = pool.submit(run_one, q)
            future_warmup[fut] = is_warmup

        for fut in as_completed(future_warmup):
            is_warmup = future_warmup[fut]
            duration_ok, row = fut.result()
            record_rows.append(row)
            if not is_warmup and duration_ok is not None:
                ok_durations.append(duration_ok)

    window_s = time.perf_counter() - t0
    ok_count = sum(1 for r in record_rows if r.get("ok"))
    total = len(record_rows)

    pricing = load_pricing(args.pricing_yaml)
    chat_model = None
    if isinstance(query_params.get("chatModel"), str):
        chat_model = query_params.get("chatModel")
    if llm_model:
        chat_model = str(llm_model)
    tok_agg = aggregate_token_estimates(record_rows)
    total_tok = tok_agg.get("totalTokensSum", 0.0)
    cost = cost_usd_estimate(total_tok, chat_model, pricing) if pricing else None

    api_block = {
        "backendBaseUrl": args.backend_base_url,
        "historicalQueryPath": query_path if transport == "historical_query" else None,
        "queryParams": query_params if transport == "historical_query" else None,
        "productBasePath": product_base if transport == "product_chat" else None,
        "projectId": project_id if transport == "product_chat" else None,
        "conversationId": conversation_id if transport == "product_chat" else None,
        "modelIdReported": chat_model,
    }

    report = base_report_v1(
        benchmark_family=args.family,
        transport=transport,
        scenario=scenario,
        api_block=api_block,
    )
    report["summary"] = build_summary(ok_durations, total, ok_count, window_s)
    report["tokenEstimates"] = {
        "method": "chars_over_4",
        "estimated": True,
        "note": "No tokenizer counts in API JSON; heuristic from question + answer length",
        "aggregate": tok_agg,
    }
    if cost:
        report["costEstimateUsd"] = cost
    elif args.pricing_yaml:
        report["costEstimateUsd"] = {
            "estimated": False,
            "note": "Missing model id or pricing entry for cost_usd_estimate",
        }

    if args.output_json:
        report["requests"] = record_rows
        report["generatedAt"] = now_iso()
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"OK: results written to {args.output_json}")
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
