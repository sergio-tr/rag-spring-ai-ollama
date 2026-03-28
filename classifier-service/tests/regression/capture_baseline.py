#!/usr/bin/env python3
"""
Capture a manual regression baseline for this service (HTTP against a running instance).
Run from classifier-service root:

  python tests/regression/capture_baseline.py [--include-evaluation]

Defaults: questions from tests/regression/questions.txt, output tests/regression/classifier_regression_baseline.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from typing import Any

from baseline_lib import (
    default_baseline_json_path,
    default_questions_path,
    read_questions,
)


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


def _try_post_evaluate(url: str, params: dict[str, Any], timeout_s: float) -> dict[str, Any]:
    query = urllib.parse.urlencode(params)
    full = f"{url}?{query}"
    try:
        status, body = _post_json(full, {}, timeout_s=timeout_s)
        return {"ok": True, "status": status, "body": body}
    except Exception as e:
        return {"ok": False, "error": f"{type(e).__name__}: {e}"}


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--classifier-base-url", default="http://localhost:8000")
    p.add_argument("--model-id", default="default")
    p.add_argument(
        "--questions-file",
        default=str(default_questions_path()),
        help="Path to questions.txt (one question per line)",
    )
    p.add_argument(
        "--output-json",
        default=str(default_baseline_json_path()),
        help="Where to write baseline JSON",
    )
    p.add_argument("--include-evaluation", action="store_true")
    p.add_argument("--timeout-s", type=float, default=180.0)
    args = p.parse_args()

    qpath = Path(args.questions_file)
    questions = read_questions(qpath)
    if not questions:
        print("Empty questions dataset.", file=sys.stderr)
        return 2

    baseline: dict[str, Any] = {
        "generatedAt": _now_iso(),
        "classifierBaseUrl": args.classifier_base_url,
        "modelId": args.model_id,
        "questionsCount": len(questions),
        "classifications": {},
    }

    for q in questions:
        payload = {"query": q, "modelId": args.model_id}
        try:
            status, body = _post_json(
                f"{args.classifier_base_url.rstrip('/')}/classify",
                payload,
                timeout_s=args.timeout_s,
            )
            qt = body.get("queryType")
            baseline["classifications"][q] = {"queryType": qt, "status": status}
        except urllib.error.HTTPError as e:
            baseline["classifications"][q] = {"queryType": None, "status": int(e.code), "error": str(e)}
        except Exception as e:
            baseline["classifications"][q] = {"queryType": None, "status": None, "error": f"{type(e).__name__}: {e}"}

    if args.include_evaluation:
        baseline["evaluationBaseline"] = _try_post_evaluate(
            f"{args.classifier_base_url.rstrip('/')}/evaluate",
            params={"modelId": args.model_id, "includeImages": "false"},
            timeout_s=args.timeout_s,
        )

    out = Path(args.output_json)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as f:
        json.dump(baseline, f, ensure_ascii=False, indent=2)

    print(f"OK: baseline written to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
