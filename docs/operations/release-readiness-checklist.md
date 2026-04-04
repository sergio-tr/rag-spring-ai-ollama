# Release readiness checklist

**Purpose:** Criteria for CI/CD, VM deploy, documentation, and quality alignment before tagging or handing off a release candidate. Treat as a **team checklist**; adjust subsets as needed.

**Related:** [runbook-docker-vm.md](runbook-docker-vm.md), [deploy-workflow-audit.md](deploy-workflow-audit.md), [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md), [../testing/README.md](../testing/README.md) (quality gates before deploy).

---

## Minimum product criteria

| # | Criterion | How to verify |
|---|-----------|----------------|
| P1 | **System deployable** with Docker Compose on a Linux VM (same compose chain as [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)). | [runbook-docker-vm.md](runbook-docker-vm.md) §3–4; containers healthy. |
| P2 | **Login** functional (authenticated session against the product). | Manual or Playwright smoke path; webapp + backend JWT flow. |
| P3 | **Chat** functional (RAG/chat path usable end-to-end with configured LLM/embeddings). | Smoke query or UI chat; backend health includes Ollama/models as configured. |
| P4 | **Observability operational** when the optional stack is enabled (`compose.obs.yml` per [observability README](../../observability/README.md)). | Traces/metrics checklist in [observability/README.md](../../observability/README.md); operator guide: [grafana-observability-guide.md](grafana-observability-guide.md). |
| P5 | **E2E smoke OK** — `ci.yml` Playwright smoke (non-`@fullstack`) green on the release candidate. | Green `ci.yml` run on the SHA; full-stack browser coverage is additionally gated via `e2e-fullstack.yml` for deploy (see below). |

---

## Documentation map (operations)

| Topic | Canonical location |
|------|-------------------|
| Release checklist | This file |
| `deploy.yml` audit — gates, secrets, limitations | [deploy-workflow-audit.md](deploy-workflow-audit.md) |
| Runbook Docker VM + env | [runbook-docker-vm.md](runbook-docker-vm.md); [docker/README.md](../../docker/README.md) § Deployment runbook |
| Azure / cloud parameterization (no fixed IPs) | [azure-vm-parameterization.md](azure-vm-parameterization.md) |
| Workflows vs quality gates | [../testing/README.md](../testing/README.md) § Quality gates before deploy; [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md) |
| Documentation governance | [../development/documentation-guidelines.md](../development/documentation-guidelines.md); hub: [../README.md](../README.md); [documentation-governance-audit.md](documentation-governance-audit.md) |
| Product / thesis alignment | [../overview/thesis-scope.md](../overview/thesis-scope.md), [../overview/product-context.md](../overview/product-context.md) |
| Observability on VM | [observability README](../../observability/README.md) § Production / VM; deploy/ops: this folder |
| Tag + release notes procedure | [release-notes-template.md](release-notes-template.md) |

---

## Quality and CI

| # | Criterion | Evidence |
|---|-----------|----------|
| Q1 | **`ci.yml`** succeeds on the release candidate commit (backend, classifier, webapp including coverage gate where configured). | Green run on `main` / merge SHA. |
| Q2 | **`e2e-fullstack.yml`** succeeds for the same SHA (Playwright `@fullstack` + stack). | Green workflow run linked to SHA. |
| Q3 | **Playwright API smoke** (`npm run test:api`) is documented for staging/ops; optional manual [`system-checks.yml`](../../.github/workflows/system-checks.yml) when validating a running URL. | Doc link or waiver if not exercised. |
| Q4 | **Sonar / `sonar.yml`** (if enabled for the repo) — no new **blocking** quality gate regressions agreed with maintainers. | Sonar dashboard or CI conclusion. |

## Deploy path

| # | Criterion | Evidence |
|---|-----------|----------|
| D1 | **`deploy.yml`** pre-deploy **gate** passes: required workflows (`ci.yml`, `e2e-fullstack.yml`) completed **successfully** for the **same** `head_sha` as the deploy run. | Deploy job log shows “Gate OK” lines. |
| D2 | **Secrets** documented (names and purpose only — no values): `VM_HOST`, `VM_USER`, `VM_SSH_KEY`, `VM_DEPLOY_DIR`, `GHCR_TOKEN`. | [deploy-workflow-audit.md](deploy-workflow-audit.md). |
| D3 | Target VM can **pull images** (`docker login` + `compose pull`) and **apply** `docker-compose.yml` + `compose.prod.yml`. | Runbook section “Verify after deploy”. |

## Operations documentation

| # | Criterion | Evidence |
|---|-----------|----------|
| O1 | **Runbook** for Linux VM + Docker Compose + env files exists and matches the actual deploy commands. | [runbook-docker-vm.md](runbook-docker-vm.md). |
| O2 | **Azure / cloud** notes cover **parameterized** hostnames and networking (no reliance on fixed IPs in documentation). | [azure-vm-parameterization.md](azure-vm-parameterization.md). |
| O3 | **Workflows vs quality gates** table is aligned with testing strategy and deploy gate. | [../testing/README.md](../testing/README.md), [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md). |

## Product / thesis alignment

| # | Criterion | Evidence |
|---|-----------|----------|
| A1 | **Thesis scope** and **product context** describe deploy reality (GitHub Actions → SSH → Compose on VM), not only abstract “Compose locally”. | [../overview/thesis-scope.md](../overview/thesis-scope.md), [../overview/product-context.md](../overview/product-context.md). |

---

## Sign-off (optional)

| Role | Name | Date |
|------|------|------|
| Maintainer | | |
| Supervisor (if required) | | |
