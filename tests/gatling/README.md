# Gatling load and stress tests

Gradle module (Scala 2.13 + Gatling 3.13) aligned with Spring path properties (`rag.api.legacy-base-path`, `rag.api.product-base-path`) via environment variables.

**Platform:** Run from **Linux** (local or CI) with `./gradlew`. **`gradlew.bat`** is only for occasional Windows desktops and is outside the supported Linux **CI/production** workflow; prefer **WSL2** + `./gradlew` for parity with [.github/workflows/gatling.yml](../../.github/workflows/gatling.yml).

## Design decisions (realistic load)

Answers below are **canonical for this repo** (do not duplicate long rationale in code comments).

| Topic | Decision |
| --- | --- |
| **LLM target** | **Real Ollama** for **load / stress / spike** profiles. **CI / smoke:** controlled settings (small model, low top-k) as set on the backend. **Soak:** optional, **manual only** (cost). |
| **Users** | **Feeder CSV** [`src/gatling/resources/users.csv`](src/gatling/resources/users.csv) with **multiple rows** — default uses repeated seed user; **replace with distinct accounts** for realistic isolation. |
| **API mix** | Default **70%** legacy RAG (`GET {legacy}/query`), **20%** auth (`POST /api/auth/login`), **10%** admin (`GET /api/admin/*` when `GATLING_ADMIN_EMAIL` is set, else `GET` product `/projects`). Override with `GATLING_MIX_*_PCT`. |
| **Queries** | [`questions.csv`](src/gatling/resources/questions.csv) includes **simple** and **complex** rows (`complexity` column) for heavier prompts. |
| **Phases** | **Stage 1:** one configurable simulation family: `MixedRealistic*` + `GATLING_PROFILE`. **Stage 2:** profile-specific classes (`MixedRealisticSmokeSimulation`, …) for workflow dropdowns. |
| **Execution** | **smoke + load** suitable for **CI** (with conservative env). **stress + spike + soak** → **manual** (or dedicated runners / long timeout). |

## Prerequisites

- JDK **21+** on `PATH`
- A running RAG Spring Boot instance (for real runs). For **mixed** simulations, **legacy** `/query` and **Ollama** must be acceptable targets — see [docs/performance/README.md](../../docs/performance/README.md).

## Commands

From this directory:

```bash
chmod +x gradlew

./gradlew compileGatlingScala

# Pick one simulation (class name includes package):
./gradlew gatlingRun --simulation simulations.ActuatorHealthSimulation
./gradlew gatlingRun --simulation simulations.LegacyQueryLoadSimulation
./gradlew gatlingRun --simulation simulations.ProductAuthenticatedSimulation
./gradlew gatlingRun --simulation simulations.StressRampSimulation
./gradlew gatlingRun --simulation simulations.ActuatorThroughputTiersSimulation
./gradlew gatlingRun --simulation simulations.LegacyQuerySpikeSimulation
./gradlew gatlingRun --simulation simulations.ChatSseSimulation
./gradlew gatlingRun --simulation simulations.OpenApiAndReadinessSimulation
./gradlew gatlingRun --simulation simulations.ProductUnauthenticatedSimulation
./gradlew gatlingRun --simulation simulations.AuthLoginNegativeSimulation
./gradlew gatlingRun --simulation simulations.AdminApiSimulation

# Realistic mixed workload (see "Mixed simulations" below):
./gradlew gatlingRun --simulation simulations.MixedRealisticLoadSimulation
./gradlew gatlingRun --simulation simulations.MixedRealisticSmokeSimulation

# Run all simulations sequentially (alphabetic order)
./gradlew gatlingRun --all
```

HTML reports are written under `build/reports/gatling/<runId>/index.html`.

## Shared scenario blocks

[`ScenarioBlocks.scala`](src/gatling/scala/simulations/ScenarioBlocks.scala) holds reusable **ChainBuilder** fragments (legacy query, login, admin vs product fallback) consumed by mixed simulations.

## Mixed simulations (`MixedRealistic*`)

