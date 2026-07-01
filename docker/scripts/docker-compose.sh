#!/usr/bin/env bash
# Unified Docker Compose: build | up | down for dev (hybrid infra) or prod-local stacks.
# Same compose file chain and env files historically used by repository up/down entrypoints.
#
# Usage (from repository root):
#   ./docker/scripts/docker-compose.sh <build|config|up|down> <dev|prod> [env options] [stack options]
#
#   down: second arg defaults to prod if omitted (compat with old down.sh).
#
# Env (optional; runs before compose up/build — not before dev down):
#   --env <name>     create-env-* (repeatable): db, obs, rag, classifier, ollama, webapp, all
#   --no-env-prompt  skip interactive set-env.sh question
#
# dev:
#   [--all] [--gpu] [--ollama] [--obs] [--classifier] [--classifier-gpu] [--logs] [--infra] [--mail] [--rag] [--proxy] [--ollama-remote] [--down] [--volumes]
#   --rag: Spring backend (backend-dev) + webapp in Docker, plus infra as selected.
#   --proxy: with --rag only. Publishes nginx (default :8080 → webapp + API); hides webapp/backend direct host ports.
#   With command "up":   --down / --volumes tear down the dev stack (same as "down dev").
#   With command "down": same flags as "up dev --down".
#   --gpu and --ollama are aliases: both start Ollama in Docker (GPU only, if available).
#   --classifier-gpu enables GPU access for classifier-service (if available).
#   --ollama-remote skips the local Ollama container profile when combined with --gpu/--ollama (URL always from rag-service/.env).
#   Local Ollama in Docker uses profile "ollama" in docker-compose.yml (+ ollama/.env for http://ollama:11434 when --gpu and NVIDIA available).
#   --all  = --gpu --obs --classifier --logs --infra --rag (no --proxy; add --proxy to use nginx in dev)
#
# prod:
#   [--server] [--all] [--obs] [--obs-private] [--gpu] [--ollama] [--classifier-gpu] [--ollama-remote] [--logs] [--infra] [--mail] [--volumes]
#   --server: university/production VM — merges compose.prod-server.yml (no host backend/classifier ports); implies --ollama-remote.
#   --volumes only applies to "down prod".
#   --all  = --obs --gpu --logs --infra (and for down: removes volumes)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"

usage() {
  echo "Usage: $0 <build|config|up|down> <dev|prod> [options]" >&2
  echo "  down: <dev|prod> optional (defaults to prod)" >&2
  echo "  Env: --env <db|obs|rag|classifier|ollama|all> ..., --no-env-prompt" >&2
  echo "  dev:  [--all] [--gpu|--ollama] [--ollama-remote] [--obs] [--classifier] [--classifier-gpu] [--logs] [--infra] [--mail] [--rag] [--proxy] [--down] [--volumes]" >&2
  echo "  prod: [--server] [--all] [--obs] [--obs-private] [--gpu|--ollama] [--ollama-remote] [--classifier-gpu] [--logs] [--infra] [--mail] [--volumes]" >&2
  exit 1
}

CMD="${1:-}"
case "$CMD" in
  build|config|up|down) shift ;;
  *)
    echo "Usage: $0 <build|config|up|down> <dev|prod> ..." >&2
    exit 1
    ;;
esac

MODE="${1:-}"
if [ -z "$MODE" ]; then
  if [ "$CMD" = down ]; then
    MODE=prod
  else
    usage
  fi
else
  case "$MODE" in
    dev|prod) shift ;;
    *) usage ;;
  esac
fi

ENV_COMPONENTS=()
NO_ENV_PROMPT=false
REST=()

while [ $# -gt 0 ]; do
  case "$1" in
    --env=*)
      ENV_COMPONENTS+=("${1#*=}")
      shift
      ;;
    --env)
      shift
      [ $# -lt 1 ] && { echo "Error: --env requires a value" >&2; exit 1; }
      ENV_COMPONENTS+=("$1")
      shift
      ;;
    --no-env-prompt)
      NO_ENV_PROMPT=true
      shift
      ;;
    *)
      REST+=("$1")
      shift
      ;;
  esac
done

