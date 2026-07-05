# Local full-stack validation with Docker

This guide describes how to run and validate the application locally using the canonical scripts under **`docker/scripts/`**. It reflects behavior encoded in **`docker/scripts/docker-compose.sh`** (invoked by **`up.sh`**, **`down.sh`**, **`build.sh`**), **`docker/docker-compose.yml`**, and the **`compose.*.yml`** overlays.

## A. Purpose

- Use Docker Compose for **Postgres**, **classifier**, **backend** (`backend` or **`backend-dev`**), **webapp**, optional **reverse-proxy**, observability, and optional tiers (profiles).
- **Direct access**: browser or `curl` hits each service’s published host port (e.g. backend `:9000`, webapp `:8081`).
- **Reverse-proxy access**: browser hits **`http://localhost`** (or `REVERSE_PROXY_*` ports); nginx routes `/` and `/_next/` to the webapp and **`${RAG_API_PRODUCT_BASE_PATH}/`** (default **`/api/v5/`**) to the backend so the UI can use **same-origin** API URLs (`NEXT_PUBLIC_API_BASE_URL` empty).

Canonical scripting references: [`docker/scripts/README.md`](../../docker/scripts/README.md), [`docker/README.md`](../../docker/README.md).

## B. Clean stop

**Recommended** (same compose project as your last `up`; omit flags if you used defaults):

```bash
./docker/scripts/down.sh prod
./docker/scripts/down.sh dev --rag --proxy --obs --classifier --logs --infra --gpu --ollama-remote
```

Match **`down`** flags to the **`up`** you used (`--rag`, `--proxy`, `--obs`, etc.). **`down.sh`** with no first argument defaults to **`prod`** mode (see [`docker/scripts/down.sh`](../../docker/scripts/down.sh)).

**Destructive (optional):** `./docker/scripts/down.sh dev --all` tears down the dev stack **and removes volumes** (`docker compose … down -v`). Use only when you intend to wipe Postgres / Grafana / Prometheus data. **`down.sh prod --all`** behaves similarly for prod-local. **Do not** use `-v` as the default for routine stops.

## C. Recommended startup modes

### Important: bare `./docker/scripts/up.sh dev`

With **no extra flags**, **`up.sh dev`** starts **`postgres` only** (see [`docker/scripts/docker-compose.sh`](../../docker/scripts/docker-compose.sh)). It does **not** start the classifier, backend, or webapp.

### Typical modes (repository-supported flags)

| Command (examples) | What starts (high level) | When to use |
| --- | --- | --- |
| `docker compose … up -d` from `docker/` with full env-file chain (see [`docker/README.md`](../../docker/README.md)) | **postgres**, **classifier-service**, **backend**, **webapp** | Matches “base stack” without dev hot-reload |
| `./docker/scripts/up.sh dev --rag` | **postgres**, **classifier-service** (reload), **backend-dev**, **webapp** | Backend + UI in Docker with DevTools reload |
| `./docker/scripts/up.sh dev --rag --proxy` | Same + **reverse-proxy** (nginx); **`compose.dev-proxy.yml`** hides direct backend/webapp ports *when* overrides apply | Same-origin **`/` + `/api/v5`** via nginx (`REVERSE_PROXY_DEV_HTTP_PORT`, default **80**) |
| `./docker/scripts/up.sh dev --rag --obs` | Adds **otel-collector**, **jaeger**, **prometheus**, **grafana** (`--profile observability`) + **`compose.obs.yml`** | Traces/metrics dashboards |
| `./docker/scripts/up.sh dev --rag --logs` | **loki**, **promtail** (`--profile logs`) | Log aggregation |
| `./docker/scripts/up.sh dev --rag --infra` | **node-exporter** (`--profile infra`) | Host metrics (`cadvisor` is separate; see [`docker/README.md`](../../docker/README.md)) |
| `./docker/scripts/up.sh prod` | **postgres**, **backend**, **classifier**, **webapp**, **reverse-proxy** (`compose.prod.yml`) | Prod-like routing and hardened internal ports |
| `./docker/scripts/up.sh prod --obs` | Prod-local + observability profile | Same as prod + OTEL/Jaeger/Prometheus/Grafana |

**“Maximum useful” dev validation** (example used in this repo’s scripts; adjust flags to match your GPU/Ollama choice):

```bash
./docker/scripts/build.sh dev --rag --proxy --obs --classifier --logs --infra --gpu --ollama-remote --no-env-prompt
./docker/scripts/up.sh dev --rag --proxy --obs --classifier --logs --infra --gpu --ollama-remote --no-env-prompt
```

- **`--gpu` / `--ollama`**: When the Docker host has an **NVIDIA** runtime, adds **`compose.gpu.yml`** and **`--profile ollama`** (Ollama **in** Docker). Without NVIDIA, the script skips the Ollama container and warns.
- **`--ollama-remote`**: With **`--gpu`**, skips the **`ollama`** container; Ollama URL is **only** from **`rag-service/.env`** (host Ollama or LAN).

