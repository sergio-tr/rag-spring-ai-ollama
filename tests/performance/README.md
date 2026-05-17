# Python micro-benchmarks (RAG)

**Purpose:** small-sample latency and **estimated** token metrics — **not** load testing (use **Gatling** for RPS / stress).

## Scripts

| Script | Role |
| --- | --- |
| `retrieval_benchmark.py` | Main runner: legacy `GET …/query` or `product_chat` (JWT + project + conversation). Schema `benchmark-report-v1` (see `schema/`). |
| `llm_benchmark.py` | Wrapper with `--family llm` default (same HTTP behaviour; report emphasizes token/cost lines). |
| `infra_probe.py` | Simple GET probe (default `/actuator/health`) — infra cold/warm, not RAG. |
| `performance_baseline.py` | **Deprecated** — forwards to `retrieval_benchmark.py`. |
| `actuator_latency_baseline.py` | **Deprecated** — forwards to `infra_probe.py`. |

## Dependencies

```bash
pip install -r tests/performance/requirements.txt
```

Run from repo root or `tests/performance/` (imports `common.py` from the same directory).

## Quick start (legacy)

```bash
cd tests/performance
python retrieval_benchmark.py --backend-base-url http://localhost:9000 --scenario baseline --output-json /tmp/bench.json
```

## PR smoke command

Use this for the short performance gate. It is intentionally backend/proxy health only, so it does not require Ollama, model pulls, classifier, or seeded benchmark datasets:

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
