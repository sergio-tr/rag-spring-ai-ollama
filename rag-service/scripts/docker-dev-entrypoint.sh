#!/usr/bin/env bash
# Hot-reload at container: compiles when sources change; Spring Boot DevTools restarts when target/classes is updated.
# The polling by hash works also with mounted volumes from Windows (inotify usually fails).
#
# Compilation at startup (slow): by default it is SKIPPED if target/classes/com/uniovi/Application.class exists
# (e.g. mvn compile on the host or a previous startup with the rag_m2_cache volume).
#   RAG_DEV_FORCE_INITIAL_COMPILE=1  → always compile at startup (old behavior).
#   RAG_DEV_SKIP_INITIAL_COMPILE=1    → do not compile at startup; if there are no classes, compile once anyway.
set -euo pipefail
cd /app

if [ -f mvnw ]; then
  sed -i 's/\r$//' mvnw 2>/dev/null || true
  chmod +x mvnw
fi

# Any .class under target/classes (classpath check — not sufficient alone)
has_compiled_classes() {
  [ -d target/classes ] || return 1
  test -n "$(find target/classes -type f -name '*.class' 2>/dev/null | head -n 1)"
}

# Spring Boot needs the @SpringBootApplication class on the classpath; a stray .class elsewhere
# is not enough (e.g. partial mvn clean on the host bind-mounted over /app).
has_boot_application_class() {
  [ -f target/classes/com/uniovi/Application.class ]
}

need_initial_compile=true
if [ "${RAG_DEV_FORCE_INITIAL_COMPILE:-}" = "1" ] || [ "${RAG_DEV_FORCE_INITIAL_COMPILE:-}" = "true" ]; then
  need_initial_compile=true
elif [ "${RAG_DEV_SKIP_INITIAL_COMPILE:-}" = "1" ] || [ "${RAG_DEV_SKIP_INITIAL_COMPILE:-}" = "true" ]; then
  if has_boot_application_class; then
    need_initial_compile=false
  else
    echo "[rag-service docker-dev] RAG_DEV_SKIP_INITIAL_COMPILE but Application.class is missing — compiling once."
  fi
elif has_boot_application_class; then
  need_initial_compile=false
elif has_compiled_classes; then
  echo "[rag-service docker-dev] target/classes has .class files but com/uniovi/Application.class is missing — compiling."
fi

if [ "$need_initial_compile" = true ]; then
  echo "[rag-service docker-dev] Initial compile..."
  ./mvnw -q compile -Dmaven.test.skip=true
else
  echo "[rag-service docker-dev] Skipping initial compilation (Application.class present). Force: RAG_DEV_FORCE_INITIAL_COMPILE=1"
fi

# Interval between checks (seconds). Increase for very large repos if needed.
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

: "${SPRING_PROFILES_ACTIVE:=dev}"
exec ./mvnw spring-boot:run \
  -Dspring.devtools.restart.enabled=true
