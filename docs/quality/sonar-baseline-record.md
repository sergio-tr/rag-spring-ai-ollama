# Sonar Cloud baseline record

Sonar Cloud is the **authority** for *new code* quality (Quality Gate, PR decoration). This file links the **real analysis workflows** and leaves a **template** for numeric snapshots (fill from the dashboard when closing a baseline or release).

## Project identity

| Field | Value |
|-------|--------|
| **Project key** | `sergio-tr_rag-spring-ai-ollama` ([`sonar-project.properties`](../../sonar-project.properties)) |
| **Organization** | `sergio-tr` |
| **Summary (new code)** | [Sonar Cloud project summary](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama) |

**Badges (live):** root [`README.md`](../../README.md) embeds Quality Gate, coverage, bugs, vulnerabilities, etc.

## Analysis workflows (where the scan runs)

| Trigger | Workflow | Notes |
|---------|----------|--------|
| **Primary (PR / main DAG)** | [`ci.yml`](../../.github/workflows/ci.yml) → [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) job **`sonar`** | Runs after core backend / classifier / webapp steps; uploads coverage inputs listed in [`sonar-project.properties`](../../sonar-project.properties). |
| **Manual / ad-hoc** | [`sonar.yml`](../../.github/workflows/sonar.yml) (`workflow_dispatch` only) | Comment in file: PR analysis normally runs **inside** CI, not from this file alone. |
| **Local analyst parity** | [../development/sonar-local-analysis.md](../development/sonar-local-analysis.md) | Optional; does not replace CI. |

### Inputs required before scan (G4)

Built by the [baseline runbook](../testing/baseline-runbook.md):

- `rag-service/target/site/jacoco/jacoco.xml`
- `classifier-service/coverage.xml`
- `webapp/coverage/lcov.info`

## Numeric baseline (manual capture)

Copy from the Sonar Cloud **Projects → your project → main branch** (or PR **new code** view). Replace placeholders.

| Metric | Snapshot value | Date (UTC) | Notes |
|--------|------------------|------------|--------|
| Quality Gate | e.g. **Passed** / Failed | — | Org-level gate; see Sonar docs. |
| Coverage on **new code** | e.g. **\_%** | — | PR / leak period; not the same as JaCoCo bundle. |
| Bugs | \_ | — | |
| Vulnerabilities | \_ | — | |
| Security hotspots (open) | \_ | — | |
| Duplications (new code) | optional | — | |

**Procedure:** open the dashboard link above → record **Overview** / **Measures** relevant to your team’s merge contract → paste into this table or into a linked release note.

**Fork PR note:** without `SONAR_TOKEN`, reusable pipeline may skip or fail Sonar steps; see [../devops/README.md](../devops/README.md).
