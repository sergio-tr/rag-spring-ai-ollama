# Performance and load testing (overview)

This document states **goals and entry points**. Tool-specific commands, environment matrices, and baselines live next to the tools.

## Goals

- Catch latency and error-rate regressions on critical product HTTP paths.
- Keep load tooling portable on **Linux** CI and developer machines (WSL2 acceptable).
- Exercise **realistic** blends of traffic (RAG + auth + admin) without duplicating the same scenario under multiple names without reason.

## Tools (canonical locations)

| Tool | Purpose | Documentation |
| --- | --- | --- |
| **Gatling** | JVM scenarios, HTML reports, smoke / load / stress, **mixed realistic** workloads | [../../tests/gatling/README.md](../../tests/gatling/README.md) |
| **Python micro-benchmarks** | Low-concurrency product Chat latency + **estimated** tokens (schema v1 JSON); **not** load | [../../tests/performance/README.md](../../tests/performance/README.md) |
| **Python infra probe** | Simple GET KPIs (e.g. `/actuator/health`) — cold/warm infra | `tests/performance/infra_probe.py` |

Gatling coverage and related pointers: [../testing/traceability-legacy-tools.md](../testing/traceability-legacy-tools.md).

### Gatling vs Python helpers

| Concern | Prefer |
| --- | --- |
| RPS ramps, HTML reports, mixed traffic, authenticated flows | **Gatling** — `gatling.yml` when `GATLING_BASE_URL` is set |
| Single-host product Chat latency samples, thesis baselines, **estimated** tokens | **Python** — `micro-benchmark.yml` (optional; no PR gates) |

The Gatling workflow **skips** when `GATLING_BASE_URL` is unset.

### Python micro-benchmarks (analysis only)

- **Scripts:** `tests/performance/retrieval_benchmark.py` (`product_chat` with JWT + project + conversation; optional historical `GET …/query` baseline when explicitly labelled in scenario YAML), `llm_benchmark.py` (wrapper, `--family llm` default), `infra_probe.py` (non-RAG GET probe). See `tests/performance/` for any additional helper scripts.
- **Report:** JSON `schemaVersion: "1.0"` — see `tests/performance/schema/benchmark-report-v1.schema.json` and `tests/performance/API_RESPONSE_AUDIT.md` (no tokenizer fields in API responses; benchmarks use a **chars/4** heuristic, `estimated: true`).
- **Scenarios:** `tests/performance/scenarios/*.yaml` — final evidence should use `product_chat` (PUT project RAG config + chat job polling) when `BENCHMARK_BEARER_TOKEN`, `BENCHMARK_PROJECT_ID`, `BENCHMARK_CONVERSATION_ID` are set. `transport: historical_query` (YAML key may still read `legacy`) is for explicitly labelled pre-product `/query` comparisons only.
- **CI:** [.github/workflows/micro-benchmark.yml](../../.github/workflows/micro-benchmark.yml) — `workflow_dispatch` + weekly schedule; requires repository variable `BENCHMARK_BASE_URL` (or dispatch input). **No gates** — artifacts for observation only. Does **not** run on every commit.

## Evidence Labels

- **INFRA_ONLY:** actuator/proxy checks such as `infra_probe.py`, Gatling actuator smoke, or `final_performance_smoke.py` when document/Chat/LAB product steps are skipped.
- **PRODUCT_FLOW:** `final_performance_smoke.py --require-product` or `retrieval_benchmark.py` with `transport: product_chat` and all required product credentials/IDs, where Chat/LAB/document steps actually execute.

Do not cite an `INFRA_ONLY` run as product Chat/LAB performance evidence.

### Gatling: profiles vs LLM cost

| Profile | Ollama / LLM | Typical execution |
| --- | --- | --- |
| **smoke**, **load** | Real Ollama allowed — use a **small model** and **conservative** backend settings in CI. | CI / dispatch with low VUs when `GATLING_BASE_URL` is configured. |
| **stress**, **spike** | Real Ollama — expect saturation; assertions are **more lenient** on failures/latency. | **Manual** (or dedicated perf env). |
| **soak** | High cumulative cost | **Manual only**, long runner timeout, not default in GitHub Actions `timeout-minutes`. |

**Chat load:** Gatling `ChatSseSimulation` accepts **200 or 202** on `POST …/messages` (streaming vs async job). Mixed simulations use product auth/admin routes; use Chat-specific simulations for product Chat evidence.

## CI strategy

- **PR smoke:** `reusable-ci-core.yml` runs the `performance` job only for PRs targeting `main` / `master`. It starts Postgres + Spring `e2e`, routes through the reverse proxy, runs Gatling `ActuatorHealthSimulation`, then runs `infra_probe.py` with strict error-rate and p95 thresholds. This lane does **not** require Ollama or classifier readiness.
- **Local PR smoke:** `.github/local/run-performance-ci-like.sh --stop-after` mirrors the PR performance job and writes local probe JSON under `.github/local/results/performance/`.
- **Manual / scheduled Gatling:** `.github/workflows/gatling.yml` (dispatch / schedule; requires `GATLING_BASE_URL` or manual input). Prefer **`MixedRealisticSmokeSimulation`** or **`ActuatorHealthSimulation`** for cheap scheduled runs; use **mixed_profile** / repository variables for `GATLING_PROFILE` when running `MixedRealisticSimulation`.
- **Manual / scheduled Python micro-benchmark:** `.github/workflows/micro-benchmark.yml` (dispatch / weekly; optional, observation artifact only).

## Caution

Load against **real LLM** endpoints is environment-dependent. For **comparable** numbers across runs, pin model, max tokens, and concurrency on Ollama. **Stub / e2e** profiles are documented in the backend README for functional tests — Gatling mixed scenarios may still hit historical query simulations when `GATLING_MIX_RAG_PCT` &gt; 0; product evidence uses Chat/job routes.

## Related

- Retired load-tool mapping (k6 → Gatling): [../testing/traceability-legacy-tools.md](../testing/traceability-legacy-tools.md)
- Documentation hub: [../README.md](../README.md)
- Root README (quick links): [../../README.md](../../README.md)
- Gatling mix configuration (percentages, users CSV): [../../tests/gatling/README.md](../../tests/gatling/README.md)
