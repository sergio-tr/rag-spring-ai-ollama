#!/usr/bin/env bash
# Unified Docker Compose: build | up | down for dev (hybrid infra) or prod-local stacks.
# Same compose file chain and env files as the legacy scripts/up.sh and scripts/down.sh.
#
# Usage (from repo root):
#   ./scripts/docker-compose.sh <build|up|down> <dev|prod> [env options] [stack options]
#
#   down: second arg defaults to prod if omitted (compat with old down.sh).
#
# Env (optional; runs before compose up/build — not before dev down):
#   --env <name>     create-env-* (repeatable): db, obs, rag, classifier, ollama, all
#   --no-env-prompt  skip interactive set-env.sh question
#
# dev:
#   [--all] [--gpu] [--ollama] [--obs] [--classifier] [--logs] [--infra] [--rag] [--down] [--volumes]
#   --rag: Spring backend in Docker (volume + mvn compile + DevTools), in addition to infra.
#   With command "up":   --down / --volumes tear down the dev stack (same as "down dev").
#   With command "down": same flags as "up dev --down".
#   --gpu and --ollama are aliases: both add compose.ollama-gpu.yml (Ollama in Docker with NVIDIA GPU only).
#   --all  = --gpu --obs --classifier --logs --infra --rag
#
# prod:
#   [--all] [--obs] [--gpu] [--ollama] [--logs] [--infra] [--volumes]
#   --volumes only applies to "down prod".
#   --all  = --obs --gpu --logs --infra (and for down: removes volumes)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"

usage() {
  echo "Usage: $0 <build|up|down> <dev|prod> [options]" >&2
  echo "  down: <dev|prod> optional (defaults to prod)" >&2
  echo "  Env: --env <db|obs|rag|classifier|ollama|all> ..., --no-env-prompt" >&2
  echo "  dev:  [--all] [--gpu|--ollama] [--obs] [--classifier] [--logs] [--infra] [--down] [--volumes]" >&2
  echo "  prod: [--all] [--obs] [--gpu|--ollama] [--logs] [--infra] [--volumes]" >&2
  exit 1
}

CMD="${1:-}"
case "$CMD" in
  build|up|down) shift ;;
  *)
    echo "Usage: $0 <build|up|down> <dev|prod> ..." >&2
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
    *)
      echo "Unknown --env component: $c (use: db, obs, rag, classifier, ollama, all)" >&2
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
  WITH_LOGS=false
  WITH_INFRA=false
  WITH_RAG_BACKEND=false
  ACTION=up
  WITH_VOLUMES=false
  ALL=false

  if [ "$CMD" = down ]; then
    ACTION=down
  elif [ "$CMD" = build ]; then
    ACTION=build
  else
    ACTION=up
  fi

  for arg in "$@"; do
    case "$arg" in
      --all)        ALL=true ;;
      --gpu)        WITH_GPU=true ;;
      --ollama)     WITH_GPU=true ;;
      --obs)        WITH_OBS=true ;;
      --classifier) WITH_CLASSIFIER=true ;;
      --logs)       WITH_LOGS=true ;;
      --infra)      WITH_INFRA=true ;;
      --rag)        WITH_RAG_BACKEND=true ;;
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

  if [ "$ALL" = true ]; then
    WITH_GPU=true
    WITH_OBS=true
    WITH_CLASSIFIER=true
    WITH_LOGS=true
    WITH_INFRA=true
    WITH_RAG_BACKEND=true
    if [ "$ACTION" = down ]; then
      WITH_VOLUMES=true
    fi
  fi

  COMPOSE_FILES=(-f "docker-compose.yml")
  # compose.dev.yml: classifier hot-reload and/or backend-dev definition (--rag)
  if [ "$WITH_CLASSIFIER" = true ] || [ "$WITH_RAG_BACKEND" = true ]; then
    COMPOSE_FILES+=(-f "compose.dev.yml")
  fi
  [ "$WITH_OBS" = true ]        && COMPOSE_FILES+=(-f "compose.obs.yml")
  [ "$WITH_LOGS" = true ]       && COMPOSE_FILES+=(-f "compose.logs.yml")
  [ "$WITH_INFRA" = true ]      && COMPOSE_FILES+=(-f "compose.infra.yml")
  [ "$WITH_GPU" = true ]        && COMPOSE_FILES+=(-f "compose.ollama-gpu.yml")
  [ "$WITH_RAG_BACKEND" = true ] && [ "$WITH_OBS" = true ] && COMPOSE_FILES+=(-f "compose.rag-dev-obs.yml")

  ENV_ARGS=()
  add_env_file() {
    local f="$1"
    if [ -f "$f" ]; then
      ENV_ARGS+=(--env-file "$f")
    else
      echo "Warning: env file not found: $f (run scripts/create-env-all.sh first)" >&2
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

  cd "$DOCKER_DIR"

  if [ "$ACTION" = down ]; then
    DOWN_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}")
    [ "$WITH_RAG_BACKEND" = true ] && DOWN_ARGS+=(--profile rag)
    DOWN_ARGS+=(down)
    [ "$WITH_VOLUMES" = true ] && DOWN_ARGS+=(-v)
    docker compose "${DOWN_ARGS[@]}"
    echo "Dev infrastructure stopped."
    exit 0
  fi

  if [ "$ACTION" = build ]; then
    maybe_run_env_setup build
    BUILD_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}")
    [ "$WITH_RAG_BACKEND" = true ] && BUILD_ARGS+=(--profile rag)
    BUILD_ARGS+=(build)
    docker compose "${BUILD_ARGS[@]}"
    echo "Dev images built (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, classifier=$WITH_CLASSIFIER, rag_backend=$WITH_RAG_BACKEND, logs=$WITH_LOGS, infra=$WITH_INFRA)."
    exit 0
  fi

  maybe_run_env_setup up

  PROFILE_ARGS=()
  [ "$WITH_RAG_BACKEND" = true ] && PROFILE_ARGS+=(--profile rag)

  SERVICES=(postgres)
  if [ "$WITH_GPU" = true ]; then
    SERVICES+=(ollama)
  fi
  [ "$WITH_OBS" = true ]        && SERVICES+=(otel-collector jaeger prometheus grafana)
  [ "$WITH_LOGS" = true ]       && SERVICES+=(loki promtail)
  [ "$WITH_INFRA" = true ]      && SERVICES+=(node-exporter cadvisor)
  [ "$WITH_CLASSIFIER" = true ] && SERVICES+=(classifier-service)
  if [ "$WITH_RAG_BACKEND" = true ]; then
    [ "$WITH_CLASSIFIER" = false ] && SERVICES+=(classifier-service)
    SERVICES+=(backend-dev)
  fi

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
    echo "  Classifier:  cd classifier-service && uvicorn main:app --reload --reload-dir app --port 8000"
  fi
  if [ "$WITH_RAG_BACKEND" = true ]; then
    echo "  Backend:     in Docker (backend-dev) — changes in src/ recompile and DevTools restart. Port ${BACKEND_PORT:-9000}."
    if [ "$WITH_GPU" = true ]; then
      echo "               Ollama: container → set SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434 in rag-service/.env"
    else
      echo "               Ollama on HOST: run Ollama locally and use http://host.docker.internal:11434 (see docs/DEV_STACK_OBS_Y_OLLAMA_HOST.md)"
    fi
  else
    echo "  Backend:     cd rag-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
  fi
  echo "  Frontend:    cd frontend && npm run dev"
  echo ""
  echo "Postgres available at:  localhost:${POSTGRES_PORT:-5432}"
  if [ "$WITH_GPU" = true ]; then
    echo "Ollama (GPU) available at: localhost:${OLLAMA_PORT:-11434}"
  fi
  [ "$WITH_OBS" = true ] && echo "Grafana available at:   localhost:${GRAFANA_PORT:-3000}"
  [ "$WITH_OBS" = true ] && echo "Jaeger available at:    localhost:${JAEGER_UI_PORT:-16686}"
  [ "$WITH_LOGS" = true ] && echo "Loki / Promtail:        host ports from observability/.env (LOKI_HOST_PORT, PROMTAIL_HOST_PORT)"
  [ "$WITH_INFRA" = true ] && echo "node-exporter / cAdvisor: NODE_EXPORTER_HOST_PORT, CADVISOR_HOST_PORT in observability/.env"
  exit 0
