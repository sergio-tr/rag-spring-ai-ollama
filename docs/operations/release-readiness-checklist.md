# Release readiness checklist

**Purpose:** Criteria for CI/CD, VM deploy, documentation, and quality alignment before tagging or handing off a release candidate. Treat as a **team checklist**; adjust subsets as needed.

**Related:** [runbook-docker-vm.md](runbook-docker-vm.md), [deploy-workflow-audit.md](deploy-workflow-audit.md), [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md), [../testing/README.md](../testing/README.md) (quality gates before deploy).

---

## Minimum product criteria

| # | Criterion | How to verify |
| --- | ----------- | ---------------- |
| P1 | **System deployable** with Docker Compose on a Linux VM (same compose chain as [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)). | [runbook-docker-vm.md](runbook-docker-vm.md) sections [3](runbook-docker-vm.md#3-compose-command-matches-deploy-workflow)–[4](runbook-docker-vm.md#4-verify-after-deploy); containers healthy. |
| P2 | **Login** functional (authenticated session against the product). | Manual or Playwright smoke path; webapp + backend JWT flow. |
| P3 | **Chat** functional (RAG/chat path usable end-to-end with configured LLM/embeddings). | Smoke query or UI chat; backend health includes Ollama/models as configured. |
| P4 | **Observability operational** when the optional stack is enabled (`compose.obs.yml` per [observability README](../../observability/README.md)). | Traces/metrics checklist in [observability/README.md](../../observability/README.md); operator guide: [grafana-observability-guide.md](grafana-observability-guide.md). |
| P5 | **E2E smoke OK** - `ci.yml` Playwright paths green on the release candidate (smoke + `@fullstack` in the same workflow). | Green `ci.yml` run on the SHA. |

---

## Documentation map (operations)

| Topic | Canonical location |
| ------ | ------------------- |
| Release checklist | This file |
| `deploy.yml` audit - gates, secrets, limitations | [deploy-workflow-audit.md](deploy-workflow-audit.md) |
| Runbook Docker VM + env | [runbook-docker-vm.md](runbook-docker-vm.md); [docker/README.md](../../docker/README.md) - [Deployment runbook](../../docker/README.md#deployment-runbook) |
| Azure / cloud parameterization (no fixed IPs) | [azure-vm-parameterization.md](azure-vm-parameterization.md) |
| Workflows vs quality gates | [../testing/README.md](../testing/README.md#quality-gates-before-deploy-vm); [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md) |
| Documentation governance | [../development/documentation-guidelines.md](../development/documentation-guidelines.md); hub: [../README.md](../README.md); [documentation-governance-audit.md](documentation-governance-audit.md) |
| Product alignment | [../overview/minimum-scope.md](../overview/minimum-scope.md), [../overview/product-context.md](../overview/product-context.md) |
| Observability on VM | [observability README](../../observability/README.md#production--vm-where-to-read-what); deploy/ops: this folder |
| Tag + release notes procedure | [release-notes-template.md](release-notes-template.md) |

---

## Quality and CI

| # | Criterion | Evidence |
| --- | ----------- | ---------- |
| Q1 | **`ci.yml`** succeeds on the release candidate commit (backend, classifier, webapp including coverage gate where configured). | Green run on `main` / merge SHA. |
| Q2 | **Playwright `@fullstack`** is part of a green **`ci.yml`** on the release SHA (see `e2e_fullstack` in [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml)). Optional: separate [`e2e-fullstack.yml`](../../.github/workflows/e2e-fullstack.yml) on `main` when paths change - not a `deploy.yml` gate. | `ci.yml` green; optional second workflow if used. |
| Q3 | **Playwright API smoke** (`npm run test:api`) is documented for staging/ops; optional manual [`system-checks.yml`](../../.github/workflows/system-checks.yml) when validating a running URL. | Doc link or waiver if not exercised. |
| Q4 | **Sonar / `sonar.yml`** (if enabled for the repo) - no new **blocking** quality gate regressions agreed with maintainers. Runs on `main`/`master` PRs and pushes inside `ci.yml`; ad-hoc via `workflow_dispatch`. | Sonar dashboard or CI conclusion. |
| Q5 | **Production email** uses real SMTP - Mailpit is only for local/prod-local mail capture (`compose.dev-mail.yml`, `compose.prod-mail.yml`), not university VM production. | [runbook-docker-vm.md](runbook-docker-vm.md); `compose.prod-server.yml` has no Mailpit. |

## Deploy path

| # | Criterion | Evidence |
| --- | ----------- | ---------- |
| D1 | **`deploy.yml`** pre-deploy **gate** passes: **[`ci.yml`](../../.github/workflows/ci.yml)** completed **successfully** for the **same** `head_sha` as the deploy run. | Deploy job log shows “Gate OK” for `CI`. |
| D2 | **Deploy configuration** documented (names only - no values): `DEPLOY_DIR`, `DEPLOY_HEALTH_URL`; server `.env` files per [`.env.example`](../../.env.example). | [deploy-workflow-audit.md](deploy-workflow-audit.md). |
| D3 | Target VM can **pull images** (`docker login` + `compose pull`) and **apply** `docker-compose.yml` + `compose.prod.yml`. | Runbook section “Verify after deploy”. |

## Operations documentation

| # | Criterion | Evidence |
| --- | ----------- | ---------- |
| O1 | **Runbook** for Linux VM + Docker Compose + env files exists and matches the actual deploy commands. | [runbook-docker-vm.md](runbook-docker-vm.md). |
| O2 | **Azure / cloud** notes cover **parameterized** hostnames and networking (no reliance on fixed IPs in documentation). | [azure-vm-parameterization.md](azure-vm-parameterization.md). |
| O3 | **Workflows vs quality gates** table is aligned with testing strategy and deploy gate. | [../testing/README.md](../testing/README.md), [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md). |

## Product alignment

| # | Criterion | Evidence |
| --- | ----------- | ---------- |
| A1 | **Minimum scope** and **product context** describe deploy reality (GitHub Actions → SSH → Compose on VM), not only abstract “Compose locally”. | [../overview/minimum-scope.md](../overview/minimum-scope.md), [../overview/product-context.md](../overview/product-context.md). |

---

## Sign-off (optional)

| Role | Name | Date |
| ------ | ------ | ------ |
| Maintainer | | |
| Supervisor (if required) | | |
