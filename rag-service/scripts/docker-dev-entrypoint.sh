#!/usr/bin/env bash
# Hot-reload en contenedor: compila al cambiar fuentes; Spring Boot DevTools reinicia al actualizarse target/classes.
# El polling por hash funciona también con volúmenes montados desde Windows (inotify suele fallar).
set -euo pipefail
cd /app

if [ -f mvnw ]; then
  sed -i 's/\r$//' mvnw 2>/dev/null || true
  chmod +x mvnw
fi

echo "[rag-service docker-dev] Initial compile..."
./mvnw -q compile -Dmaven.test.skip=true

# Intervalo entre comprobaciones (segundos). Subir en repos muy grandes si hace falta.
POLL_INTERVAL="${RAG_DEV_POLL_INTERVAL:-2}"

watch_compile() {
  local prev=""
  while sleep "$POLL_INTERVAL"; do
    h=""
    if [ -d /app/src ]; then
      h="$(find /app/src -type f \( -name '*.java' -o -name '*.xml' -o -name '*.properties' -o -name '*.yml' -o -name '*.yaml' \) -print0 2>/dev/null | sort -z | xargs -0 md5sum 2>/dev/null | md5sum | awk '{print $1}')"
    fi
    [ -z "$h" ] && continue
    if [ -n "$prev" ] && [ "$h" != "$prev" ]; then
      echo "[rag-service docker-dev] Sources changed, recompiling..."
      ./mvnw -q compile -Dmaven.test.skip=true || echo "[rag-service docker-dev] compile failed (fix errors and save again)"
    fi
    prev="$h"
  done
}

watch_compile &
WATCH_PID=$!
cleanup() { kill "$WATCH_PID" 2>/dev/null || true; }
trap cleanup EXIT

exec ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring.devtools.restart.enabled=true
