# RAG Spring AI Ollama

<!-- ═══════════════════════════════════════════════════════════════════════════
     BADGES
     ═══════════════════════════════════════════════════════════════════════════ -->

<!-- CI & Build -->
[![CI](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/ci.yml)
[![Build (no tests)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build.yml)
[![SonarCloud](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/sonar.yml/badge.svg?branch=main)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/sonar.yml)

<!-- SonarCloud quality metrics -->
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=alert_status)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=coverage)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=bugs)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Security Hotspots](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=security_hotspots)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)

<!-- Last release -->
[![GitHub Release](https://img.shields.io/github/v/release/sergio-tr/rag-spring-ai-ollama?label=latest%20release&color=blue)](https://github.com/sergio-tr/rag-spring-ai-ollama/releases/latest)
[![Build & Push Images](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build-images.yml/badge.svg)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build-images.yml)

---

RAG (Retrieval-Augmented Generation) system built with **Spring Boot**, **Spring AI**, **Ollama**, and **PostgreSQL + pgvector**. Includes a trainable query-type classifier exposed as an HTTP microservice (FastAPI + TensorFlow).

## Quick start (development)

```bash
# 1. Create env files for each component
./scripts/create-env-all.sh

# 2. Start infrastructure (Postgres) in Docker
./scripts/dev.sh

# 3. Backend with hot-reload (terminal 2)
cd rag-service && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Classifier with hot-reload (terminal 3)
cd classifier-service && uvicorn main:app --reload --reload-dir app

# API available at http://localhost:9000/api/v4
```

## Full stack with Docker Compose

```bash
# Build and start all services
cd docker
docker compose \
  --env-file ../db/.env \
  --env-file ../rag-service/.env \
  --env-file ../classifier-service/.env \
  up -d
```

## Execution modes

| Mode | Command | Description |
|---|---|---|
| Dev (hybrid) | `./scripts/dev.sh` | Only infra in Docker; services run locally |
| Full compose | `cd docker && docker compose ... up -d` | Everything in Docker |
| With observability | add `-f compose.obs.yml --env-file ../observability/.env` | + OTEL/Jaeger/Prometheus/Grafana |
| With GPU (Ollama) | add `-f compose.gpu.yml --env-file ../ollama/.env` | + Ollama with NVIDIA GPU |
| Prod local | `./scripts/up-prod-local.sh` | Hardened: nginx reverse proxy, no exposed ports |

## Key API endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v4/query?question=...` | RAG query |
| `POST` | `/api/v4/documents` | Upload document (multipart) |
| `POST` | `/api/v4/documents/minute` | Add meeting minute (JSON) |
| `DELETE` | `/api/v4/documents/{id}` | Delete document |
| `GET` | `/api/v4/evaluate` | Run RAG evaluation |
| `POST` | `/api/v4/evaluate/custom` | Evaluate with custom config |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

Classifier endpoints: `POST /classify`, `GET /models`, `POST /train`, `POST /evaluate`

## Documentation

| Document | Description |
|---|---|
| [docs/ENTORNO_DESARROLLO.md](docs/ENTORNO_DESARROLLO.md) | Dev environment setup and hot-reload guide |
| [docs/MODOS_EJECUCION.md](docs/MODOS_EJECUCION.md) | All execution modes (dev, compose, prod local) |
| [docs/SMOKE_TEST.md](docs/SMOKE_TEST.md) | Manual smoke test checklist |
| [docs/RECONOCIMIENTO_ESTADO_SISTEMA.md](docs/RECONOCIMIENTO_ESTADO_SISTEMA.md) | System state analysis and known issues |
| [docs/SONAR_QUALITY_GATE.md](docs/SONAR_QUALITY_GATE.md) | SonarCloud quality gate configuration guide |
| [observability/README.md](observability/README.md) | Observability stack configuration |

## Tech stack

**Backend**: Spring Boot · Spring AI · Java · Maven · Flyway · JaCoCo  
**Classifier**: FastAPI · TensorFlow/Keras · Python 3.11 · pytest-cov  
**Database**: PostgreSQL + pgvector  
**LLM runtime**: Ollama (local, GPU-optional)  
**Observability**: OpenTelemetry · Jaeger · Prometheus · Grafana · Loki  
**Infrastructure**: Docker · Docker Compose · Nginx  
**CI/CD**: GitHub Actions · SonarCloud · GHCR  
