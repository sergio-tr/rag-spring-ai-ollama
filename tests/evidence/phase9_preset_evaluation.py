#!/usr/bin/env python3
"""Phase 9 — preset evaluation evidence collector (API-backed)."""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EVIDENCE = REPO_ROOT / "docs/evidence/final-engineering-hardening-autonomous-20260627/09_preset_evaluation"
REFERENCE_DATASET_ID = "00000000-0000-7000-8000-000000000001"
QUESTIONS_CSV = REPO_ROOT / "rag-service/src/main/resources/evaluation/functional-defense-subset/functional_subset_questions.csv"

DEMO_PRESETS = {
    "Demo_Best": "cafe0001-0001-4001-8001-000000000003",
    "Demo_NaiveFullCorpus": "cafe0001-0001-4001-8001-000000000002",
    "Demo_Worst": "cafe0001-0001-4001-8001-000000000001",
}

STAGE_A_IDS = ["FD-CD-01", "FD-FL-01", "FD-FL-03", "FD-GF-03", "FD-GF-06"]

CORPUS_FIXTURE = REPO_ROOT / "rag-service/src/test/resources/acta-fixtures/acta-1.txt"
if not CORPUS_FIXTURE.is_file():
    CORPUS_FIXTURE = REPO_ROOT / "webapp/e2e/fixtures/files/sample.txt"

RAG_SMOKE_PRESETS = ["P0"]  # P1/P2 require preset-specific index materialization on evaluation corpus


@dataclass
class ApiClient:
    base: str
    product: str
    token: str
    timeout: float = 60.0

    def request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        *,
        raw: bytes | None = None,
        content_type: str | None = None,
    ) -> tuple[int, Any, str | None]:
        url = self.base.rstrip("/") + (path if path.startswith("/") else f"/{path}")
        headers = {"Accept": "application/json", "Authorization": f"Bearer {self.token}"}
        data: bytes | None = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        elif raw is not None:
            data = raw
            if content_type:
                headers["Content-Type"] = content_type
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw_body = resp.read()
                if "application/json" in resp.headers.get("Content-Type", ""):
                    return resp.status, json.loads(raw_body), None
                return resp.status, raw_body.decode("utf-8", errors="replace"), None
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            try:
                return e.code, json.loads(err_body), err_body
            except json.JSONDecodeError:
                return e.code, err_body, err_body
        except Exception as e:  # noqa: BLE001
            return 0, None, f"{type(e).__name__}:{e}"


