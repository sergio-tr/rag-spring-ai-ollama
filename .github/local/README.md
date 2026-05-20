# Local CI parity (`.github/local`)

Scripts mirror the **CI PR pipeline** ([`reusable-ci-core.yml`](../workflows/reusable-ci-core.yml) via [`ci.yml`](../workflows/ci.yml)): pinned Postgres, same Maven/Playwright/pytest commands as CI where applicable.

**Canonical Postgres image** (must match Compose and workflows): see [`lib/common.sh`](lib/common.sh) (`RAG_PLATFORM_POSTGRES_IMAGE`).

---

## Scripts

| Script | Purpose |
| --- | --- |
| [`lib/common.sh`](lib/common.sh) | Exports pinned image and defaults; sourced by all `run-*.sh`. Optional [`.env.local`](.env.local) for non-image overrides only. |
| [`run-ci-core.sh`](run-ci-core.sh) | Backend `mvn verify` + javadoc with Docker Postgres (core backend job). |
| [`run-integration.sh`](run-integration.sh) | Spring `e2e` + pytest stack integration (needs existing Postgres container + `psql` on host). |
| [`run-e2e-fullstack.sh`](run-e2e-fullstack.sh) | Spring `e2e` + Playwright `@fullstack` fast-fail mode (needs Postgres + Node). |
| [`run-sonar.sh`](run-sonar.sh) | Delegates to `ci-like-sonar.sh` (SonarCloud + coverage). |
| [`run-pre-push-fast.sh`](run-pre-push-fast.sh) | Fast local pre-push lane: lint, typecheck, unit tests, backend tests, classifier tests, and Playwright smoke. |
| [`run-pre-pr-full.sh`](run-pre-pr-full.sh) | Full local pre-PR lane: backend verify, classifier pytest, webapp lint/typecheck/coverage/build, strict integration, and CI-like fullstack E2E. |
| [`run-release-validation.sh`](run-release-validation.sh) | Release wrapper around the final closure lane (`run-closure-ci-local.sh`). |
| [`run-pr-dev.sh`](run-pr-dev.sh) | Runs core → integration → e2e → sonar (if `SONAR_TOKEN` set). |
| [`run-pr-main.sh`](run-pr-main.sh) | Runs `run-pr-dev.sh` then local Gatling smoke + `infra_probe.py` (main/master PR parity). |
| [`run-closure-ci-local.sh`](run-closure-ci-local.sh) | Final fail-fast closure lane with per-step evidence logs and skip/duration summary. |
| [`run-performance-ci-like.sh`](run-performance-ci-like.sh) | Local performance PR smoke: Docker Postgres + Spring `e2e` + Gatling actuator smoke + Python infra probe thresholds. |
| `ci-like-verify.sh` | **Shim** → `run-ci-core.sh` (deprecated name). |
| `ci-like-sonar.sh` | Full Sonar local pipeline (called by `run-sonar.sh`). |
| [`ci-postgres-extensions.sql`](ci-postgres-extensions.sql) | Extensions applied by CI and local scripts. |

---

## Quick start

```bash
# Fast pre-push check
.github/local/run-pre-push-fast.sh

# Full pre-PR check
.github/local/run-pre-pr-full.sh

# Release validation
.github/local/run-release-validation.sh

# Dev-equivalent gate (Sonar skipped if SONAR_TOKEN unset)
.github/local/run-pr-dev.sh

# With Sonar
export SONAR_TOKEN=your_token
.github/local/run-pr-dev.sh

# Main/master parity (+ performance)
.github/local/run-pr-main.sh

# Performance PR smoke only
.github/local/run-performance-ci-like.sh --stop-after

# Final closure lane with evidence logs
.github/local/run-closure-ci-local.sh
```

## Closure mode (strict integration + fullstack E2E)

When collecting closure-grade evidence (annexes, release readiness), run:

```bash
# Strict stack integration (no false-green: fails if everything was skipped)
# Default: recreates Postgres for clean Flyway; use --reuse-postgres only if you know the DB matches current migrations.
INTEGRATION_REQUIRE_CLASSIFIER=1 .github/local/run-integration-ci-like.sh

# Fullstack E2E (proxy-mode parity, fast-fail)
.github/local/run-e2e-fullstack-ci-like.sh

# Performance smoke (Gatling + infra probe JSON under .github/local/results/performance/)
.github/local/run-performance-ci-like.sh --stop-after
```

### Manual Postgres (optional)

```bash
docker run -d -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=vectordb \
  pgvector/pgvector:0.8.2-pg16-bookworm
psql -U postgres -d vectordb -f .github/local/ci-postgres-extensions.sql
```

---

## Related docs

* [`docs/development/sonar-local-analysis.md`](../../docs/development/sonar-local-analysis.md)
* [`docs/operations/local-ci-parity.md`](../../docs/operations/local-ci-parity.md)
* [`.github/workflows/ci.yml`](../workflows/ci.yml)
* [`.github/workflows/reusable-ci-core.yml`](../workflows/reusable-ci-core.yml)
* [`docs/testing/baseline-runbook.md`](../../docs/testing/baseline-runbook.md)
