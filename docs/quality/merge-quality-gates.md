# Merge and release quality gates

Blocking checks are defined in **[`docs/devops/README.md`](../devops/README.md)** (fork tokens, required checks). This page states the **technical** Minimum Viable Merge contract for this repository.

## Mandatory local / CI parity (before merge)

| Gate | Command / evidence | Pass | Reject |
|------|---------------------|------|--------|
| **G1 Backend** | `cd rag-service && ./mvnw clean verify` | Exit **0**; **`jacoco:check`** passes | Any Surefire failure/error; JaCoCo **LINE** bundle &lt; **0.80** |
| **G2 Classifier** | `cd classifier-service && pytest tests/` (with deps installed) | Exit **0**; **`coverage.xml`** produced; `fail_under` satisfied | pytest failure; coverage under threshold |
| **G3 Webapp** | `npm ci && npm run lint && npm run typecheck && npm run test:coverage && npm run build` | Exit **0**; **Vitest** thresholds met; **`webapp/coverage/lcov.info`** exists | ESLint **errors**; type errors; Vitest failure; coverage below thresholds |
| **G4 Sonar inputs** | Reports exist at paths in [`sonar-project.properties`](../../sonar-project.properties) | JaCoCo XML, Python `coverage.xml`, TS `lcov.info` after G1–G3 | Missing reports on a branch that runs Sonar |

**Toolchain:** use **Node ≥ 22** and an **npm** version compatible with `webapp/package-lock.json` (see [runbook](../testing/baseline-runbook.md)); **Python 3.11+** for classifier parity with CI.

## Sonar Cloud (organization gate)

- **Quality Gate** on **new code** is the primary **cross-language** merge contract for teams using Sonar for PRs.
- **Fix** or **resolve** Security **Hotspots** per org policy — do not rely on JaCoCo bundle green alone.

## Explicit reject conditions (even if “it compiles”)

- **`verify` red** but merge “by exception” without a logged waiver.
- **New** `rag-service` JaCoCo or Sonar coverage excludes **without** updating the exclusion matrix + [jacoco ledger](../coverage/jacoco-coverage-target-ledger.md) / [maven-jacoco-inventory.md](maven-jacoco-inventory.md).
- **New** tests that depend on **real** Ollama/classifier URLs by default — see [external-mocks-policy.md](external-mocks-policy.md).
- **New** scattered **`/api/vN`** literals in tests — see [api-path-policy.md](api-path-policy.md).

## Release candidate (optional hardening)

For a tagged release, re-run the [baseline runbook](../testing/baseline-runbook.md) on the **tag SHA**, update the **Baseline execution record** in [README.md](README.md), and capture Sonar metrics in [sonar-baseline-record.md](sonar-baseline-record.md).