def login(base: str, product: str, email: str, password: str) -> str:
    url = f"{base.rstrip('/')}{product}/auth/login"
    req = urllib.request.Request(
        url,
        data=json.dumps({"email": email, "password": password}).encode(),
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = json.loads(resp.read())
    token = body.get("accessToken")
    if not isinstance(token, str):
        raise RuntimeError("login missing accessToken")
    return token


def load_questions() -> dict[str, str]:
    out: dict[str, str] = {}
    with QUESTIONS_CSV.open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            out[row["questionId"]] = row["question"]
    return out


def poll_job(api: ApiClient, job_id: str, timeout_s: float, interval_s: float = 2.0) -> dict[str, Any]:
    deadline = time.time() + timeout_s
    last: dict[str, Any] = {}
    while time.time() < deadline:
        code, body, err = api.request("GET", f"{api.product}/lab/jobs/{job_id}")
        if code != 200 or not isinstance(body, dict):
            raise RuntimeError(f"poll job failed {code}: {err}")
        last = body
        if body.get("terminal"):
            return body
        time.sleep(interval_s)
    raise TimeoutError(f"job {job_id} not terminal after {timeout_s}s; last={last.get('status')}")


def create_corpus_and_index(api: ApiClient) -> str:
    code, created, err = api.request("POST", f"{api.product}/lab/evaluation-corpora", {"name": f"phase9-kb-{int(time.time())}"})
    if code not in (200, 201):
        raise RuntimeError(f"create corpus {code}: {err}")
    corpus_id = str(created["id"])

    boundary = f"----phase9{int(time.time())}"
    file_bytes = CORPUS_FIXTURE.read_bytes()
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="bootstrap-acta.txt"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
    ).encode() + file_bytes + f"\r\n--{boundary}--\r\n".encode()
    code, _, err = api.request(
        "POST",
        f"{api.product}/lab/evaluation-corpora/{corpus_id}/documents",
        raw=body,
        content_type=f"multipart/form-data; boundary={boundary}",
    )
    if code not in (200, 201):
        raise RuntimeError(f"upload corpus doc {code}: {err}")

    deadline = time.time() + 120
    while time.time() < deadline:
        code, summary, _ = api.request("GET", f"{api.product}/lab/evaluation-corpora/{corpus_id}")
        if code == 200 and isinstance(summary, dict) and (summary.get("readyCount") or 0) >= 1:
            break
        time.sleep(2)
    else:
        raise RuntimeError("corpus documents not READY")

    code, _, err = api.request("POST", f"{api.product}/lab/evaluation-corpora/{corpus_id}/prepare-index")
    if code not in (200, 201):
        raise RuntimeError(f"prepare-index {code}: {err}")

    deadline = time.time() + 180
    while time.time() < deadline:
        code, readiness, _ = api.request("GET", f"{api.product}/lab/evaluation-corpora/{corpus_id}/readiness")
        if code == 200 and isinstance(readiness, dict):
            if readiness.get("activeSnapshotId") or readiness.get("reindexRequired") is False:
                return corpus_id
        time.sleep(2)
    raise RuntimeError("corpus index not ready")


def start_benchmark(api: ApiClient, kind: str, payload: dict[str, Any]) -> dict[str, Any]:
    code, body, err = api.request("POST", f"{api.product}/lab/benchmarks/{kind}/runs", payload)
    if code != 202:
        raise RuntimeError(f"start {kind} {code}: {err}")
    return body  # type: ignore[return-value]


def export_run_items(api: ApiClient, run_id: str) -> list[dict[str, Any]]:
    code, body, err = api.request("GET", f"{api.product}/lab/runs/{run_id}/items")
    if code != 200:
        raise RuntimeError(f"run items {code}: {err}")
    return body if isinstance(body, list) else []


