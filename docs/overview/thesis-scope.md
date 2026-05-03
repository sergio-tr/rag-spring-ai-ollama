# Thesis scope (minimum deliverable and deferred work)

**Purpose:** Single official statement of what the project commits to deliver versus what remains **out of minimum scope** or **deferred**. This page is the canonical reference for supervisors and reviewers.

**Deploy reality (thesis alignment):** Production-style deployment used in this repository is **GitHub Actions** [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) (manual) → **SSH** to a **Linux VM** → `docker compose` with [`docker/docker-compose.yml`](../../docker/docker-compose.yml) + [`docker/compose.prod.yml`](../../docker/compose.prod.yml). Operator steps and env layout: [../operations/runbook-docker-vm.md](../operations/runbook-docker-vm.md); gate and secrets: [../operations/deploy-workflow-audit.md](../operations/deploy-workflow-audit.md). Azure-specific naming without fixed IPs: [../operations/azure-vm-parameterization.md](../operations/azure-vm-parameterization.md).

**Related:** [product-context.md](product-context.md), [ADR 0001 — Lab promotion modes](../adr/0001-lab-promotion-modes.md), [ADR 0003 — evaluation async and project scope](../adr/0003-evaluation-async-project-scope-and-dataset-dedup.md).

---

## In scope (minimum)

- **Platform:** Authenticated **multi-user** product API (see `RAG_API_PRODUCT_BASE_PATH` / `NEXT_PUBLIC_RAG_API_PREFIX` in module READMEs), projects, documents, conversations, streaming chat, layered RAG configuration and presets — as implemented in `rag-service` and `webapp`, consistent with [ADR 0002 — user/project data isolation](../adr/0002-multitenancy-assumption.md).
- **Research Lab:** Use of existing Lab endpoints (evaluations, classifier proxy, async jobs) for demonstration; promotion modes per [ADR 0001](../adr/0001-lab-promotion-modes.md) (no silent writes to production config).
- **Classifier:** Training/evaluation **via BFF** to `classifier-service`; no requirement for a new public REST CRUD for evaluation **datasets** in the minimum scope (see decision below).
- **Observability:** Stack demonstrable with Compose (`compose.obs.yml`); traces/metrics sufficient for thesis defence narrative, not necessarily full SLO automation.
- **Quality:** CI gates (`ci.yml`) and documented manual/local verification per [testing strategy overview](../testing/README.md).
- **Documentation:** Architecture, data model, ADRs, and module READMEs aligned with repository state at freeze.

---

## Explicitly out of minimum scope (unless added by separate decision)

- **Product REST API for evaluation datasets** (`GET/POST {product}/datasets` style): **not** part of the minimum deliverable; evaluation may rely on existing Lab flows or internal loading as described in [ADR 0003](../adr/0003-evaluation-async-project-scope-and-dataset-dedup.md) and the backend OpenAPI export.
- **Strong multi-tenant SaaS** isolation (separate DB per tenant, org-wide RLS): out of scope per ADR 0002.
- **Automatic Lab → production promotion** (modes B/C without UX): out of minimum; explicit actions only.

---

## Deferred / optional

- Admin **limits/quotas** and optional `GET /api/admin/system-config` (not part of the minimum deliverable unless explicitly scoped).
- Full **Azure** production hardening beyond the documented **Linux VM + Compose** runbook ([runbook-docker-vm.md](../operations/runbook-docker-vm.md), [azure-vm-parameterization.md](../operations/azure-vm-parameterization.md)).
- **Hexagonal refactor** of controllers — later engineering work if prioritized.

---

## Operational expectations (informative)

A deployable stack on a VM, **login** and **chat** working, **E2E** coverage from a green **`ci.yml`** (including Playwright `@fullstack` in the same DAG), **observability** demonstrable when the obs overlay is enabled, and **deploy** gated by successful **`CI`** for the same SHA per [deploy.yml](../../.github/workflows/deploy.yml). Details: [release readiness checklist](../operations/release-readiness-checklist.md).

---

## Supplementary material

Thesis chapters or local working copies **do not** override this page for scope disputes. Use ADRs and module READMEs for engineering truth alongside this document.