run_create_env_component() {
  local c="$1"
  case "$c" in
    all) "$SCRIPT_DIR/create-env-all.sh" ;;
    db) "$SCRIPT_DIR/create-env-db.sh" ;;
    obs|observability) "$SCRIPT_DIR/create-env-observability.sh" ;;
    rag) "$SCRIPT_DIR/create-env-rag-service.sh" ;;
    classifier) "$SCRIPT_DIR/create-env-classifier-service.sh" ;;
    ollama) "$SCRIPT_DIR/create-env-ollama.sh" ;;
    webapp) "$SCRIPT_DIR/create-env-webapp.sh" ;;
    *)
      echo "Unknown --env component: $c (use: db, obs, rag, classifier, ollama, webapp, all)" >&2
      exit 1
      ;;
  esac
}

run_env_from_list() {
  local IFS=,
  local part
  for part in $1; do
    part="$(echo "$part" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [ -n "$part" ] || continue
    run_create_env_component "$part"
  done
}

maybe_run_env_setup() {
  local for_action="$1"
  if [ "$for_action" != up ] && [ "$for_action" != build ]; then
    return 0
  fi
  if [ ${#ENV_COMPONENTS[@]} -gt 0 ]; then
    echo "=== Creating .env file(s) ==="
    for c in "${ENV_COMPONENTS[@]}"; do
      run_env_from_list "$c"
    done
    echo ""
    return 0
  fi
  if [ "$NO_ENV_PROMPT" = true ] || [ ! -t 0 ]; then
    return 0
  fi
  echo -n "Run interactive .env setup (set-env.sh)? [y/N] "
  read -r ans
  case "${ans:-n}" in
    y|Y) "$SCRIPT_DIR/set-env.sh" ;;
    *) ;;
  esac
  echo ""
}

set -- "${REST[@]}"