def export_campaign_json(api: ApiClient, campaign_id: str, name: str) -> dict[str, Any]:
    code, body, err = api.request("GET", f"{api.product}/lab/campaigns/{campaign_id}/export/campaign-items.json")
    if code != 200:
        raise RuntimeError(f"campaign export {code}: {err}")
    return body if isinstance(body, dict) else {"raw": body}


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def collect_inventory(api: ApiClient) -> dict[str, Any]:
    inv: dict[str, Any] = {"collectedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    for t in ("LLM", "EMBEDDING"):
        code, body, err = api.request("GET", f"{api.product}/models?type={t}")
        inv[f"{t.lower()}Models"] = body if code == 200 else {"error": err}
    code, catalog, err = api.request("GET", f"{api.product}/chat/presets/catalog")
    inv["presetCatalog"] = catalog if code == 200 else {"error": err}
    code, status, _ = api.request("GET", f"{api.product}/lab/status")
    inv["labStatus"] = status if code == 200 else {}
    return inv


def llm_row_from_item(item: dict[str, Any], model: str) -> dict[str, str]:
    mvp = item.get("mvp") or {}
    metrics = mvp.get("metrics") or {}
    return {
        "benchmarkKind": "LLM_JUDGE_QA",
        "modelId": model,
        "questionId": str(item.get("questionId") or ""),
        "outcome": str(item.get("outcome") or mvp.get("outcome") or ""),
        "score": str(metrics.get("finalScore") or metrics.get("score") or ""),
        "semanticScore": str(metrics.get("semanticScore") or ""),
        "latencyMs": str(metrics.get("latencyMs") or item.get("latencyMs") or ""),
        "presetCode": str(item.get("presetCode") or ""),
    }


def embedding_row_from_comparison(row: dict[str, Any]) -> dict[str, str]:
    return {
        "benchmarkKind": "EMBEDDING_RETRIEVAL",
        "embeddingModelId": str(row.get("axisValue") or row.get("groupValue") or ""),
        "totalItems": str(row.get("totalItems") or ""),
        "executed": str(row.get("executed") or ""),
        "failed": str(row.get("failed") or ""),
        "skipped": str(row.get("skipped") or ""),
        "meanRecallAt1": str(row.get("meanRecallAt1") or ""),
        "meanExactMatch": str(row.get("meanExactMatch") or ""),
        "meanLatencyMs": str(row.get("meanLatencyMs") or ""),
        "scoreGlobal": str(row.get("scoreGlobal") or ""),
    }


def rag_row_from_item(item: dict[str, Any]) -> dict[str, str]:
    mvp = item.get("mvp") or {}
    metrics = mvp.get("metrics") or {}
    operational = mvp.get("operational") or {}
    return {
        "benchmarkKind": "RAG_PRESET_END_TO_END",
        "presetCode": str(item.get("presetCode") or operational.get("presetCode") or ""),
        "questionId": str(item.get("questionId") or ""),
        "outcome": str(item.get("outcome") or operational.get("outcome") or ""),
        "score": str(metrics.get("finalScore") or metrics.get("score") or ""),
        "latencyMs": str(metrics.get("latencyMs") or ""),
        "route": str(operational.get("route") or operational.get("routingPath") or ""),
    }


def chat_preset_row(preset: str, qid: str, job: dict[str, Any], assistant: str) -> dict[str, str]:
    result = job.get("result") or {}
    meta = result.get("executionMetadata") if isinstance(result, dict) else {}
    if not isinstance(meta, dict):
        meta = {}
    return {
        "preset": preset,
        "questionId": qid,
        "jobStatus": str(job.get("status") or ""),
        "latencyMs": str(meta.get("latencyMs") or ""),
        "retrievalUsed": str(meta.get("retrievalUsed") or meta.get("useRetrieval") or ""),
        "answerPreview": (assistant or "")[:240].replace("\n", " "),
    }


def setup_conversation(api: ApiClient, preset_id: str) -> str:
    code, proj, err = api.request("POST", f"{api.product}/projects", {"name": f"phase9-{int(time.time())}"})
    if code != 201:
        raise RuntimeError(f"create project {code}: {err}")
    pid = proj["id"]
    api.request("PUT", f"{api.product}/projects/{pid}/activate")
    code, conv, err = api.request("POST", f"{api.product}/projects/{pid}/conversations", {})
    if code != 201:
        raise RuntimeError(f"create conversation {code}: {err}")
    cid = conv["id"]
    code, _, err = api.request("PATCH", f"{api.product}/conversations/{cid}", {"presetId": preset_id})
    if code != 200:
        raise RuntimeError(f"patch preset {code}: {err}")
    return str(cid)


def ask_chat(api: ApiClient, conversation_id: str, question: str, poll_timeout: float) -> tuple[dict[str, Any], str]:
    code, post, err = api.request(
        "POST",
        f"{api.product}/conversations/{conversation_id}/messages",
        {"content": question, "llmModel": None},
    )
    if code not in (200, 202):
        raise RuntimeError(f"post message {code}: {err}")
    job = poll_job(api, str(post["jobId"]), poll_timeout)
    code, msgs, _ = api.request("GET", f"{api.product}/conversations/{conversation_id}/messages")
    assistant = ""
    if code == 200 and isinstance(msgs, list):
        for m in reversed(msgs):
            if m.get("role") == "ASSISTANT":
                assistant = str(m.get("content") or "")
                break
    return job, assistant


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = list(rows[0].keys())
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, default=DEFAULT_EVIDENCE)
    parser.add_argument("--backend", default=os.environ.get("API_BASE_URL", "http://127.0.0.1:9000"))
    parser.add_argument("--product", default="/api/v5")
    parser.add_argument("--email", default="dev@local.test")
    parser.add_argument("--password", default="dev")
    parser.add_argument("--llm-poll-timeout", type=float, default=3600.0)
    parser.add_argument("--rag-poll-timeout", type=float, default=1800.0)
    parser.add_argument("--chat-poll-timeout", type=float, default=240.0)
    parser.add_argument("--skip-llm-run", action="store_true")
    parser.add_argument("--skip-rag-run", action="store_true")
    args = parser.parse_args()

    evidence = args.evidence_dir
    raw_dir = evidence / "raw_outputs"
    evidence.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)

    token = login(args.backend, args.product, args.email, args.password)
    api = ApiClient(args.backend, args.product, token)

    inventory = collect_inventory(api)
    write_json(evidence / "inventory.json", inventory)

    questions = load_questions()
    llm_rows: list[dict[str, str]] = []
    embedding_rows: list[dict[str, str]] = []
    rag_rows: list[dict[str, str]] = []
    preset_rows: list[dict[str, str]] = []
    raw_index: list[dict[str, str]] = []

    # --- Embedding: reuse latest SUCCEEDED or note ---
    code, emb_latest, _ = api.request("GET", f"{api.product}/lab/benchmarks/EMBEDDING_RETRIEVAL/runs/latest")
    if code == 200 and isinstance(emb_latest, dict) and emb_latest.get("status") == "SUCCEEDED":
        campaign_id = emb_latest.get("campaignId")
        write_json(raw_dir / "embedding_latest_run.json", emb_latest)
        raw_index.append({"artifact": "raw_outputs/embedding_latest_run.json", "kind": "EMBEDDING_RETRIEVAL"})
        if campaign_id:
            cmp_code, comparison, _ = api.request("GET", f"{api.product}/lab/campaigns/{campaign_id}/comparison")
            write_json(raw_dir / "embedding_campaign_comparison.json", comparison if cmp_code == 200 else {})
            raw_index.append({"artifact": "raw_outputs/embedding_campaign_comparison.json", "kind": "EMBEDDING"})
            for row in (comparison.get("rows") if isinstance(comparison, dict) else []) or []:
                embedding_rows.append(embedding_row_from_comparison(row))
            try:
                items = export_campaign_json(api, str(campaign_id), "embedding")
                write_json(raw_dir / "embedding_campaign_items.json", items)
                raw_index.append({"artifact": "raw_outputs/embedding_campaign_items.json", "kind": "EMBEDDING"})
            except RuntimeError as e:
                embedding_rows.append({"benchmarkKind": "EMBEDDING_RETRIEVAL", "embeddingModelId": "ERROR", "totalItems": "0", "executed": "0", "failed": "1", "skipped": "0", "meanRecallAt1": "", "meanExactMatch": "", "meanLatencyMs": "", "scoreGlobal": str(e)})

    # --- LLM benchmark ---
    if not args.skip_llm_run:
        llm_models = [m["modelId"] for m in (inventory.get("llmModels") or []) if isinstance(m, dict) and m.get("available")]
        model = llm_models[0] if llm_models else "gemma3:4b"
        accepted = start_benchmark(
            api,
            "LLM_JUDGE_QA",
            {
                "datasetId": REFERENCE_DATASET_ID,
                "llmModelIds": [model],
                "name": "phase9-llm-evaluation",
            },
        )
        write_json(raw_dir / "llm_run_accepted.json", accepted)
        raw_index.append({"artifact": "raw_outputs/llm_run_accepted.json", "kind": "LLM"})
        job = poll_job(api, str(accepted["asyncTaskId"]), args.llm_poll_timeout, 3.0)
        write_json(raw_dir / "llm_job_terminal.json", job)
        raw_index.append({"artifact": "raw_outputs/llm_job_terminal.json", "kind": "LLM"})
        run_id = str(accepted.get("evaluationRunId") or "")
        if run_id:
            items = export_run_items(api, run_id)
            write_json(raw_dir / "llm_run_items.json", items)
            raw_index.append({"artifact": "raw_outputs/llm_run_items.json", "kind": "LLM"})
            for item in items:
                if isinstance(item, dict):
                    llm_rows.append(llm_row_from_item(item, model))

    # --- RAG benchmark (smoke presets + Stage A subset) ---
    if not args.skip_rag_run:
        corpus_id = create_corpus_and_index(api)
        accepted = start_benchmark(
            api,
            "RAG_PRESET_END_TO_END",
            {
                "datasetId": REFERENCE_DATASET_ID,
                "corpusId": corpus_id,
                "experimentalPresetCodes": RAG_SMOKE_PRESETS,
                "datasetQuestionIds": STAGE_A_IDS,
                "name": "phase9-rag-smoke",
            },
        )
        write_json(raw_dir / "rag_run_accepted.json", accepted)
        raw_index.append({"artifact": "raw_outputs/rag_run_accepted.json", "kind": "RAG"})
        job = poll_job(api, str(accepted["asyncTaskId"]), args.rag_poll_timeout, 3.0)
        write_json(raw_dir / "rag_job_terminal.json", job)
        raw_index.append({"artifact": "raw_outputs/rag_job_terminal.json", "kind": "RAG"})
        campaign_id = job.get("campaignId") or accepted.get("campaignId")
        if campaign_id:
            cmp_code, comparison, _ = api.request("GET", f"{api.product}/lab/campaigns/{campaign_id}/comparison")
            write_json(raw_dir / "rag_campaign_comparison.json", comparison if cmp_code == 200 else {})
            raw_index.append({"artifact": "raw_outputs/rag_campaign_comparison.json", "kind": "RAG"})
            items_doc = export_campaign_json(api, str(campaign_id), "rag")
            write_json(raw_dir / "rag_campaign_items.json", items_doc)
            raw_index.append({"artifact": "raw_outputs/rag_campaign_items.json", "kind": "RAG"})
            for item in (items_doc.get("items") if isinstance(items_doc, dict) else []) or []:
                if isinstance(item, dict):
                    rag_rows.append(rag_row_from_item(item))

    # --- Chat preset comparison (Demo_* × Stage A) ---
    for preset_name, preset_id in DEMO_PRESETS.items():
        conv_id = setup_conversation(api, preset_id)
        for qid in STAGE_A_IDS:
            q = questions.get(qid, qid)
            try:
                job, assistant = ask_chat(api, conv_id, q, args.chat_poll_timeout)
                preset_rows.append(chat_preset_row(preset_name, qid, job, assistant))
            except Exception as e:  # noqa: BLE001
                preset_rows.append(
                    {
                        "preset": preset_name,
                        "questionId": qid,
                        "jobStatus": "ERROR",
                        "latencyMs": "",
                        "retrievalUsed": "",
                        "answerPreview": str(e)[:240],
                    }
                )

    write_csv(evidence / "LLM_RESULTS.csv", llm_rows)
    write_csv(evidence / "EMBEDDING_RESULTS.csv", embedding_rows)
    write_csv(evidence / "RAG_RESULTS.csv", rag_rows)
    write_csv(evidence / "PRESET_CHAT_RESULTS.csv", preset_rows)
    write_json(evidence / "RAW_OUTPUTS_INDEX.json", raw_index)

    summary = {
        "llmItemCount": len(llm_rows),
        "embeddingRowCount": len(embedding_rows),
        "ragItemCount": len(rag_rows),
        "presetChatRowCount": len(preset_rows),
        "stageAIds": STAGE_A_IDS,
        "ragSmokePresets": RAG_SMOKE_PRESETS,
        "referenceDatasetId": REFERENCE_DATASET_ID,
    }
    write_json(evidence / "phase9_summary.json", summary)
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