| Class | Profile | Typical use |
| --- | --- | --- |
| `MixedRealisticSimulation` | `GATLING_PROFILE` env (default `load`) | Single entry with env-driven profile |
| `MixedRealisticSmokeSimulation` | `smoke` | Short, few VUs — **CI / post-deploy** |
| `MixedRealisticLoadSimulation` | `load` | Sustained nominal mix |
| `MixedRealisticStressSimulation` | `stress` | High ramp + hold (lenient assertions) |
| `MixedRealisticSpikeSimulation` | `spike` | Burst + tail ramp |
| `MixedRealisticSoakSimulation` | `soak` | Long duration — **manual**, increase runner timeout |

### Mix and SLA env (selected)

| Variable | Default | Purpose |
| --- | --- | --- |
| `GATLING_MIX_RAG_PCT` | `70` | Weight for legacy `/query` branch |
| `GATLING_MIX_AUTH_PCT` | `20` | Weight for `POST /api/auth/login` |
| `GATLING_MIX_ADMIN_PCT` | `10` | Weight for admin (or product fallback) |
| `GATLING_PROFILE` | `load` | Used only by `MixedRealisticSimulation` |
| `GATLING_MIX_DURATION_SEC` | `120` | Loop duration per user (`load`) |
| `GATLING_MIX_VUS` | `20` | Virtual users (`load`) |
| `GATLING_MIX_RAMP_SEC` / `GATLING_MIX_HOLD_SEC` | `30` / `90` | Injection (`load`) |
| `GATLING_MIX_SMOKE_VUS` | `4` | Smoke VUs |
| `GATLING_MIX_SMOKE_DURATION_SEC` | `90` | Smoke scenario window |
| `GATLING_MIX_MAX_FAIL_PCT` | `5` | Assertion (non-stress profiles) |
| `GATLING_MIX_P99_MS` | same as `GATLING_P99_MS` | Global p99 cap for mixed load |
| `GATLING_MIX_SOAK_DURATION_MIN` | `180` | Soak duration (minutes) |
| `GATLING_MIX_SOAK_RPS` | `2` | Soak constant arrival rate |

Stress/spike reuse `GATLING_STRESS_*` / `GATLING_SPIKE_*` where noted in [`MixedRealisticSimulation.scala`](src/gatling/scala/simulations/MixedRealisticSimulation.scala).

## Environment variables (global)

| Variable | Default | Purpose |
| --- | --- | --- |
| `GATLING_BASE_URL` | `http://localhost:9000` | Spring base URL (no trailing slash). |
| `GATLING_LEGACY_PREFIX` | *(match `rag.api.legacy-base-path`)* | Legacy API prefix (`RAG_API_LEGACY_BASE_PATH`). |
| `GATLING_PRODUCT_PREFIX` | *(match `rag.api.product-base-path`)* | Product API prefix (`RAG_API_PRODUCT_BASE_PATH`). |
| `GATLING_LOGIN_EMAIL` | `dev@local.test` | Product auth simulation. |
| `GATLING_LOGIN_PASSWORD` | `dev` | Product auth simulation. |
| `GATLING_MAX_FAIL_PCT` | `5` | Assertion: max failed request percentage. |
| `GATLING_P99_MS` | `15000` | Assertion: global p99 latency (ms). |
| `GATLING_HEALTH_USERS` | `5` | `ActuatorHealthSimulation` ramp users. |
| `GATLING_HEALTH_DURATION_SEC` | `15` | Ramp duration window (seconds). |
| `GATLING_LEGACY_RPS` | `0` | If &gt; 0, inject `constantUsersPerSec` for legacy query. |
| `GATLING_LEGACY_VUS` | `10` | Legacy steady users when RPS mode off. |
| `GATLING_LEGACY_DURATION_SEC` | `60` | Legacy scenario duration. |
| `GATLING_PRODUCT_VUS` | `8` | Authenticated product scenario users. |
| `GATLING_PRODUCT_ITERATION_SEC` | `30` | Loop duration after login. |
| `GATLING_STRESS_TARGET` | `health` | `health` or `legacy` for `StressRampSimulation`. |
| `GATLING_STRESS_PEAK_USERS` | `80` | Stress ramp peak users. |
| `GATLING_STRESS_RAMP_SEC` | `120` | Stress ramp duration. |
| `GATLING_STRESS_HOLD_SEC` | `60` | Hold phase duration. |
| `GATLING_STRESS_MAX_FAIL_PCT` | `25` | Stress assertion (more lenient). |
| `GATLING_STRESS_P99_MS` | `30000` | Stress p99 cap (ms). |
| `GATLING_PROBE_USERS` | `6` | `OpenApiAndReadinessSimulation` users. |
| `GATLING_UNAUTH_VUS` | `4` | `ProductUnauthenticatedSimulation` ramp users. |
| `GATLING_UNAUTH_ITERATION_SEC` | `20` | Loop duration per scenario. |
| `GATLING_AUTH_NEG_VUS` | `3` | `AuthLoginNegativeSimulation` users. |
| `GATLING_AUTH_NEG_ITERATION_SEC` | `15` | Loop duration for negative auth scenario. |
| `GATLING_ADMIN_EMAIL` | *(empty)* | When set (e.g. `admin@e2e.local`), mixed **admin** branch hits `/api/admin/health`. |
| `GATLING_ADMIN_PASSWORD` | `e2e` | Password for `GATLING_ADMIN_EMAIL`. |
| `GATLING_ADMIN_API_VUS` | `3` | Users for `AdminApiSimulation`. |

