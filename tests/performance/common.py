"""Shared helpers for micro-benchmark scripts (quantiles, report v1, token heuristics)."""

from __future__ import annotations

import json
import math
import socket
import math
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class LegacyRequestRecord:
    question: str
    ok: bool
    status_code: int | None
    duration_ms: float
    error: str | None
    answer_preview: str | None
    query_type: str | None
    used_tool: bool | None
    token_estimate: dict[str, Any] | None

SCHEMA_VERSION = "1.0"


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _json_float(x: float) -> float | None:
    if math.isnan(x):
        return None
    return x


def quantile(sorted_values: list[float], q: float) -> float:
    if not sorted_values:
        return float("nan")
    if len(sorted_values) == 1:
        return sorted_values[0]
    idx = int(round(q * (len(sorted_values) - 1)))
    idx = max(0, min(idx, len(sorted_values) - 1))
    return sorted_values[idx]


def estimate_tokens_chars_over_4(question: str, answer: str | None) -> dict[str, Any]:
    """
    Rough token proxy: ceil(len/4). Not a substitute for real tokenizer counts.
    Marked estimated: true in reports.
    """
    q = question or ""
    a = answer or ""
    prompt_est = max(0, int(math.ceil(len(q) / 4.0)))
    completion_est = max(0, int(math.ceil(len(a) / 4.0)))
    return {
        "method": "chars_over_4",
        "estimated": True,
        "promptTokens": prompt_est,
        "completionTokens": completion_est,
        "totalTokens": prompt_est + completion_est,
    }


def load_pricing(path: str | None) -> dict[str, Any]:
    """Load optional per-model USD per 1K tokens: { models: { \"model-name\": { input, output } } }."""
    if not path:
        return {}
    p = Path(path)
    if not p.is_file():
        return {}
    with p.open("r", encoding="utf-8") as f:
        return json.load(f)


def cost_usd_estimate(
    total_tokens: float,
    model_id: str | None,
    pricing: dict[str, Any],
) -> dict[str, Any] | None:
    if not pricing or not model_id:
        return None
    models = pricing.get("models") if isinstance(pricing, dict) else None
    if not isinstance(models, dict):
        return None
    entry = models.get(model_id)
    if not isinstance(entry, dict):
        return None
    # Single blended rate per 1K tokens (optional)
    blended = entry.get("per1kTokensUsd")
    if isinstance(blended, (int, float)):
        return {
            "estimated": True,
            "total": total_tokens / 1000.0 * float(blended),
            "pricingSource": "pricing.yaml",
            "modelId": model_id,
        }
    inp = entry.get("inputPer1kUsd")
    out = entry.get("outputPer1kUsd")
    if isinstance(inp, (int, float)) and isinstance(out, (int, float)):
        # Without split counts, use average of input/output rates on total
        mid = (float(inp) + float(out)) / 2.0
        return {
            "estimated": True,
            "total": total_tokens / 1000.0 * mid,
            "pricingSource": "pricing.yaml",
            "modelId": model_id,
            "note": "prompt/completion split unknown; used average of input/output rates",
        }
    return None


def build_summary(
    ok_durations: list[float],
    total_requests: int,
    ok_count: int,
    window_s: float,
) -> dict[str, Any]:
    sorted_ok = sorted(ok_durations)
    tput = (len(ok_durations) / window_s) if window_s > 0 else 0.0
    return {
        "totalRequests": total_requests,
        "okRequests": ok_count,
        "errorRate": (total_requests - ok_count) / total_requests if total_requests else 0.0,
        "durationWindowSeconds": window_s,
        "throughputBenchmarkRps": tput,
        "kpisMs": {
            "p50": _json_float(quantile(sorted_ok, 0.50)),
            "p95": _json_float(quantile(sorted_ok, 0.95)),
            "p99": _json_float(quantile(sorted_ok, 0.99)),
            "avg": _json_float(sum(sorted_ok) / len(sorted_ok)) if sorted_ok else None,
        },
    }


def aggregate_token_estimates(records: list[dict[str, Any]]) -> dict[str, float]:
    totals: dict[str, float] = {
        "promptTokensSum": 0.0,
        "completionTokensSum": 0.0,
        "totalTokensSum": 0.0,
    }
    n = 0
    for r in records:
        te = r.get("tokenEstimate")
        if not isinstance(te, dict):
            continue
        totals["promptTokensSum"] += float(te.get("promptTokens") or 0)
        totals["completionTokensSum"] += float(te.get("completionTokens") or 0)
        totals["totalTokensSum"] += float(te.get("totalTokens") or 0)
        n += 1
    if n:
        totals["promptTokensAvg"] = totals["promptTokensSum"] / n
        totals["completionTokensAvg"] = totals["completionTokensSum"] / n
        totals["totalTokensAvg"] = totals["totalTokensSum"] / n
    return totals


def base_report_v1(
    *,
    benchmark_family: str,
    transport: str,
    scenario: dict[str, Any],
    api_block: dict[str, Any],
) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": now_iso(),
        "host": socket.gethostname(),
        "benchmarkFamily": benchmark_family,
        "transport": transport,
        "scenario": scenario,
        "api": api_block,
    }