# ---------- dev ----------
if [ "$MODE" = dev ]; then
  WITH_GPU=false
  WITH_OBS=false
  WITH_CLASSIFIER=false
  WITH_CLASSIFIER_GPU=false
  WITH_LOGS=false
  WITH_INFRA=false
  WITH_MAIL=false
  WITH_RAG_BACKEND=false
  WITH_DEV_PROXY=false
  WITH_OLLAMA_REMOTE=false
  WITH_NVIDIA=false
  ACTION=up
  WITH_VOLUMES=false
  ALL=false

  if [ "$CMD" = down ]; then
    ACTION=down
  elif [ "$CMD" = build ]; then
    ACTION=build
  elif [ "$CMD" = config ]; then
    ACTION=config
  else
    ACTION=up
  fi

  for arg in "$@"; do
    case "$arg" in
      --all)        ALL=true ;;
      --gpu)        WITH_GPU=true ;;
      --ollama)     WITH_GPU=true ;;
      --ollama-remote) WITH_OLLAMA_REMOTE=true ;;
      --obs)        WITH_OBS=true ;;
      --classifier) WITH_CLASSIFIER=true ;;
      --classifier-gpu) WITH_CLASSIFIER_GPU=true ;;
      --logs)       WITH_LOGS=true ;;
      --infra)      WITH_INFRA=true ;;
      --mail)       WITH_MAIL=true ;;
      --rag)        WITH_RAG_BACKEND=true ;;
      --proxy)      WITH_DEV_PROXY=true ;;
      --down)       ACTION=down ;;
      --volumes)    WITH_VOLUMES=true ;;
      *)
        echo "Unknown argument: $arg" >&2
        usage
        ;;
    esac
  done

  if [ "$CMD" = build ] && [ "$ACTION" = down ]; then
    echo "Error: cannot combine build with --down" >&2
    exit 1
  fi

  if [ "$WITH_DEV_PROXY" = true ] && [ "$WITH_RAG_BACKEND" != true ]; then
    echo "Error: --proxy requires --rag (backend-dev + webapp behind nginx)." >&2
    exit 1
  fi

  if [ "$ALL" = true ]; then
    WITH_GPU=true
    WITH_OBS=true
    WITH_CLASSIFIER=true
    WITH_CLASSIFIER_GPU=true
    WITH_LOGS=true
    WITH_INFRA=true
    WITH_RAG_BACKEND=true
    WITH_OLLAMA_REMOTE=false
    if [ "$ACTION" = down ]; then
      WITH_VOLUMES=true
    fi
  fi

  has_nvidia_runtime() {
    docker run --rm --gpus all nvidia/cuda:12.5.1-base-ubuntu22.04 nvidia-smi >/dev/null 2>&1
  }

  # Always try to enable NVIDIA GPU if the host supports it, unless user explicitly didn't request it.
  # If the host doesn't support it, fall back to CPU silently (or warn if user requested GPU).
  if has_nvidia_runtime; then
    WITH_NVIDIA=true
  else
    if [ "$WITH_GPU" = true ] || [ "$WITH_CLASSIFIER_GPU" = true ]; then
      echo "Warning: NVIDIA runtime not available on this Docker host; falling back to CPU." >&2
    fi
    WITH_NVIDIA=false
  fi

  CLASSIFIER_CUDA_BASE_IMAGE=nvidia/cuda:12.5.1-cudnn-runtime-ubuntu22.04
  if [ "$WITH_CLASSIFIER_GPU" = true ] && [ "$WITH_NVIDIA" = true ]; then
    export CLASSIFIER_PYTHON_BASE_IMAGE="$CLASSIFIER_CUDA_BASE_IMAGE"
    export CLASSIFIER_INSTALL_GPU_EXTRAS=1
  fi

  COMPOSE_FILES=(-f "docker-compose.yml")
  # compose.dev.yml: classifier hot-reload and/or backend-dev definition (--rag)
  if [ "$WITH_CLASSIFIER" = true ] || [ "$WITH_RAG_BACKEND" = true ]; then
    COMPOSE_FILES+=(-f "compose.dev.yml")
  fi
  # webapp ordering dependency is now part of compose.dev.yml
  if [ "$WITH_RAG_BACKEND" = true ] && [ "$WITH_DEV_PROXY" != true ]; then
    COMPOSE_FILES+=(-f "compose.dev-direct-ports.yml")
  fi
  [ "$WITH_DEV_PROXY" = true ] && [ "$WITH_RAG_BACKEND" = true ] && COMPOSE_FILES+=(-f "compose.dev-proxy.yml")
  [ "$WITH_OBS" = true ]        && COMPOSE_FILES+=(-f "compose.obs.yml")
  if [ "$WITH_NVIDIA" = true ] && [ "$WITH_CLASSIFIER_GPU" = true ]; then
    COMPOSE_FILES+=(-f "compose.gpu.yml")
  fi
  [ "$WITH_RAG_BACKEND" = true ] && [ "$WITH_OBS" = true ] && COMPOSE_FILES+=(-f "compose.rag-dev-obs.yml")
  [ "$WITH_MAIL" = true ] && COMPOSE_FILES+=(-f "compose.dev-mail.yml")

  PROFILE_ARGS=()
  [ "$WITH_OBS" = true ] && PROFILE_ARGS+=(--profile observability)
  [ "$WITH_LOGS" = true ] && PROFILE_ARGS+=(--profile logs)
  [ "$WITH_INFRA" = true ] && PROFILE_ARGS+=(--profile infra)
  [ "$WITH_MAIL" = true ] && PROFILE_ARGS+=(--profile dev-mail)
  if [ "$WITH_GPU" = true ] && [ "$WITH_NVIDIA" = true ] && [ "$WITH_OLLAMA_REMOTE" != true ]; then
    PROFILE_ARGS+=(--profile ollama)
  fi
  [ "$WITH_DEV_PROXY" = true ] && [ "$WITH_RAG_BACKEND" = true ] && PROFILE_ARGS+=(--profile proxy)
  [ "$WITH_RAG_BACKEND" = true ] && PROFILE_ARGS+=(--profile rag)

  ENV_ARGS=()
  add_env_file() {
    local f="$1"
    if [ -f "$f" ]; then
      ENV_ARGS+=(--env-file "$f")
    else
      echo "Warning: env file not found: $f (run ./docker/scripts/create-env-all.sh first)" >&2
    fi
  }

  add_env_file "$ROOT_DIR/db/.env"
  [ "$WITH_CLASSIFIER" = true ] && add_env_file "$ROOT_DIR/classifier-service/.env"
  if [ "$WITH_OBS" = true ] || [ "$WITH_LOGS" = true ] || [ "$WITH_INFRA" = true ]; then
    add_env_file "$ROOT_DIR/observability/.env"
  fi
  if [ "$WITH_GPU" = true ]; then
    add_env_file "$ROOT_DIR/ollama/.env"
  fi
  [ "$WITH_RAG_BACKEND" = true ] && add_env_file "$ROOT_DIR/rag-service/.env"
  [ "$WITH_RAG_BACKEND" = true ] && add_env_file "$ROOT_DIR/webapp/.env"

  # Same-origin API via nginx: empty NEXT_PUBLIC_API_BASE_URL (overrides webapp/.env :9000 cross-origin).
  if [ "$WITH_DEV_PROXY" = true ] && [ "$WITH_RAG_BACKEND" = true ]; then
    export WEBAPP_NEXT_PUBLIC_API_BASE_URL=""
    export NEXT_PUBLIC_API_BASE_URL=""
  fi

  cd "$DOCKER_DIR"

  if [ "$ACTION" = down ]; then
    DOWN_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}")
    DOWN_ARGS+=(down)
    [ "$WITH_VOLUMES" = true ] && DOWN_ARGS+=(-v)
    docker compose "${DOWN_ARGS[@]}"
    echo "Dev infrastructure stopped."
    exit 0
  fi

  if [ "$ACTION" = build ]; then
    maybe_run_env_setup build
    BUILD_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}")
    BUILD_ARGS+=(build)
    docker compose "${BUILD_ARGS[@]}"
    echo "Dev images built (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, classifier=$WITH_CLASSIFIER, rag_backend=$WITH_RAG_BACKEND, dev_proxy=$WITH_DEV_PROXY, logs=$WITH_LOGS, infra=$WITH_INFRA, mail=$WITH_MAIL)."
    exit 0
  fi

  if [ "$ACTION" = config ]; then
    docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}" config -q
    echo "Dev compose config OK (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, classifier=$WITH_CLASSIFIER, rag_backend=$WITH_RAG_BACKEND, dev_proxy=$WITH_DEV_PROXY, logs=$WITH_LOGS, infra=$WITH_INFRA, mail=$WITH_MAIL)."
    exit 0
  fi

  maybe_run_env_setup up

  SERVICES=(postgres)
  if [ "$WITH_GPU" = true ] && [ "$WITH_NVIDIA" = true ] && [ "$WITH_OLLAMA_REMOTE" != true ]; then
    SERVICES+=(ollama)
  elif [ "$WITH_GPU" = true ] && [ "$WITH_NVIDIA" = false ] && [ "$WITH_OLLAMA_REMOTE" != true ]; then
    echo "Warning: --gpu/--ollama requested but NVIDIA runtime is not available; skipping Ollama container." >&2
  fi
  [ "$WITH_OBS" = true ]        && SERVICES+=(otel-collector jaeger prometheus grafana)
  [ "$WITH_LOGS" = true ]       && SERVICES+=(loki promtail)
  [ "$WITH_INFRA" = true ]      && SERVICES+=(node-exporter)
  [ "$WITH_CLASSIFIER" = true ] && SERVICES+=(classifier-service)
  if [ "$WITH_RAG_BACKEND" = true ]; then
    [ "$WITH_CLASSIFIER" = false ] && SERVICES+=(classifier-service)
    SERVICES+=(backend-dev webapp)
  fi
  if [ "$WITH_DEV_PROXY" = true ] && [ "$WITH_RAG_BACKEND" = true ]; then
    SERVICES+=(reverse-proxy)
  fi
  [ "$WITH_MAIL" = true ] && SERVICES+=(mailpit)

  docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}" up -d "${SERVICES[@]}"

  echo ""
  echo "Dev infrastructure started."
  echo ""
  echo "Services in Docker:"
  for s in "${SERVICES[@]}"; do
    echo "  • $s"
  done
  echo ""
  echo "Run locally (with hot-reload):"
  if [ "$WITH_CLASSIFIER" = true ] || [ "$WITH_RAG_BACKEND" = true ]; then
    echo "  Classifier:  in Docker (compose.dev.yml: Dockerfile.dev + uvicorn --reload). --rag applies this override even without --classifier."
  else
    echo "  Classifier:  cd classifier-service && uvicorn uvicorn_entry:app --reload --reload-dir app --port 8000"
  fi
  if [ "$WITH_RAG_BACKEND" = true ]; then
    echo "  Backend:     in Docker (backend-dev) — hot reload via DevTools. Container port ${SERVER_PORT:-9000}."
    if [ "$WITH_DEV_PROXY" = true ]; then
      _https_port="${REVERSE_PROXY_DEV_HTTPS_PORT:-8443}"
      if [ "${REVERSE_PROXY_ENFORCE_HTTPS:-0}" = "1" ]; then
        echo "               Entry: https://127.0.0.1:${_https_port}/ (self-signed; HTTP :${REVERSE_PROXY_DEV_HTTP_PORT:-8080} redirects)."
      else
        echo "               Entry: http://127.0.0.1:${REVERSE_PROXY_DEV_HTTP_PORT:-8080}/ (HTTPS optional: https://127.0.0.1:${_https_port}/)."
      fi
      echo "               API + BFF via nginx (NEXT_PUBLIC_API_BASE_URL empty = same-origin ${NEXT_PUBLIC_RAG_API_PREFIX:-/api/v5})."
    else
      echo "               API on host: http://127.0.0.1:${BACKEND_PORT:-9000} — set WEBAPP_NEXT_PUBLIC_API_BASE_URL accordingly in webapp/.env for the browser."
    fi
    if [ "$WITH_GPU" = true ] && [ "$WITH_OLLAMA_REMOTE" != true ]; then
      echo "               Ollama: container at http://ollama:11434 (profile ollama). Set OLLAMA_BASE_URL in rag-service/.env to match."
    elif [ "$WITH_GPU" = true ] && [ "$WITH_OLLAMA_REMOTE" = true ]; then
      echo "               Ollama: no local container (--ollama-remote). Use OLLAMA_BASE_URL / SPRING_AI_OLLAMA_BASE_URL in rag-service/.env (e.g. host.docker.internal or LAN)."
    else
      echo "               Ollama URL: rag-service/.env (OLLAMA_BASE_URL); default from compose is host.docker.internal when not using profile ollama."
    fi
    echo "  Webapp:      in Docker (Next.js). Without --proxy: http://127.0.0.1:${WEBAPP_HTTP_PORT:-80}/"
    if [ "$WITH_DEV_PROXY" = true ]; then
      echo "               With --proxy: http://127.0.0.1:${REVERSE_PROXY_DEV_HTTP_PORT:-80}/ (leave WEBAPP_NEXT_PUBLIC_API_BASE_URL empty for same-origin /api)."
    fi
  else
    echo "  Backend:     cd rag-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
    echo "  Webapp:      cd webapp && npm run dev"
  fi
  echo ""
  echo "Postgres available at:  localhost:${POSTGRES_PORT:-5432}"
  if [ "$WITH_GPU" = true ]; then
    echo "Ollama (GPU) available at: localhost:${OLLAMA_PORT:-11434}"
  fi
  [ "$WITH_OBS" = true ] && echo "Grafana available at:   localhost:${GRAFANA_PORT:-3000}"
  [ "$WITH_OBS" = true ] && echo "Jaeger available at:    localhost:${JAEGER_UI_PORT:-16686}"
  [ "$WITH_LOGS" = true ] && echo "Loki / Promtail:        host ports from observability/.env (LOKI_HOST_PORT, PROMTAIL_HOST_PORT)"
  [ "$WITH_INFRA" = true ] && echo "node-exporter: NODE_EXPORTER_HOST_PORT in observability/.env (cAdvisor: optional --profile cadvisor in docker-compose.yml)"
  if [ "$WITH_MAIL" = true ]; then
    echo "Mailpit UI:           http://127.0.0.1:${MAILPIT_HTTP_PORT:-8025}/"
    echo "Mailpit SMTP:         127.0.0.1:${MAILPIT_SMTP_PORT:-1025} (in-stack host: mailpit:1025)"
    if [ "$WITH_RAG_BACKEND" = true ]; then
      if [ "$WITH_DEV_PROXY" = true ]; then
        echo "Auth email links:     set RAG_AUTH_WEBAPP_BASE_URL=http://127.0.0.1 (or https://127.0.0.1:${REVERSE_PROXY_DEV_HTTPS_PORT:-8443}) in rag-service/.env"
      else
        echo "Auth email links:     set RAG_AUTH_WEBAPP_BASE_URL to match how you open the webapp (see WEBAPP_HTTP_PORT in webapp/.env)"
      fi
    else
      echo "Auth email (local backend): set SPRING_MAIL_HOST=127.0.0.1 and SPRING_MAIL_PORT=${MAILPIT_SMTP_PORT:-1025} in rag-service/.env"
    fi
  fi
  exit 0
