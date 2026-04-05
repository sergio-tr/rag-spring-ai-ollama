# Documentation governance strategy

**Status:** Aligned with [documentation-guidelines.md](documentation-guidelines.md). Official documentation lives under `docs/` and module READMEs; cross-cutting testing and performance overviews are [../testing/README.md](../testing/README.md) and [../performance/README.md](../performance/README.md).

---

## 1. Audit (current state)

### 1.1 Official `docs/` (canonical hub)

| Area | Role |
|------|------|
| [`docs/README.md`](../README.md) | Hub: tables linking to topics and module READMEs |
| [`docs/architecture/`](../architecture/) | System shape, DATA_MODEL, integration, deployment concepts |
| [`docs/domain/`](../domain/) | Conceptual domain |
| [`docs/operations/`](../operations/) | Release/deploy concepts |
| [`docs/overview/`](../overview/) | Product context |
| [`docs/adr/`](../adr/) | Architecture Decision Records |
| [`docs/development/`](../development/) | Contributor rules, **documentation-guidelines** |
| [`docs/testing/`](../testing/), [`docs/performance/`](../performance/) | Cross-cutting **overview** with pointers to module READMEs |

**Strengths:** Clear hub; [documentation-guidelines.md](documentation-guidelines.md) defines global vs module layers and DO/DO NOT.

### 1.2 Module READMEs

Root [`README.md`](../../README.md), `rag-service/`, `webapp/`, `docker/`, etc. remain the right place for **commands, env, ports** per [documentation-guidelines.md](documentation-guidelines.md).

**Gap:** Occasional long narrative in README vs `docs/` — acceptable if “how” stays in README and “why / system role” stays in `docs/`.

---

## 2. Target structure for `/docs` (official only)

```
docs/
  README.md                 # Hub only: tables + links (no long procedures)
  architecture/
  domain/
  operations/
  overview/
  adr/
  development/              # Contributor + governance (guidelines, this strategy)
  testing/                  # README + topic pages (e.g. legacy tool traceability)
  performance/              # README — load / micro-benchmark overview
  coverage/
```

**Rule:** No long-form **internal assessment** write-ups under official `docs/` unless trimmed and promoted into architecture/overview.

---

## 3. Rules (human contributors)

| Topic | Rule |
|-------|------|
| **Canonical topic** | Exactly one **official** home in `docs/` **or** one module README, not both with full detail. |
| **Cross-cutting “what/why”** | `docs/` — short narrative + link to module README for “how”. |
| **Commands / env / ports** | Module `README.md` only. |
| **ADRs** | Structural decisions → `docs/adr/`. |
| **API** | Contract: OpenAPI (`/v3/api-docs` or export scripts) plus integration tests; prefer linking generated artifacts over duplicating paths. |

---

## 4. Rules for agents (DO / DON’T)

**DO**

- Prefer **updating the module README** when changing runbooks, env vars, or CLI.
- Update **`docs/`** only when the **system story** (boundaries, data model chapter, deployment concept) changes.
- Add or update an **ADR** for structural decisions.
- Ask maintainers before adding a **new** top-level topic under `docs/`.

**DON’T**

- Copy long command blocks from READMEs into `docs/`.
- Duplicate full testing or performance procedures in `docs/` when the canonical commands already live in `tests/**` or module READMEs.

---

## 5. Root README vs `docs/README.md`

| File | Role |
|------|------|
| **Repository root [`README.md`](../../README.md)** | First impression: what the repo is, badges, **one** quick start, table pointing to `docs/README.md` and key module READMEs. |
| **[`docs/README.md`](../README.md)** | Full documentation **hub**: topic table, platform assumptions, CI table, link to governance. |

**Rule:** Root README stays short; duplicated CI tables should appear in **one** place (prefer `docs/README.md` + link from root).

---

## 6. Optional migration (maintenance)

1. **Promote** selected narrative into official paths or merge into existing `docs/**/*.md` as **short** sections + links.
2. **Update** [documentation-guidelines.md](documentation-guidelines.md) when governance rules change.
3. **Optional:** Add `docs/_archive/` for thesis PDFs or snapshots if retention is required (clear banner: not maintained).

---

## 7. Risks

| Risk | Mitigation |
|------|------------|
| Breaking links in PRs | Single migration PR + grep for stale paths |
| Contributor confusion | Keep the layers table in [documentation-guidelines.md](documentation-guidelines.md) current |
| Agent rule drift | Keep agent DO/DON’T in guidelines + `.cursor/rules` aligned |
