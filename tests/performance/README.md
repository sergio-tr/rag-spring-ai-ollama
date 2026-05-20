# Python micro-benchmarks (RAG)

**Purpose:** small-sample latency and **estimated** token metrics — **not** load testing (use **Gatling** for RPS / stress).

## Scripts

| Script | Role |
| --- | --- |
| `retrieval_benchmark.py` | Main runner for `product_chat` (JWT + project + conversation). Historical `GET …/query` remains available only for old-baseline comparison. Schema `benchmark-report-v1` (see `schema/`). |
| `llm_benchmark.py` | Wrapper with `--family llm` default (same HTTP behaviour; report emphasizes token/cost lines). |
| `infra_probe.py` | Simple GET probe (default `/actuator/health`) — infra cold/warm, not RAG. |
| `final_performance_smoke.py` | Final-scope bounded smoke: health/metrics plus optional document, Chat job, and Lab run start/status when `PERF_*` inputs are provided. |
| `performance_baseline.py` | **Deprecated** — forwards to `retrieval_benchmark.py`. |
| `actuator_latency_baseline.py` | **Deprecated** — forwards to `infra_probe.py`. |

## Dependencies

```bash
pip install -r tests/performance/requirements.txt
```

Run from repo root or `tests/performance/` (imports `common.py` from the same directory).

## Quick start (product Chat)

```bash
cd tests/performance
export BENCHMARK_BEARER_TOKEN="<jwt>"
export BENCHMARK_PROJECT_ID="<project-uuid>"
export BENCHMARK_CONVERSATION_ID="<conversation-uuid>"
python retrieval_benchmark.py --backend-base-url http://localhost:9000 --scenario baseline --output-json /tmp/bench.json
```

Without those `BENCHMARK_*` inputs, do not use this command as final product performance evidence.

## PR smoke command

Use this for the short performance gate. It is intentionally backend/proxy health only, so it does not require Ollama, model pulls, classifier, or seeded benchmark datasets. Evidence from this command is **INFRA_ONLY**, not product Chat/LAB performance:

```bash
.github/local/run-performance-ci-like.sh --stop-after
```

The wrapper writes `infra-probe-local.json` under `.github/local/results/performance/` and Gatling HTML under `tests/gatling/build/reports/gatling/`.

`infra_probe.py` fails when measured requests exceed either threshold:

- `--max-error-rate` / `PERF_INFRA_MAX_ERROR_RATE` default: `0`
- `--max-p95-ms` / `PERF_INFRA_MAX_P95_MS` default: `2000`

Example standalone probe:

```bash
python tests/performance/infra_probe.py \
  --backend-base-url http://localhost:9000 \
  --repetitions 5 \
  --warmup 1 \
  --concurrency 1 \
  --max-error-rate 0 \
  --max-p95-ms 2000 \
  --output-json /tmp/infra-probe.json
```

## Final evidence smoke

Use this when collecting final thesis evidence. It records latency, errors, skipped product steps, thresholds, and limitations in one JSON file. It is intentionally a bounded smoke and must not be used to claim production scalability.

```bash
python tests/performance/final_performance_smoke.py \
  --backend-base-url http://127.0.0.1:9000 \
  --output-json .cursor/context/evidence/performance/final-performance-smoke.json
```

This command is acceptable only for an **INFRA_ONLY** claim when product steps are skipped. For product performance evidence, use `--require-product` plus the credentials/IDs below.

Optional product-scoped measurements:

```bash
export PERF_BEARER_TOKEN="<jwt>"              # or PERF_EMAIL / PERF_PASSWORD
export PERF_PROJECT_ID="<project-uuid>"
export PERF_CONVERSATION_ID="<conversation-uuid>"
export PERF_DATASET_ID="<dataset-uuid>"       # only needed with --enable-lab-start

python tests/performance/final_performance_smoke.py \
  --backend-base-url http://127.0.0.1:9000 \
  --enable-lab-start \
  --require-product \
  --output-json .cursor/context/evidence/performance/final-performance-smoke.json
```

Defaults:

- `PERF_MAX_ERROR_RATE=0`
- `PERF_MAX_P95_MS=3000`
- Product steps are skipped unless the required credentials and IDs are supplied.
- Pass `--require-product` to fail if document/Chat/Lab product steps are skipped.

## Product chat scenarios (`transport: product_chat`)

Requires:

- `BENCHMARK_BEARER_TOKEN` — JWT (`Authorization: Bearer`)
- `BENCHMARK_PROJECT_ID` — UUID
- `BENCHMARK_CONVERSATION_ID` — UUID

Optional prelude: scenario `product_chat.rag_config` is sent with `PUT {product}/config/project/{projectId}` before each run (once per process — script runs prelude once at start).

Example:

```bash
export BENCHMARK_BEARER_TOKEN="eyJ..."
export BENCHMARK_PROJECT_ID="..."
export BENCHMARK_CONVERSATION_ID="..."
python retrieval_benchmark.py --scenario retrieval_off --output-json /tmp/bench.json
```

## Historical `/query` scenarios

Historical `GET .../query` scenarios are retained for historical comparison and regression diagnosis only. They are not the final product Chat path and must not be cited as release performance evidence unless the report is explicitly labelled `historical_query`.

## Audit & schema

- JSON field paths: [API_RESPONSE_AUDIT.md](API_RESPONSE_AUDIT.md)
- Report JSON Schema: [schema/benchmark-report-v1.schema.json](schema/benchmark-report-v1.schema.json) — see [schema/README.md](schema/README.md)
- Scenario YAML: [scenarios/](scenarios/) — see [scenarios/README.md](scenarios/README.md)

## Optional cost line

Copy `pricing.example.yaml`, adjust model keys to match `chatModel` / `llmModel`, then:

```bash
python retrieval_benchmark.py --pricing-yaml pricing.example.yaml --scenario baseline
```

## CI

Optional workflow: [.github/workflows/micro-benchmark.yml](../../.github/workflows/micro-benchmark.yml) (`workflow_dispatch` + weekly). **No PR gates** — observation only; upload JSON artifact when configured.