fi

# ---------- prod ----------
WITH_OBS=false
WITH_OBS_PRIVATE=false
WITH_GPU=false
WITH_LOGS=false
WITH_INFRA=false
WITH_MAIL=false
WITH_VOLUMES=false
WITH_SERVER=false
ALL=false
WITH_CLASSIFIER_GPU=false
WITH_OLLAMA_REMOTE=false
WITH_NVIDIA=false

for arg in "$@"; do
  case "$arg" in
    --all) ALL=true ;;
    --server) WITH_SERVER=true ;;
    --obs) WITH_OBS=true ;;
    --obs-private) WITH_OBS=true; WITH_OBS_PRIVATE=true ;;
    --gpu) WITH_GPU=true ;;
    --ollama) WITH_GPU=true ;;
    --ollama-remote) WITH_OLLAMA_REMOTE=true ;;
    --classifier-gpu) WITH_CLASSIFIER_GPU=true ;;
    --logs) WITH_LOGS=true ;;
    --infra) WITH_INFRA=true ;;
    --mail) WITH_MAIL=true ;;
    --volumes) WITH_VOLUMES=true ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage
      ;;
  esac
done

if [ "$ALL" = true ]; then
  WITH_OBS=true
  WITH_GPU=true
  WITH_LOGS=true
  WITH_INFRA=true
  if [ "$CMD" = down ]; then
    WITH_VOLUMES=true
  fi
