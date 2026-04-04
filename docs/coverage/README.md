# Coverage (baseline and report paths)

This document records **where** each module writes coverage output after the canonical commands. Regenerate after major refactors; **do not** commit generated HTML/XML into git (folders are usually gitignored).

## Commands and outputs

| Module | Command (from module root) | Primary reports |
|--------|----------------------------|-----------------|
| **rag-service** | `./mvnw verify` | `rag-service/target/site/jacoco/index.html`, `rag-service/target/site/jacoco/jacoco.xml` |
| **classifier-service** | `pytest` (uses `pytest.ini` / `.coveragerc`) | `classifier-service/htmlcov/index.html`, `classifier-service/coverage.xml` |
| **webapp** | `npm run test:coverage` | `webapp/coverage/index.html`, `webapp/coverage/lcov.info` |

## Gaps snapshot (sub-threshold packages — illustrative)

Figures change with every run; use the HTML reports for the current numbers.

- **rag-service (JaCoCo bundle, after `pom.xml` excludes):** The configured check is **line coverage ≥ 80%** on the instrumented bundle. Large orchestration/async surfaces (`ChatMessageApplicationService`, chat job handler, etc.) are **excluded** from the JaCoCo bundle (and mirrored in Sonar coverage exclusions) with rationale aligned to WebMvc/E2E coverage. Adapter classes (`JpaModelCatalogAdapter`, `JpaConfigurationSourceAdapter`) have **unit tests**.
- **classifier-service:** `fail_under = 80` (lines, branches on). Typical low files in reports: `app/inference/model_loader.py` (Keras/zip edge paths), `app/telemetry.py` (OTLP optional paths).
- **webapp (Vitest):** Thresholds **80%** lines/statements/functions/branches on included globs. Branch gaps often appear in `src/proxy.ts`, `src/lib/sse-post.ts`, and auth forms; see `webapp/coverage/index.html`.

## SonarCloud

- **Java + Python:** `sonar.yml` runs `mvn verify`, classifier pytest, then **Vitest** `npm run test:coverage` so `webapp/coverage/lcov.info` exists before the scan.
- **TypeScript:** Sources under `webapp/src`; **Vitest/LCOV** is the primary frontend coverage input for Sonar; Sonar adds a dashboard view of the same LCOV data.

## Related

- Testing overview: [../testing/README.md](../testing/README.md)
- Documentation hub: [../README.md](../README.md)