## Feeders

- `src/gatling/resources/questions.csv` — `question` (+ optional `complexity`) for legacy query and mixed RAG branch.
- `src/gatling/resources/users.csv` — `email`, `password` for multi-row login feeders (**replace rows** with real distinct users when load-testing tenancy).

## Simulations (summary)

| Class | Purpose |
| --- | --- |
| `ActuatorHealthSimulation` | Short actuator check / warmup. |
| `LegacyQueryLoadSimulation` | `GET {legacy}/query` with feeder; optional RPS-style injection. |
| `ProductAuthenticatedSimulation` | `POST /api/auth/login` then `GET` projects and config schema. |
| `StressRampSimulation` | Ramp on actuator (default) or legacy query; breakpoint-style runs. |
| `ActuatorThroughputTiersSimulation` | Several constant-RPS plateaus on actuator in one run. |
| `LegacyQuerySpikeSimulation` | Sudden user burst on legacy query + tail ramp. |
| `ChatSseSimulation` | Login + create project/conversation + **POST** message; asserts **200 or 202**. |
| `OpenApiAndReadinessSimulation` | `/v3/api-docs`, readiness, liveness (low cost). |
| `ProductUnauthenticatedSimulation` | `GET` presets + `/config/schema` without JWT (**401 or 403**). |
| `AuthLoginNegativeSimulation` | Wrong login, invalid email login, invalid refresh (**401/400**). |
| `AdminApiSimulation` | `/api/admin` unauthenticated + USER **403**; optional ADMIN **200** if `GATLING_ADMIN_EMAIL` set. |
| `MixedRealisticSimulation` | Weighted **RAG + auth + admin** mix; profile from `GATLING_PROFILE`. |
| `MixedRealisticSmokeSimulation` | **Smoke** profile — few VUs, short duration. |
| `MixedRealisticLoadSimulation` | **Load** profile — ramp + hold. |
| `MixedRealisticStressSimulation` | **Stress** profile — higher ramp / lenient SLAs. |
| `MixedRealisticSpikeSimulation` | **Spike** injection pattern. |
| `MixedRealisticSoakSimulation` | **Soak** — long `maxDuration`; **manual** only in practice. |

## Optional token prep

For JWT-from-CSV feeders, generate tokens with [`gatling-prepare-tokens.sh`](gatling-prepare-tokens.sh) (from repo root: `./tests/gatling/gatling-prepare-tokens.sh`) against `/api/auth/login` and add a `token` column to a CSV; keep secrets out of git. See [docs/performance/README.md](../../docs/performance/README.md).

## Sequential scenario matrix (local)

Conservative multi-simulation run from the repo root:

```bash
./tests/gatling/run-gatling-scenario-matrix.sh
```

Same as target `gatling-matrix` in the root `Makefile`.

## CI

[.github/workflows/gatling.yml](../../.github/workflows/gatling.yml) runs on `workflow_dispatch` and weekly schedule when `GATLING_BASE_URL` is set (repository variable or manual input). Choose a **mixed** simulation + optional `mixed_profile` input; repository variable `GATLING_PROFILE` applies to scheduled runs when using `MixedRealisticSimulation`.