fi

if [ "$WITH_SERVER" = true ]; then
  WITH_OLLAMA_REMOTE=true
  if [ "$WITH_MAIL" = true ]; then
    echo "Error: --mail (Mailpit) is not supported with --server (production)." >&2
    exit 1
  fi
fi

has_nvidia_runtime() {
  docker info --format '{{json .Runtimes}}' 2>/dev/null | grep -q '"nvidia"'
}

if has_nvidia_runtime; then
  WITH_NVIDIA=true
else
  if [ "$WITH_GPU" = true ] || [ "$WITH_CLASSIFIER_GPU" = true ]; then
    echo "Warning: NVIDIA runtime not available on this Docker host; falling back to CPU." >&2
  fi
  WITH_NVIDIA=false
fi

CLASSIFIER_CUDA_BASE_IMAGE=nvidia/cuda:12.5.1-cudnn-runtime-ubuntu22.04
if [ "$WITH_CLASSIFIER_GPU" = true ] && [ "$WITH_NVIDIA" = true ]; then
  export CLASSIFIER_PYTHON_BASE_IMAGE="$CLASSIFIER_CUDA_BASE_IMAGE"
  export CLASSIFIER_INSTALL_GPU_EXTRAS=1
fi

COMPOSE_FILES=(-f "docker-compose.yml")
[ "$WITH_OBS" = true ]   && COMPOSE_FILES+=(-f "compose.obs.yml")
COMPOSE_FILES+=(-f "compose.prod.yml")
if [ "$WITH_SERVER" = true ]; then
  COMPOSE_FILES+=(-f "compose.prod-server.yml")