Env bootstrap (optional): `./docker/scripts/up.sh dev … --env all` or **`--env db,rag,webapp,classifier,obs`**.

**Mailpit (`dev-mail` profile):** enable with **`--mail`** on [`docker/scripts/up.sh`](../../docker/scripts/up.sh) (same flag on `build`, `down`, and `config`). Example: `./docker/scripts/up.sh dev --rag --proxy --mail --no-env-prompt`. UI: `http://127.0.0.1:${MAILPIT_HTTP_PORT:-8025}/`.

## D. Ollama on the host

1. **Install and run Ollama on the host** (outside Docker). Pull models the backend expects (defaults in Compose / `rag-service/.env.example`): e.g. **`gemma3:4b`** (chat), **`mxbai-embed-large`** (embeddings).
2. **Configure the backend** via **`rag-service/.env`**:
   - **`OLLAMA_BASE_URL`** and **`SPRING_AI_OLLAMA_BASE_URL`** - HTTP base for Ollama’s API (must include port **`11434`** unless customized).
3. **Docker → host reachability**
   - **`backend-dev`** adds **`extra_hosts: host.docker.internal:host-gateway`** ([`docker/compose.dev.yml`](../../docker/compose.dev.yml)), so **`http://host.docker.internal:11434`** works on **Linux** with modern Docker.
   - **Docker Desktop** (Windows/macOS): **`host.docker.internal`** is the usual choice.
   - **Packaged `backend`** (non-dev): Compose defaults in [`docker/docker-compose.yml`](../../docker/docker-compose.yml) set **`OLLAMA_BASE_URL`** to **`http://host.docker.internal:11434`** unless overridden.
4. **If Ollama runs inside Compose** (`./docker/scripts/up.sh … --gpu` without **`--ollama-remote`**): set **`OLLAMA_BASE_URL=http://ollama:11434`** (and matching **`SPRING_AI_OLLAMA_BASE_URL`**) in **`rag-service/.env`**, per [`docker/README.md`](../../docker/README.md).
5. **Connectivity check from backend container** (example):

```bash
docker exec docker-backend-dev-1 curl -sS --max-time 5 "http://host.docker.internal:11434/api/tags" | head -c 400
```

Readiness may stay **DOWN** until configured models exist on that Ollama instance (`mxbai-embed-large`, etc.).

## E. Local URLs

Defaults vary with **`webapp/.env`**, **`observability/.env`**, and **`REVERSE_PROXY_*`**. After **`up dev --rag --proxy …`**, typical bindings include:

| Surface | URL / port |
| --- | --- |
| Reverse-proxy HTTP | **`http://127.0.0.1`** or **`http://127.0.0.1:${REVERSE_PROXY_DEV_HTTP_PORT:-80}`** |
| Reverse-proxy HTTPS | **`https://127.0.0.1:${REVERSE_PROXY_DEV_HTTPS_PORT:-8444}`** (self-signed cert baked in image; browser warnings expected) |
| Webapp direct | **`http://127.0.0.1:${WEBAPP_HTTP_PORT:-80}`** when ports published; with **`--proxy`**, check **`docker compose ps`** - often **`8081`** → **`3000`** if `WEBAPP_HTTP_PORT=8081` |
| Backend direct | **`http://127.0.0.1:${BACKEND_PORT:-9000}`** when published |
| API base (same-origin behind proxy) | **`http://127.0.0.1/api/v5`** (matches **`NEXT_PUBLIC_RAG_API_PREFIX`**) |
| Classifier | **`http://127.0.0.1:8000`** (host port from **`CLASSIFIER_SERVICE_PORT`**) |
| Postgres | **`localhost:${POSTGRES_PORT:-5432}`** |
| Swagger UI (springdoc; dev profile) | Usually **`http://127.0.0.1:9000/swagger-ui/index.html`** on backend - nginx **does not** expose arbitrary `/swagger-ui/**` on port 80 unless you extend nginx |
| OpenAPI JSON | **`http://127.0.0.1:9000/v3/api-docs`** (when enabled) |
| Grafana | **`http://127.0.0.1:${GRAFANA_PORT:-3000}`** |
| Jaeger UI | **`http://127.0.0.1:${JAEGER_UI_PORT:-16686}`** |
| Prometheus | **`http://127.0.0.1:${PROMETHEUS_PORT:-9090}`** |
| OTEL gRPC / HTTP | **`4317`**, **`4318`** (host ports from **`observability/.env`**) |
| Loki / Promtail | **`${LOKI_HOST_PORT:-3100}`**, **`${PROMTAIL_HOST_PORT:-9080}`** |
| node-exporter | **`${NODE_EXPORTER_HOST_PORT:-9100}`** |
| Mailpit | **`http://127.0.0.1:${MAILPIT_HTTP_PORT:-8025}`**, SMTP **`${MAILPIT_SMTP_PORT:-1025}`** when **`--mail`** / **`--profile dev-mail`** is active |