fi

# ---------- prod ----------
WITH_OBS=false
WITH_GPU=false
WITH_LOGS=false
WITH_INFRA=false
WITH_VOLUMES=false
ALL=false

for arg in "$@"; do
  case "$arg" in
    --all) ALL=true ;;
    --obs) WITH_OBS=true ;;
    --gpu) WITH_GPU=true ;;
    --ollama) WITH_GPU=true ;;
    --logs) WITH_LOGS=true ;;
    --infra) WITH_INFRA=true ;;
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

COMPOSE_FILES=(-f "docker-compose.yml")
[ "$WITH_OBS" = true ]   && COMPOSE_FILES+=(-f "compose.obs.yml")
[ "$WITH_LOGS" = true ]  && COMPOSE_FILES+=(-f "compose.logs.yml")
[ "$WITH_INFRA" = true ] && COMPOSE_FILES+=(-f "compose.infra.yml")
[ "$WITH_GPU" = true ]   && COMPOSE_FILES+=(-f "compose.ollama-gpu.yml")
COMPOSE_FILES+=(-f "compose.prod.yml")

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
if [ "$WITH_OBS" = true ] || [ "$WITH_LOGS" = true ] || [ "$WITH_INFRA" = true ]; then
  add_env_file "$ROOT_DIR/observability/.env"
fi
if [ "$WITH_GPU" = true ]; then
  add_env_file "$ROOT_DIR/ollama/.env"
fi

cd "$DOCKER_DIR"

if [ "$CMD" = down ]; then
  DOWN_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" down)
  if [ "$WITH_VOLUMES" = true ]; then
    DOWN_ARGS=( "${DOWN_ARGS[@]}" -v )
  fi
  docker compose "${DOWN_ARGS[@]}"
  echo "Prod local stopped (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, logs=$WITH_LOGS, infra=$WITH_INFRA, volumes=$WITH_VOLUMES)."
  exit 0
fi

if [ "$CMD" = build ]; then
  maybe_run_env_setup build
  docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" build
  echo "Prod local images built (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, logs=$WITH_LOGS, infra=$WITH_INFRA)."
  exit 0
fi

maybe_run_env_setup up
docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" up -d

echo "Prod local started (obs=$WITH_OBS, ollama_gpu=$WITH_GPU, logs=$WITH_LOGS, infra=$WITH_INFRA)."