else
  COMPOSE_FILES+=(-f "compose.prod-host-ports.yml")
fi
[ "$WITH_OBS" = true ] && [ "$WITH_OBS_PRIVATE" = true ] && COMPOSE_FILES+=(-f "compose.prod-obs.yml")
if [ "$WITH_NVIDIA" = true ] && [ "$WITH_CLASSIFIER_GPU" = true ]; then
  COMPOSE_FILES+=(-f "compose.gpu.yml")
fi
[ "$WITH_MAIL" = true ] && COMPOSE_FILES+=(-f "compose.prod-mail.yml")

PROFILE_ARGS=()
[ "$WITH_OBS" = true ] && PROFILE_ARGS+=(--profile observability)
[ "$WITH_LOGS" = true ] && PROFILE_ARGS+=(--profile logs)
[ "$WITH_INFRA" = true ] && PROFILE_ARGS+=(--profile infra)
[ "$WITH_MAIL" = true ] && PROFILE_ARGS+=(--profile dev-mail)
if [ "$WITH_GPU" = true ] && [ "$WITH_NVIDIA" = true ] && [ "$WITH_OLLAMA_REMOTE" != true ]; then
  PROFILE_ARGS+=(--profile ollama)
fi

ENV_ARGS=()
add_env_file() {
  local f="$1"
  if [ -f "$f" ]; then
    ENV_ARGS+=(--env-file "$f")
  else
    echo "Warning: env file not found: $f" >&2
  fi
}