## F. HTTPS locally

**Already supported:**

- **Reverse-proxy image** ([`reverse-proxy/Dockerfile`](../../reverse-proxy/Dockerfile)) generates a **self-signed** certificate at **build time** (`tls.crt` / `tls.key` under `/etc/nginx/certs/`).
- **nginx** listens on **443** with TLS ([`reverse-proxy/nginx.conf`](../../reverse-proxy/nginx.conf)).
- **Dev proxy**: [`docker/compose.dev-proxy.yml`](../../docker/compose.dev-proxy.yml) publishes **`${REVERSE_PROXY_DEV_HTTPS_PORT:-8444}:443`**. **`REVERSE_PROXY_ENFORCE_HTTPS`** defaults to **`0`** in dev proxy env (no forced redirect to HTTPS unless you set **`1`**).
- **Prod-local**: [`docker/compose.prod.yml`](../../docker/compose.prod.yml) uses **`${REVERSE_PROXY_HTTPS_PORT:-8443}:443`** and **`REVERSE_PROXY_ENFORCE_HTTPS` defaults to `1`** - HTTP may redirect to HTTPS.

**Browser trust:** Accept the certificate warning for the baked-in self-signed cert, or terminate TLS with **mkcert** / your own CA and mount certs by extending the reverse-proxy service with a **local-only** compose override (paths **gitignored**; never commit private keys).

## G. Rebuild rules

| Change | Action |
| --- | --- |
| **`NEXT_PUBLIC_*`**, **`WEBAPP_NEXT_PUBLIC_*`** in **`webapp/.env`** | **Rebuild webapp image** (`build.sh …` or `docker compose build webapp`) and **recreate** the container - values are **build-args** ([`docker/docker-compose.yml`](../../docker/docker-compose.yml)). |
| **`reverse-proxy/nginx.conf`** or **`reverse-proxy/Dockerfile`** | **`docker compose build reverse-proxy`** (or **`build.sh`** with same `-f` chain). |
| Backend Java / **`pom.xml`** | Rebuild **`backend`** / **`backend-dev`** image or rely on **`backend-dev`** bind-mount + DevTools as appropriate. |
| **Dockerfiles / compose build args** | **`./docker/scripts/build.sh`** with the **same** mode and flags as **`up`**. |

## H. Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| **Google OAuth CTA missing** | **`NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED`** not **`true`** at **build** time; rebuild webapp. Check [`webapp/README.md`](../../webapp/README.md). |
| **`redirect_uri_mismatch`** | Google Console must list **`RAG_AUTH_BACKEND_BASE_URL` + `RAG_AUTH_OAUTH_GOOGLE_REDIRECT_PATH`** exactly; see [`rag-service/README.md`](../../rag-service/README.md), [`rag-service/.env.example`](../../rag-service/.env.example). |
| **Register logs in immediately** | Email confirmation disabled - **`RAG_AUTH_EMAIL_CONFIRMATION_ENABLED`** not **`true`** in **`rag-service/.env`**. |
| **No verification / reset email** | Enable **`--mail`** (or set **`SPRING_MAIL_HOST=mailpit`** manually). Check **`RAG_AUTH_PASSWORD_RESET_ENABLED`**, **`RAG_AUTH_MAIL_ENABLED`**, and **`RAG_AUTH_WEBAPP_BASE_URL`**. Invalid real SMTP creds can make **readiness** fail when mail health is in the readiness group. |
| **Backend cannot reach Ollama** | Wrong **`SPRING_AI_OLLAMA_BASE_URL`**; host firewall; models not pulled. Use **`host.docker.internal:11434`** on Docker Desktop or Linux with **`extra_hosts`** (see §D). |
| **502 from nginx** | Upstream down or **readiness** failing; check **`docker compose ps`** and **`docker logs`** for **`backend-dev`** / **`webapp`**. |
| **Stale `NEXT_PUBLIC_*`** | Rebuild webapp image; restart container - see §G. |
| **DB / migration issues** | Postgres logs; **`postgres-collation-bootstrap`** job; volume corruption - only then consider **`down … -v`** (destructive). |
| **`/actuator/health` = 503`** | Overall readiness **DOWN** (e.g. **ollama**, **mail**, disk). **`/actuator/health/liveness`** may still be **200**. |

## Do not commit

Never commit or push:

- TLS **private keys**, `.pem` / `.key` material used for local HTTPS experiments  
- **Gmail** or other SMTP passwords / app passwords  
- **OAuth client secrets**, **`RAG_JWT_SECRET`**, database passwords  
- **`*.env`** files filled with real production values  

Use **`.env.example`** templates and local-only overrides ignored by git.

Related: [`docker/scripts/README.md`](../../docker/scripts/README.md), [`docker/README.md`](../../docker/README.md), [`docs/devops/README.md`](README.md).
