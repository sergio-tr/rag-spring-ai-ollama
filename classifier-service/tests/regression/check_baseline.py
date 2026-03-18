#!/usr/bin/env python3
"""
Check classifier regression against a captured baseline (HTTP).

Run from classifier-service root:

  python tests/regression/check_baseline.py

Exit code 0 if all queryType values match baseline; 1 otherwise.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from datetime import datetime, timezone
from typing import Any

from baseline_lib import default_baseline_json_path


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _post_json(url: str, payload: dict[str, Any], timeout_s: float) -> tuple[int, Any]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        status = getattr(resp, "status", None)  # type: ignore[attr-defined]
        body = resp.read().decode("utf-8", errors="replace")
        return int(status), json.loads(body)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--classifier-base-url", default="http://localhost:8000")
    p.add_argument("--baseline-json", default=str(default_baseline_json_path()))
    p.add_argument("--timeout-s", type=float, default=180.0)
    args = p.parse_args()

    with open(args.baseline_json, "r", encoding="utf-8") as f:
        baseline = json.load(f)

    model_id = baseline.get("modelId", "default")
    classifications: dict[str, Any] = baseline.get("classifications", {})
    questions = list(classifications.keys())

    if not questions:
        print("Baseline has no questions.", file=sys.stderr)
        return 2

    base = args.classifier_base_url.rstrip("/")
    mismatches: list[dict[str, Any]] = []
    ok_count = 0

    for q in questions:
        expected = classifications[q].get("queryType") if isinstance(classifications[q], dict) else None
        try:
            payload = {"query": q, "modelId": model_id}
            status, body = _post_json(f"{base}/classify", payload, timeout_s=args.timeout_s)
            actual = body.get("queryType")
            if actual == expected:
                ok_count += 1
            else:
                mismatches.append(
                    {"question": q, "expected": expected, "actual": actual, "status": status}
                )
        except Exception as e:
            mismatches.append(
                {
                    "question": q,
                    "expected": expected,
                    "actual": None,
                    "error": f"{type(e).__name__}: {e}",
                }
            )

    report = {
        "checkedAt": _now_iso(),
        "classifierBaseUrl": args.classifier_base_url,
        "baselineJson": args.baseline_json,
        "modelId": model_id,
        "questionsCount": len(questions),
        "okCount": ok_count,
        "mismatchesCount": len(mismatches),
        "mismatches": mismatches,
    }

    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if not mismatches else 1


if __name__ == "__main__":
    raise SystemExit(main())
