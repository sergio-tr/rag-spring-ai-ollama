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

# Any .class under target/classes (classpath check - not sufficient alone)
has_compiled_classes() {
  [ -d target/classes ] || return 1
  test -n "$(find target/classes -type f -name '*.class' 2>/dev/null | head -n 1)"
}

# Spring Boot needs the @SpringBootApplication class on the classpath; a stray .class elsewhere
# is not enough (e.g. partial mvn clean on the host bind-mounted over /app).
has_boot_application_class() {
  [ -f target/classes/com/uniovi/Application.class ]
}

compiled_class_count() {
  find target/classes -type f -name '*.class' 2>/dev/null | wc -l | tr -d ' '
}

has_healthy_compile_tree() {
  has_boot_application_class || return 1
  local count
  count="$(compiled_class_count)"
  [ "${count:-0}" -ge 500 ]
}

need_initial_compile=true
if [ "${RAG_DEV_FORCE_INITIAL_COMPILE:-}" = "1" ] || [ "${RAG_DEV_FORCE_INITIAL_COMPILE:-}" = "true" ]; then
  need_initial_compile=true
elif [ "${RAG_DEV_SKIP_INITIAL_COMPILE:-}" = "1" ] || [ "${RAG_DEV_SKIP_INITIAL_COMPILE:-}" = "true" ]; then
  if has_healthy_compile_tree; then
    need_initial_compile=false
  else
    echo "[rag-service docker-dev] RAG_DEV_SKIP_INITIAL_COMPILE but compile tree looks incomplete - compiling once."
  fi
elif has_healthy_compile_tree; then
  need_initial_compile=false
elif has_boot_application_class; then
  echo "[rag-service docker-dev] Application.class present but only $(compiled_class_count) .class files - partial target/classes; compiling."
elif has_compiled_classes; then
  echo "[rag-service docker-dev] target/classes has .class files but com/uniovi/Application.class is missing - compiling."
fi

if [ "$need_initial_compile" = true ]; then
  echo "[rag-service docker-dev] Initial compile..."
  ./mvnw -q compile -Dmaven.test.skip=true
else
  echo "[rag-service docker-dev] Skipping initial compilation ($(compiled_class_count) .class files). Force: RAG_DEV_FORCE_INITIAL_COMPILE=1"
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

# DevTools restarts on target/classes changes; with a bind-mounted target/ a partial clean on the
# host can leave stale .class files and trigger NoClassDefFoundError crash-loops. After refactors or
# mvn clean on the host, run ./mvnw clean compile (or docker/scripts/dev-smoke-bootstrap.sh).
DEVTOOLS_RESTART=true
if [ "${RAG_DEV_DISABLE_DEVTOOLS:-}" = "1" ] || [ "${RAG_DEV_DISABLE_DEVTOOLS:-}" = "true" ]; then
  DEVTOOLS_RESTART=false
fi

exec ./mvnw spring-boot:run \
  -Dspring.devtools.restart.enabled="${DEVTOOLS_RESTART}"
