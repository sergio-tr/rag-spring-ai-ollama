# Run SonarCloud analysis locally (CI parity)

This mirrors [`.github/workflows/sonar.yml`](../../.github/workflows/sonar.yml): Java `mvn verify` + JaCoCo, classifier `pytest` + `coverage.xml`, webapp Vitest + LCOV, then **SonarScanner** uploading to SonarCloud.

Configuration lives in the repo root [`sonar-project.properties`](../../sonar-project.properties).

## Prerequisites

| Requirement | Notes |
| ------------- | -------- |
| **SONAR_TOKEN** | SonarCloud → *My Account → Security* → generate a token. Never commit it. |
| **Full git history** | Sonar uses blame for new code. If the clone is shallow: `git fetch --unshallow`. |
| **JDK 21** | Same as CI (`setup-java` in `sonar.yml`). `sonar-local.sh` tries to prepend `/usr/lib/jvm/java-21-*` (and Temurin 21 paths) to `PATH` when the default `java` is older but JDK 21 is installed. Set `SKIP_AUTO_JDK21=1` to disable. If Maven still reports `release version 21 not supported`, see [Troubleshooting](#jdk-21-release-version-21-not-supported). |
| **PostgreSQL 16 + pgvector** | On `localhost:5432`, database `vectordb`, user/password `postgres` (or override env vars below). |
| **Client tools** | `psql` / `pg_isready` on `PATH`, **or** Docker: `sonar-local.sh` uses `pgvector/pgvector:0.8.2-pg16-bookworm` with `--network host`. Override host with `SONAR_PG_DOCKER_HOST`. |
| **Python 3.11** | For `classifier-service` tests. |
| **Node.js** | **`webapp/package.json` requires Node ≥ 22** (Vitest/rolldown); use the same in CI. On Node 20, `npm run test:coverage` may fail to load native `rolldown` bindings. |
| **Docker** | Used to run `sonarsource/sonar-scanner-cli` (same stack family as `SonarSource/sonarqube-scan-action` in CI). |

## Environment variables (same idea as CI)

Defaults match `sonar.yml` unless you export overrides:

- `SPRING_DATASOURCE_URL` - default `jdbc:postgresql://localhost:5432/vectordb`
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` - default `postgres`
- `RAG_JWT_SECRET` - long test secret (required for Spring tests)
- `RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false`
- `INTEGRATION_JDBC_URL` - default `jdbc:postgresql://localhost:5432/testdb`
- `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false` - avoids empty OTLP URL issues during tests

## Start Postgres (example with Docker)

If you do not already run Postgres locally:

```bash
docker run -d --name sonar-ci-pg -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=vectordb \
  pgvector/pgvector:0.8.2-pg16-bookworm
```

Wait until the instance accepts connections, then run the scripts (the `sonar-local` scripts wait for `pg_isready` and apply extensions + `testdb`).

## Run the full pipeline

**Linux / macOS / Git Bash / WSL** (from repository root):

```bash
export SONAR_TOKEN="your_token_here"
chmod +x .github/local/sonar-local.sh
.github/local/sonar-local.sh
```

### Optional: branch name in SonarCloud

So the dashboard shows results under your feature branch (not only “main”):

```bash
export SONAR_BRANCH_NAME="$(git branch --show-current)"
.github/local/sonar-local.sh
```

### Skip Postgres bootstrap

If you already applied `.github/local/ci-postgres-extensions.sql`, created `testdb`, and ran `test-init.sql` (e.g. after `ci-like-verify`):

```bash
SKIP_POSTGRES_PREP=1 .github/local/sonar-local.sh
```

## Without Docker (scanner only)

If builds and coverage are already generated, you can run the scanner [installed locally](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/analysis-methods/sonarscanner-cli/) from the repo root:

```bash
export SONAR_TOKEN="..."
sonar-scanner \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.projectBaseDir=.
```

Ensure these exist first (as in CI):

- `rag-service/target/classes`, `rag-service/target/site/jacoco/jacoco.xml`, `rag-service/target/dependency/*.jar`
- `classifier-service/coverage.xml`
- `webapp/coverage/lcov.info`

## Security Hotspots (quality gate)

The Sonar **Security Hotspots Reviewed** condition is satisfied in the UI: open each hotspot in SonarCloud, confirm or fix, and mark as **Safe** / **Fixed** as appropriate. Code-only fixes (logging sanitization, binding addresses, removing default credentials) help, but the gate still requires **reviewed** state in the dashboard.

## Faster feedback in the IDE

[SonarLint](https://www.sonarsource.com/products/sonarlint/) connected to SonarCloud catches many of the same rules while you edit; it does **not** replace a full scan (coverage, security engine versions, PR “new code” window).

## Troubleshooting

### JDK 21 / `release version 21 not supported`

Maven compiles `rag-service` with **Java 21**. That error means the JDK on your `PATH` (or behind `JAVA_HOME`) is **17, 11, or older**.

**WSL / Debian / Ubuntu:**

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # adjust if `ls /usr/lib/jvm` shows a different path
java -version   # should show 21.x
```

**Portable `JAVA_HOME` from `java` on PATH:**

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
```

Then re-run `./.github/local/sonar-local.sh`.

If **`java -version` still shows 11** after installing `openjdk-21-jdk`, your `PATH` prefers the old JDK. For one terminal session:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Adjust `JAVA_HOME` if `ls /usr/lib/jvm` lists a different directory (e.g. `java-21-openjdk-arm64` on ARM). To change the default for all shells: `sudo update-alternatives --config java`.

### Other

| Symptom | Likely cause |
| --------- | ---------------- |
| *Project not found* | Missing or wrong `SONAR_TOKEN`, or `sonar.organization` / `sonar.projectKey` in `sonar-project.properties` does not match your SonarCloud project. |
| Java analysis noise / unresolved types | Run `mvnw dependency:copy-dependencies` so `rag-service/target/dependency` is populated (the `sonar-local` scripts do this). |
| *CI analysis while Automatic Analysis is enabled* | In SonarCloud: *Project → Administration → Analysis Method* - use **CI-based** analysis only. |

## Related

- Root README: [SonarCloud section](../../README.md#sonarcloud-quality-gate-and-static-analysis)
- Workflow: [`.github/workflows/sonar.yml`](../../.github/workflows/sonar.yml)
