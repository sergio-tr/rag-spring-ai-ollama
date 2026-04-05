# Local CI & Sonar Reproduction (`.github/local`)

This directory provides scripts to **replicate GitHub Actions workflows locally**, including:

* Backend verification with PostgreSQL + pgvector
* Full multi-service test execution (Java, Python, Web)
* Coverage generation
* SonarCloud analysis

---

## Available Scripts

| Script                       | Purpose                                                                         |
| ---------------------------- | ------------------------------------------------------------------------------- |
| `ci-like-sonar.sh`           | Full pipeline: build + tests + coverage + SonarCloud scan (mirrors `sonar.yml`) |
| `ci-like-verify.sh`          | Backend-only CI replication with PostgreSQL + `mvn verify` (mirrors `ci.yml`)   |
| `ci-postgres-extensions.sql` | Required PostgreSQL extensions setup (`vector`, `hstore`, `uuid-ossp`)          |

---

## `ci-like-sonar.sh`

Runs the **complete local SonarCloud pipeline**:

* Backend (`rag-service`): Maven + JaCoCo
* Classifier (`classifier-service`): pytest
* Webapp (`webapp`): Vitest coverage
* SonarCloud scan via Docker

### Requirements

* JDK 21
* Python 3.11 + pip
* Node.js (see `webapp/package.json`)
* Docker
* PostgreSQL 16 with pgvector (or Docker fallback)
* `SONAR_TOKEN` (from SonarCloud)

### Usage

```bash
export SONAR_TOKEN=your_token
.github/local/ci-like-sonar.sh
```

### Optional Environment Variables

| Variable                 | Description                            |
| ------------------------ | -------------------------------------- |
| `SONAR_BRANCH_NAME`      | Publish analysis for a specific branch |
| `SKIP_POSTGRES_PREP=1`   | Skip DB initialization                 |
| `USE_DOCKER_PG_CLIENT=0` | Force native `psql` usage              |
| `SKIP_AUTO_JDK21=1`      | Disable automatic JDK 21 detection     |

---

## `ci-like-verify.sh`

Replicates the backend portion of CI:

* Spins up PostgreSQL (Docker)
* Applies extensions and test schema
* Runs `mvn verify`

### Usage

```bash
.github/local/ci-like-verify.sh
```

### Options

```bash
--prepare-only   # Only setup DB, skip Maven
--stop-after     # Remove container after execution
```

### Environment Variables

| Variable                    | Description           |
| --------------------------- | --------------------- |
| `RAG_CI_POSTGRES_CONTAINER` | Custom container name |
| `RAG_CI_STOP_CONTAINER=1`   | Auto-remove container |

---

## PostgreSQL Setup

The scripts ensure:

* `vectordb` → extensions enabled
* `testdb` → initialized with test schema

If you prefer manual setup:

```bash
docker run -d -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=vectordb \
  pgvector/pgvector:pg16
```

Then apply:

```bash
psql -U postgres -d vectordb -f ci-postgres-extensions.sql
```

---

## Environment Defaults

Unless overridden:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vectordb
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
INTEGRATION_JDBC_URL=jdbc:postgresql://localhost:5432/testdb
```

---

## Notes

* Full git history is recommended:

  ```bash
  git fetch --unshallow
  ```
* JDK 21 is enforced automatically when possible
* Docker is used:

  * For Sonar scanner
  * Optionally as PostgreSQL client fallback

---

## Related Docs

* `docs/development/sonar-local-analysis.md`
* `.github/workflows/sonar.yml`
* `.github/workflows/ci.yml`

---

## Summary

Use this folder when you want to:

* Debug CI failures locally
* Validate coverage before pushing
* Run SonarCloud analysis without GitHub Actions