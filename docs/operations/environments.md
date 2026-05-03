# Environments

## Reference platform

Automation and containers target **Linux** (including **WSL2** on developer laptops). CI uses `ubuntu-*` runners.

## Patterns

| Pattern | Meaning |
| --------- | --------- |
| **Dev hybrid** | Databases or infra in Docker; Spring and/or Next.js on the host for fast iteration. |
| **Compose stack** | Services defined under `docker/` run as containers with mounted or built images. |
| **Prod-local** | Reverse proxy + hardened internal port exposure; still local/staging-oriented. |

## Host Ollama vs container Ollama

Spring must point `SPRING_AI_OLLAMA_BASE_URL` at a reachable Ollama HTTP API — on the host, inside Docker, or remote. The trade-offs and URL patterns are documented in **service READMEs**, not repeated here.

**Detail:** [../../rag-service/README.md](../../rag-service/README.md), [../../docker/README.md](../../docker/README.md), [../../ollama/README.md](../../ollama/README.md)

## Observability

When enabled, collectors and UIs are configured via `observability/.env` and Compose overrides.

**Detail:** [../../observability/README.md](../../observability/README.md)

## Canonical commands and scenario matrix

Operational scenarios (stack base includes **webapp** by default), compose chains, env files, and health evidence are maintained in **[../../docker/README.md](../../docker/README.md)** and **[../../docker/scripts/README.md](../../docker/scripts/README.md)**. The canonical operator entry points are **`./docker/scripts/up.sh`** and **`./docker/scripts/docker-compose.sh`** — documented examples must match those scripts.
