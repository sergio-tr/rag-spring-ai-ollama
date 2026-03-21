#!/usr/bin/env bash
# Start stack with docker compose. Usage: ./up.sh [gpu|obs|'']
#   no args: docker compose up -d
#   gpu: add compose.ollama-gpu.yml (Ollama with GPU)
#   obs: add compose.obs.yml (Jaeger, Prometheus, Grafana)
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_ARGS="-f docker-compose.yml"
case "${1:-}" in
  gpu)  COMPOSE_ARGS="$COMPOSE_ARGS -f compose.ollama-gpu.yml" ;;
  obs)  COMPOSE_ARGS="$COMPOSE_ARGS -f compose.obs.yml" ;;
  "")   ;;
  *)    echo "Usage: $0 [gpu|obs]"; exit 1 ;;
esac
docker compose $COMPOSE_ARGS up -d
