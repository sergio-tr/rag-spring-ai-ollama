# Nginx reverse proxy (TLS + routing)

Assets for the `reverse-proxy` Compose service. Built from `docker/compose.prod.yml` and `docker/compose.dev-proxy.yml`.

## Features

- HTTP on container port **80**, HTTPS on container port **443** (host ports via `REVERSE_PROXY_*`).
- Self-signed TLS certificate generated at **container start** when missing (not at image build time).
- Subject Alternative Names (SAN) from `TLS_CERT_*` environment variables.
- Persistent certificate volume `reverse_proxy_certs` → `/etc/nginx/certs`.
- HTTP→HTTPS redirect when `REVERSE_PROXY_ENFORCE_HTTPS=1`.
- Same API, auth, OAuth, and Next.js routes on both HTTP and HTTPS listeners.

## TLS flow

1. Container starts → `docker-entrypoint.sh` exports routing and TLS variables.
2. `/generate-self-signed-cert.sh` writes `tls.crt` / `tls.key` **only if missing** (or when `TLS_CERT_FORCE_REGENERATE=1`).
3. OpenSSL config is built dynamically; empty `TLS_CERT_DNS_*` / `TLS_CERT_IP_*` entries are omitted.
4. `envsubst` renders `nginx.conf.template` → `/etc/nginx/nginx.conf`.
5. Nginx starts with `exec nginx -g 'daemon off;'`.

Reference SAN list: [`openssl-san.cnf`](openssl-san.cnf) (documentation defaults). Active config is generated at runtime.

## Local development

In `webapp/.env` (or root `.env.example`):

```env
REVERSE_PROXY_DEV_HTTP_PORT=8080
REVERSE_PROXY_DEV_HTTPS_PORT=8444
REVERSE_PROXY_ENFORCE_HTTPS=0
REVERSE_PROXY_HTTPS_PORT_SUFFIX=:8444
REVERSE_PROXY_SERVER_NAME=localhost
TLS_CERT_COMMON_NAME=localhost
TLS_CERT_DNS_1=localhost
TLS_CERT_DNS_2=host.docker.internal
TLS_CERT_IP_1=127.0.0.1
PUBLIC_APP_URL=http://localhost:8080
PUBLIC_API_URL=http://localhost:8080/api/v5
```

Start: `./docker/scripts/up.sh dev --rag --proxy`

| URL | Address |
| --- | --- |
| HTTP | `http://localhost:8080` |
| HTTPS | `https://localhost:8444` (browser warns on self-signed cert) |

HTTPS-only testing:

```env
REVERSE_PROXY_ENFORCE_HTTPS=1
PUBLIC_APP_URL=https://localhost:8444
PUBLIC_API_URL=https://localhost:8444/api/v5
NEXT_PUBLIC_APP_URL=https://localhost:8444
```

## Production (university application server)

Application server: `156.35.95.27`. Replace `uniovi.es` with the real hostname if different.

```env
REVERSE_PROXY_HTTP_PORT=80
REVERSE_PROXY_HTTPS_PORT=443
REVERSE_PROXY_ENFORCE_HTTPS=1
REVERSE_PROXY_HTTPS_PORT_SUFFIX=
REVERSE_PROXY_SERVER_NAME=uniovi.es
TLS_CERT_COMMON_NAME=uniovi.es
TLS_CERT_DNS_1=uniovi.es
TLS_CERT_DNS_2=www.uniovi.es
TLS_CERT_DNS_3=rag.uniovi.es
TLS_CERT_IP_1=156.35.95.27
PUBLIC_APP_URL=https://uniovi.es
PUBLIC_API_URL=https://uniovi.es/api/v5
NEXT_PUBLIC_APP_URL=https://uniovi.es
RAG_AUTH_WEBAPP_BASE_URL=https://uniovi.es
RAG_AUTH_BACKEND_BASE_URL=https://uniovi.es
RAG_CORS_ALLOWED_ORIGINS=https://uniovi.es
```

**Manual steps:**

1. Publish host port **443** in Docker Compose and allow it on the university firewall.
2. Configure **DNS** for the public hostname → `156.35.95.27`.
3. **Google OAuth:** authorized redirect URI must match the production HTTPS callback exactly, e.g. `https://uniovi.es/api/v5/auth/oauth/google/callback`.
4. Self-signed certificates show a browser warning unless trusted manually. For public production, mount a **CA-signed** cert at `TLS_CERT_PATH` / `TLS_KEY_PATH` (pre-populate the volume to skip auto-generation).

## TLS environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `TLS_CERT_DIR` | `/etc/nginx/certs` | Certificate directory |
| `TLS_CERT_PATH` | `/etc/nginx/certs/tls.crt` | Certificate file |
| `TLS_KEY_PATH` | `/etc/nginx/certs/tls.key` | Private key |
| `OPENSSL_CONFIG` | `/etc/nginx/openssl-san.cnf` | Base path (generated config: `.generated`) |
| `TLS_CERT_DAYS` | `365` | Validity period |
| `TLS_CERT_COMMON_NAME` | `localhost` | Certificate CN |
| `TLS_CERT_DNS_1`…`4` | - | DNS SAN entries |
| `TLS_CERT_IP_1`, `2` | `127.0.0.1` | IP SAN entries |
| `TLS_CERT_FORCE_REGENERATE` | `0` | Set `1` to replace existing cert |

## Force regeneration

```env
TLS_CERT_FORCE_REGENERATE=1
```

Restart `reverse-proxy` once, then unset.

## Verify

```bash
docker compose exec reverse-proxy nginx -t
docker compose exec reverse-proxy openssl x509 -in /etc/nginx/certs/tls.crt -text -noout | grep -A 10 "Subject Alternative Name"
curl -k "https://localhost:8443/"
curl "http://localhost:8080/"
```

Adjust ports for prod (`443`) or dev (`8444`).

## Files

| File | Role |
| --- | --- |
| `Dockerfile` | nginx:1.27-alpine + openssl |
| `nginx.conf` | envsubst template |
| `docker-entrypoint.sh` | cert generation + envsubst + nginx |
| `generate-self-signed-cert.sh` | Runtime self-signed cert |
| `openssl-san.cnf` | SAN reference defaults |
| `openssl-san.cnf.template` | envsubst template (reference) |
