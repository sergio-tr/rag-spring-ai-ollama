# Coverage (baseline and report paths)

This document records **where** each module writes coverage output after the canonical commands. Regenerate after major refactors; **do not** commit generated HTML/XML into git (folders are usually gitignored).

**Quality baseline hub** (exclusions matrix, Sonar baseline, coverage strategy): [../quality/README.md](../quality/README.md).

**Coverage Target Ledger** (Wave 6.01+ — JaCoCo exclude census, `target_wave` per row, Sonar parity, and the **Residual final allowlist** in the Wave 6.09 section): [jacoco-coverage-target-ledger.md](jacoco-coverage-target-ledger.md).

**External test harness** (Wave 6.02 — mocks for Ollama, classifier, OTLP): [../testing/external-test-harness.md](../testing/external-test-harness.md).

**Heavy JaCoCo / Sonar excludes — per-package exit contract** (test tiers, gate, prerequisites): [../testing/rag-service-heavy-package-coverage-exit-contracts.md](../testing/rag-service-heavy-package-coverage-exit-contracts.md).

## Commands and outputs

| Module | Command (from module root) | Primary reports |
| --- | --- | --- |
| **rag-service** | `./mvnw verify` | `rag-service/target/site/jacoco/index.html`, `rag-service/target/site/jacoco/jacoco.xml` |
| **classifier-service** | `pytest` (uses `pytest.ini` / `.coveragerc`) | `classifier-service/htmlcov/index.html`, `classifier-service/coverage.xml` |
| **webapp** | `npm run test:coverage` | `webapp/coverage/index.html`, `webapp/coverage/lcov.info` |

## Gaps snapshot (sub-threshold packages — illustrative)

Figures change with every run; use the HTML reports for the current numbers.

- **rag-service (JaCoCo bundle, after `pom.xml` excludes):** The configured check is **line coverage ≥ 80%** on the instrumented bundle. Large orchestration/async surfaces (`ChatMessageApplicationService`, chat job handler, etc.) are **excluded** from the JaCoCo bundle (and mirrored in Sonar coverage exclusions) with rationale aligned to WebMvc/E2E coverage. Adapter classes (`JpaModelCatalogAdapter`, `JpaConfigurationSourceAdapter`) have **unit tests**.
- **classifier-service:** `fail_under = 80` (lines, branches on). Typical low files in reports: `app/inference/model_loader.py` (Keras/zip edge paths), `app/telemetry.py` (OTLP optional paths).
- **webapp (Vitest):** Thresholds **80%** lines/statements/functions/branches on included globs (`vitest.config.ts`). See `webapp/coverage/index.html` for per-file gaps.

## SonarCloud

- **Java + Python:** `sonar.yml` runs `mvn verify`, classifier pytest, then **Vitest** `npm run test:coverage` so `webapp/coverage/lcov.info` exists before the scan.
- **TypeScript:** Sources under `webapp/src`; **Vitest/LCOV** is the primary frontend coverage input for Sonar; Sonar adds a dashboard view of the same LCOV data.
- **Local run (same pipeline):** [development/sonar-local-analysis.md](../development/sonar-local-analysis.md).

## Related

- Testing overview: [../testing/README.md](../testing/README.md)
- Documentation hub: [../README.md](../README.md)
