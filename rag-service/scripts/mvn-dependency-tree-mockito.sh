#!/usr/bin/env bash
# Shows the Maven dependency subtree for org.mockito:mockito-core under rag-service.
# Bash does not split on ':' in -D values (unlike typical PowerShell invocation quirks).
# Usage from repo root: ./rag-service/scripts/mvn-dependency-tree-mockito.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_SERVICE="$(cd "${HERE}/.." && pwd)"
cd "$RAG_SERVICE"
exec ./mvnw -B dependency:tree -Dverbose=false '-Dincludes=org.mockito:mockito-core'