add_env_file "$ROOT_DIR/db/.env"
add_env_file "$ROOT_DIR/classifier-service/.env"
add_env_file "$ROOT_DIR/rag-service/.env"
add_env_file "$ROOT_DIR/webapp/.env"
if [ "$WITH_OBS" = true ] || [ "$WITH_LOGS" = true ] || [ "$WITH_INFRA" = true ]; then
  add_env_file "$ROOT_DIR/observability/.env"
fi
if [ "$WITH_GPU" = true ]; then
  add_env_file "$ROOT_DIR/ollama/.env"
fi

cd "$DOCKER_DIR"

if [ "$CMD" = down ]; then
  DOWN_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}" down)
  if [ "$WITH_VOLUMES" = true ]; then
    DOWN_ARGS=( "${DOWN_ARGS[@]}" -v )
  fi
  docker compose "${DOWN_ARGS[@]}"
  echo "Prod local stopped (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, logs=$WITH_LOGS, infra=$WITH_INFRA, mail=$WITH_MAIL, volumes=$WITH_VOLUMES)."
  exit 0
fi

if [ "$CMD" = build ]; then
  maybe_run_env_setup build
  docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}" build
  echo "Prod local images built (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, logs=$WITH_LOGS, infra=$WITH_INFRA, mail=$WITH_MAIL)."
  exit 0
fi

if [ "$CMD" = config ]; then
  docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}" config -q
  echo "Prod compose config OK (server=$WITH_SERVER, obs=$WITH_OBS, obs_private=$WITH_OBS_PRIVATE, ollama_gpu=$WITH_GPU, ollama_remote=$WITH_OLLAMA_REMOTE, logs=$WITH_LOGS, infra=$WITH_INFRA, mail=$WITH_MAIL)."
  exit 0
fi

maybe_run_env_setup up
docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" "${PROFILE_ARGS[@]}" up -d

echo "Prod started (server=$WITH_SERVER, obs=$WITH_OBS, obs_private=$WITH_OBS_PRIVATE, ollama_gpu=$WITH_GPU, ollama_remote=$WITH_OLLAMA_REMOTE, logs=$WITH_LOGS, infra=$WITH_INFRA, mail=$WITH_MAIL)."
echo "Reverse-proxy HTTP: http://127.0.0.1:${REVERSE_PROXY_HTTP_PORT:-80}/ (set REVERSE_PROXY_HTTP_PORT if 80 is not free)."
if [ "$WITH_MAIL" = true ]; then
  echo "Mailpit UI:         http://127.0.0.1:${MAILPIT_HTTP_PORT:-8025}/"
  echo "Mailpit SMTP:       127.0.0.1:${MAILPIT_SMTP_PORT:-1025} (in-stack host: mailpit:1025)"
  echo "Auth email links:   set RAG_AUTH_WEBAPP_BASE_URL=http://127.0.0.1:${REVERSE_PROXY_HTTP_PORT:-80} in rag-service/.env"
fi
if [ "$WITH_OBS" = true ] && [ "$WITH_OBS_PRIVATE" != true ]; then
  echo "Prometheus:         http://127.0.0.1:${PROMETHEUS_PORT:-9090}/"
  echo "Grafana:            http://127.0.0.1:${GRAFANA_PORT:-3000}/"
  echo "Jaeger:             http://127.0.0.1:${JAEGER_UI_PORT:-16686}/"
elif [ "$WITH_OBS" = true ]; then
  echo "Observability UIs are private (--obs-private); use Docker network access or port-forwarding for evidence screenshots."
fi
