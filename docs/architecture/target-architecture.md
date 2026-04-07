# Target platform architecture

**Purpose:** Single canonical description of **target** subsystems, boundaries, and global rules for this monorepo. It governs all later development phases until changed by an ADR. It is **not** a snapshot of the codebase; gaps are listed under [Alignment with the repository (current state)](#alignment-with-the-repository-current-state).

**Related:** [rag-runtime-architecture.md](rag-runtime-architecture.md), [configuration-resolution-model.md](configuration-resolution-model.md), [knowledge-system-model.md](knowledge-system-model.md), [implementation-roadmap.md](implementation-roadmap.md), [integration-flows.md](integration-flows.md), [system-context.md](system-context.md).

## Thesis context (product domain)

The degree project centres on **RAG over neighbourhood meeting minutes** (*actas vecinales*) as the primary illustrative domain. The platform remains **API-first** with a **multi-user web product** and a **Lab** for reproducible empirical work; domain wording in UX or datasets may reflect meeting minutes while the architecture below applies to the whole system.

## Global rules

1. **Single RAG execution engine** — Product and Lab/benchmark share the same runtime semantics (see [ADR 0009](../adr/0009-unified-product-and-lab-execution-engine.md)).
2. **No legacy without real use** — Legacy surfaces or code paths must either prove active use or carry an explicit sunset justification and removal criterion (documented here or in an ADR).
3. **Keycloak and HTTPS** — Identity and transport security foundations are closed decisions ([ADR 0006](../adr/0006-keycloak-identity-and-https-foundation.md)).
4. **Configuration** — Feature flags may exist as inputs, but **governing semantics** are capabilities, compatibility rules, presets, resolved runtime configuration, and workflow selection — not a forest of ad-hoc conditionals ([ADR 0007](../adr/0007-capability-groups-and-compatibility-rules.md)).

## Subsystems (canonical list)

### 1. Identity & Access

**Purpose:** Authenticate and authorize users for the product and Lab APIs.

**Owns (conceptually):** identities, sessions/tokens, roles/scopes, integration with the external IdP.

**Consumes / provides:** JWT validation in Spring; future Keycloak realm and clients per [ADR 0006](../adr/0006-keycloak-identity-and-https-foundation.md).

**Interfaces:** Product REST and webapp use authenticated calls; public routes remain as documented in [integration-flows.md](integration-flows.md).

### 2. Workspace / Product

**Purpose:** Multi-user workspace: projects, workspace documents, conversations, presets, and API-first product flows.

**Owns:** Project and document lifecycle from a **product** perspective, conversation history, user-visible configuration selections.

**Depends on:** Identity & Access, Runtime Configuration, Knowledge System (for corpus), RAG Runtime Engine (for answers).

**Interfaces:** `interfaces.rest` product API (see [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md)); webapp client.

### 3. Runtime Configuration

**Purpose:** Resolve what the RAG runtime must do for a request: capabilities, workflows, prompts composition inputs, model choices (as part of resolved config).

**Owns:** Capability groups, capabilities, compatibility rules, resolved runtime config, snapshots, workflow selection, reindex-impact analysis, system prompt composition ([configuration-resolution-model.md](configuration-resolution-model.md)).

**Depends on:** Governance (ADRs), persistence of presets/user/project layers.

**Rule:** Resolved configuration drives behaviour; raw flags are **inputs** to resolution, not the architecture’s primary vocabulary.

### 4. Knowledge System

**Purpose:** Persist and serve **knowledge artefacts** derived from workspace documents: parsing, metadata, chunks, indices, snapshots, reindex, materialization strategies, structured search.

**Owns:** Conceptual pipeline from `WorkspaceDocument` to retrievable units; alignment with physical schema per [DATA_MODEL.md](DATA_MODEL.md).

**Depends on:** Workspace / Product (document ownership), Platform & Ops (storage, backups).

**Interfaces:** Ingestion services, retrievers, index maintenance.

### 5. RAG Runtime Engine

**Purpose:** Execute one RAG request end-to-end: understanding, retrieval, post-retrieval, tools, optional clarification, memory, adaptive routing, judges.

**Owns:** The **normative vocabulary** of runtime pieces ([rag-runtime-architecture.md](rag-runtime-architecture.md), [ADR 0005](../adr/0005-target-rag-architecture-and-runtime-center.md)).

**Depends on:** Runtime Configuration (resolved config + prompts), Knowledge System (retrieval), optional Classifier System signals, Ollama (LLM/embeddings).

### 6. Experimentation / Lab

**Purpose:** Datasets, runs, metrics, async jobs, classifier proxy — **same engine** as the product for comparable results.

**Owns:** Lab-specific workflows and storage of experiment artefacts (within existing schema/services).

**Depends on:** RAG Runtime Engine, Runtime Configuration, Classifier System as needed.

**Scenario ladder:** S0–S4 are architecturally frozen ([ADR 0010](../adr/0010-rag-scenario-ladder-s0-to-s4.md)); S0–S2 are the **core** benchmark spine; S3–S4 are **first-class** architecturally, with complementary empirical roles.

### 7. Classifier System

**Purpose:** Separately deployed service (FastAPI) for query-type / routing signals into the runtime and Lab.

**Owns:** Model training/deployment lifecycle for classification; HTTP API consumed by Spring.

**Depends on:** Platform & Ops (deployment, networking).

**Interfaces:** See [classifier-service/README.md](../../classifier-service/README.md); [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md) (`infrastructure.classifier`).

### 8. Platform & Ops

**Purpose:** Compose, images, environments, secrets, observability stack, CI/CD, VM/deploy runbooks.

**Owns:** Operational contracts: ports, profiles, health, telemetry export.

**Depends on:** HTTPS and identity foundations for secure deployments ([ADR 0006](../adr/0006-keycloak-identity-and-https-foundation.md)).

**Interfaces:** [docker/README.md](../../docker/README.md), [observability/README.md](../../observability/README.md), [operations/README.md](../operations/README.md).

### 9. Governance & Compliance

**Purpose:** ADRs, documentation governance, branch/PR policy, traceability for thesis defence, RGPD/compliance **as a roadmap block** (implementation follows [implementation-roadmap.md](implementation-roadmap.md)).

**Owns:** Decision records, canonical docs under `docs/`, rules for `docs/` vs module READMEs ([documentation-guidelines.md](../development/documentation-guidelines.md)).

**Interfaces:** [adr/README.md](../adr/README.md).

## Conceptual data flow (high level)

Users and agents interact through Workspace / Product or Lab. Each **RAG request** obtains **Identity** context, **Runtime Configuration** resolution, reads from the **Knowledge System**, and executes in the **RAG Runtime Engine**. **Classifier** may inform understanding. **Platform & Ops** carries traffic, storage, and telemetry. **Governance** constrains how all of the above may evolve.

## Alignment with the repository (current state)

### What already exists

- Spring Boot **product** and **legacy** HTTP surfaces, JWT auth, projects/documents/conversations, presets, Lab endpoints, classifier HTTP clients — see [integration-flows.md](integration-flows.md) and [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md).
- Ingestion and retrieval services, tool routing, retrievers, rankers (e.g. faithfulness), reasoning strategies — partial mapping to target runtime vocabulary.
- Compose-based deployment and observability overlays; **JWT** auth (not yet Keycloak as IdP in the described stack).
- ADRs 0001–0004 and ER in [DATA_MODEL.md](DATA_MODEL.md).

### What is partial

- **Capability-group / compatibility-rule / resolved-snapshot** model is not fully explicit in code or docs as named here; configuration is layered (system → user → project) with flags and presets.
- **Keycloak** and **HTTPS** as described in ADR 0006 are **decisions**; full integration may not be complete.
- **Lab vs product** share code paths but full **unified execution trace** and scenario ladder **S0–S4** labelling may not be uniform everywhere.
- **Knowledge** pipeline exists (ingestion, chunks, vectors) but **index snapshots**, **reindex events**, and **materialization strategy** taxonomy may not be uniformly named or implemented.

### What is still missing

- Full alignment of Java/TS modules with every **named runtime component** in [rag-runtime-architecture.md](rag-runtime-architecture.md) (or explicit deprecation of gaps).
- **`SystemPromptComposer`** and four-layer prompt stack as the **only** documented composition path for effective system prompts.
- **`ReindexImpactAnalyzer`** (or equivalent) as a first-class concept tied to config changes.
- Removal of **unused legacy** per policy.
- **Compliance / RGPD** block execution beyond high-level Governance placeholder.
