# Documentation inventory and classification

**Purpose:** Classify Markdown under the documentation **allowlist** (`docs/**`, repository root [`README.md`](../../README.md), [`rag-service/README.md`](../../rag-service/README.md)) as **correct and worth keeping**, **outdated**, **misleading or internal**, or **missing**. Use this table when reviewing docs hygiene or onboarding integrators.

**Convention:** “Internal” means material that reads like execution plans, sprint numbering, or tooling-local paths-not the canonical system story for operators or integrators.

---

## Summary table (by area)

| Area | Correct / keep | Outdated | Misleading / internal | Missing |
| ------ | ---------------- | ---------- | ------------------------ | --------- |
| [`docs/README.md`](../README.md), hub | Yes - central index | CI table must stay aligned with workflows over time | - | Optional pointer to this inventory |
| [`docs/development/`](README.md) | Guidelines + strategy | - | Older drafts that cited tooling-only paths | Normative policy lives in [`documentation-guidelines.md`](documentation-guidelines.md) |
| [`docs/adr/`](../adr/) | Index + accepted ADRs | Individual ADRs if implementation drifts | The word “multitenancy” alone can overstate SaaS isolation | Preferred terminology in [ADR 0002](../adr/0002-multitenancy-assumption.md) |
| [`docs/architecture/`](../architecture/) | `DATA_MODEL`, diagram catalogue, `BACKEND_PACKAGES` | `implementation-roadmap` “alignment” bullets age with the repo | Roadmap must not imply unpublished branch policy | Backend logical diagram: [`backend-logical-layers.mmd`](../architecture/backend-logical-layers.mmd) |
| [`docs/operations/`](../operations/) | Runbooks, deploy audit | Gate wording vs current workflows | Outdated cross-reference style (clean up toward Markdown anchors) | - |
| [`docs/overview/`](../overview/) | `minimum-scope`, `product-context` | - | Informal “multi-tenant-style” wording without ADR 0002 nuance | - |
| [`docs/ai/`](../ai/) | Hub, inventory, pipeline contracts | - | - | - |
| [`docs/backend/`](../backend/) | Backend norms index (`README`), refactoring pointer | - | - | Previously missing `README` / refactoring pointer - restored for ADR 0012 links |
| [`docs/enhancement/`](../enhancement/) | README states non-canonical intent | Sprint plans superseded by code | [`planes-ejecucion/`](../enhancement/planes-ejecucion/) historical plans | - |
| Root [`README.md`](../../README.md) | Quick start + pointers | Badge URLs if repo moves | - | - |
| [`rag-service/README.md`](../../rag-service/README.md) | API summary + links | - | - | - |

---

## Detailed notes (representative paths)

### Correct and worth keeping

- [`docs/README.md`](../README.md) - Documentation hub; module map; CI overview.
- [`documentation-guidelines.md`](documentation-guidelines.md) - Layer model (global vs module README) and normative destinations.
- [`docs/adr/README.md`](../adr/README.md) - ADR index and format rules.
- [`docs/architecture/DATA_MODEL.md`](../architecture/DATA_MODEL.md) - Logical and physical reference (Flyway is the source of truth).
- [`docs/architecture/README.md`](../architecture/README.md) - Diagram catalogue and narrative index.
- [`docs/architecture/diagram-export.md`](../architecture/diagram-export.md) - Mermaid export workflow.
- [`docs/testing/README.md`](../testing/README.md) - Testing overview and deploy-gate pointers.
- Repository root [`README.md`](../../README.md) - Entry point and quick start.

### Outdated (re-verify on each release)

- [`docs/architecture/implementation-roadmap.md`](../architecture/implementation-roadmap.md) - The “alignment with the repository” section ages as code moves; refresh or point to ADRs and the package map.
- Workflow names in [`docs/README.md`](../README.md) / [`README.md`](../../README.md) whenever [`.github/workflows/`](../../.github/workflows/) changes.

### Misleading or internal (needs framing; not hub authority alone)

- [`docs/enhancement/planes-ejecucion/`](../enhancement/planes-ejecucion/) - Historical sprint-style plans; **not** authoritative for API or schema. See [`docs/enhancement/README.md`](../enhancement/README.md).
- Long assessment drafts under [`docs/enhancement/`](../enhancement/) - Archival context only.

### Delivered conventions (maintain going forward)

- Single normative policy for README vs [`docs/architecture/`](../architecture/) vs [`docs/adr/`](../adr/) in [`documentation-guidelines.md`](documentation-guidelines.md).
- Stable anchors in [`DATA_MODEL.md`](../architecture/DATA_MODEL.md) (for example `#dm-s6-1`, `#6-active-configuration-resolution`) for cross-links.
- Diagrams: [`backend-logical-layers.mmd`](../architecture/backend-logical-layers.mmd), refined [`rag-request-flow.mmd`](../architecture/rag-request-flow.mmd); catalogue in [`docs/architecture/README.md`](../architecture/README.md).

**Note:** [`docs/enhancement/`](../enhancement/) may retain older section-sign notation in archival plans; it is **not** canonical product documentation ([`docs/enhancement/README.md`](../enhancement/README.md)).

---

## Diagram catalogue - maintenance status

| Item | Status |
| ------ | -------- |
| System context (`context-level.mmd`) | Keep aligned with deployment names |
| Component interactions (`service-runtime-integrations.mmd`) | Current |
| RAG request path (`rag-request-flow.mmd`) | Includes resolve / orchestration / query path sub-steps |
| Backend layers (`backend-logical-layers.mmd`) | Present |
| Infra overlays (`deployment-modes.mmd`, `deployment-compose.mmd`) | Update when Docker / Compose profiles change |

---

## Priority follow-ups

| Priority | Item |
| --- | --- |
| High | When API prefixes or Compose profiles change, update affected `docs/architecture/*.mmd` diagrams. |
| Medium | Re-read [`implementation-roadmap.md`](../architecture/implementation-roadmap.md) “Alignment with the repository” when major roadmap blocks close. |
| Low | Optional glossary if onboarding repeatedly asks the same terms. |

---

## Related

- [Documentation guidelines](documentation-guidelines.md)
- [Documentation governance strategy](documentation-governance-strategy.md)
