#!/usr/bin/env bash
# ARCHIVED (2026-05): one-off migration script — do not run in CI. Target packages (e.g. application.service.query.legacy) were removed by legacy-zero L1.
# Agent A1: move com.uniovi.rag.service.. -> application.service.. (except service.model)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MAIN_BASE="${REPO_ROOT}/rag-service/src/main/java/com/uniovi/rag"
TEST_BASE="${REPO_ROOT}/rag-service/src/test/java/com/uniovi/rag"

log() { printf '[migrate-a1] %s\n' "$*"; }

ensure_dir() {
  mkdir -p "$1"
}

git_mv_dir() {
  local src="$1"
  local dst="$2"
  if [[ ! -d "$src" ]]; then
    return 0
  fi
  ensure_dir "$(dirname "$dst")"
  if [[ -d "$dst" ]]; then
    # Merge contents
    shopt -s nullglob
    for f in "$src"/*; do
      local base
      base="$(basename "$f")"
      if [[ -e "$dst/$base" ]]; then
        if [[ "$base" == "package-info.java" ]]; then
          git rm -f "$f" 2>/dev/null || rm -f "$f"
        else
          echo "error: merge conflict $dst/$base" >&2
          exit 1
        fi
      else
        git mv "$f" "$dst/$base"
      fi
    done
    shopt -u nullglob
    rmdir "$src" 2>/dev/null || true
  else
    git mv "$src" "$dst"
  fi
}

git_mv_file() {
  local src="$1"
  local dst="$2"
  [[ -f "$src" ]] || return 0
  ensure_dir "$(dirname "$dst")"
  if [[ -f "$dst" ]]; then
    git rm -f "$src" 2>/dev/null || rm -f "$src"
    return 0
  fi
  git mv "$src" "$dst"
}

move_tree() {
  local rel="$1"   # e.g. service/evaluation/preset
  local target="$2" # e.g. application/service/evaluation/preset
  git_mv_dir "${MAIN_BASE}/${rel}" "${MAIN_BASE}/${target}"
  if [[ -d "${TEST_BASE}/${rel}" ]]; then
    git_mv_dir "${TEST_BASE}/${rel}" "${TEST_BASE}/${target}"
  fi
}

cd "${REPO_ROOT}"

log "Phase 1: git mv directories (deepest first)"

move_tree "service/evaluation/preset" "application/service/evaluation/preset"
move_tree "service/evaluation/baseline" "application/service/evaluation/baseline"

# evaluation root Java files (not subdirs)
if [[ -d "${MAIN_BASE}/service/evaluation" ]]; then
  ensure_dir "${MAIN_BASE}/application/service/evaluation"
  shopt -s nullglob
  for f in "${MAIN_BASE}/service/evaluation"/*.java; do
    git_mv_file "$f" "${MAIN_BASE}/application/service/evaluation/$(basename "$f")"
  done
  shopt -u nullglob
fi
if [[ -d "${TEST_BASE}/service/evaluation" ]]; then
  ensure_dir "${TEST_BASE}/application/service/evaluation"
  shopt -s nullglob
  for f in "${TEST_BASE}/service/evaluation"/*.java; do
    git_mv_file "$f" "${TEST_BASE}/application/service/evaluation/$(basename "$f")"
  done
  shopt -u nullglob
fi

move_tree "service/async/lab" "application/service/evaluation/async"
move_tree "service/async/chat" "application/service/chat/async"
move_tree "service/async/account" "application/service/account/async"
move_tree "service/query/pipeline" "application/service/query/pipeline"
move_tree "service/query" "application/service/query/legacy"
move_tree "service/async" "application/service/async"
move_tree "service/admin" "application/service/admin"
move_tree "service/classifier" "application/service/classifier"
move_tree "service/project" "application/service/project"
move_tree "service/preset" "application/service/preset"
move_tree "service/document" "application/service/knowledge/document"
move_tree "service/retriever" "application/service/runtime/retrieval"
move_tree "service/ranker" "application/service/runtime/ranking"
move_tree "service/reasoning" "application/service/runtime/reasoning"
move_tree "service/analyser" "application/service/runtime/query/analyser"
move_tree "service/expand" "application/service/runtime/query/expand"
move_tree "service/extraction" "application/service/runtime/document/extraction"
move_tree "service/guard" "application/service/runtime/query/guard"
move_tree "service/postretrieval" "application/service/runtime/retrieval/post"
move_tree "service/config" "application/service/config"
move_tree "service/account" "application/service/account"
move_tree "service/chat" "application/service/chat"

# Remove empty dirs (keep service/model)
rm -rf "${MAIN_BASE}/service/evaluation/mvp" 2>/dev/null || true
if [[ -f "${MAIN_BASE}/service/package-info.java" ]]; then
  git rm -f "${MAIN_BASE}/service/package-info.java" 2>/dev/null || rm -f "${MAIN_BASE}/service/package-info.java"
fi

log "Phase 2: package/import replacement in rag-service (Python, longest-first; skips service.model)"

python3 << 'PY'
import os
root = "rag-service"
pairs = [
    ("com.uniovi.rag.service.evaluation.preset", "com.uniovi.rag.application.service.evaluation.preset"),
    ("com.uniovi.rag.service.evaluation.baseline", "com.uniovi.rag.application.service.evaluation.baseline"),
    ("com.uniovi.rag.service.async.lab", "com.uniovi.rag.application.service.evaluation.async"),
    ("com.uniovi.rag.service.async.chat", "com.uniovi.rag.application.service.chat.async"),
    ("com.uniovi.rag.service.async.account", "com.uniovi.rag.application.service.account.async"),
    ("com.uniovi.rag.service.query.pipeline", "com.uniovi.rag.application.service.query.pipeline"),
    ("com.uniovi.rag.service.query", "com.uniovi.rag.application.service.query.legacy"),
    ("com.uniovi.rag.service.postretrieval", "com.uniovi.rag.application.service.runtime.retrieval.post"),
    ("com.uniovi.rag.service.evaluation", "com.uniovi.rag.application.service.evaluation"),
    ("com.uniovi.rag.service.retriever", "com.uniovi.rag.application.service.runtime.retrieval"),
    ("com.uniovi.rag.service.reasoning", "com.uniovi.rag.application.service.runtime.reasoning"),
    ("com.uniovi.rag.service.extraction", "com.uniovi.rag.application.service.runtime.document.extraction"),
    ("com.uniovi.rag.service.document", "com.uniovi.rag.application.service.knowledge.document"),
    ("com.uniovi.rag.service.analyser", "com.uniovi.rag.application.service.runtime.query.analyser"),
    ("com.uniovi.rag.service.classifier", "com.uniovi.rag.application.service.classifier"),
    ("com.uniovi.rag.service.async", "com.uniovi.rag.application.service.async"),
    ("com.uniovi.rag.service.project", "com.uniovi.rag.application.service.project"),
    ("com.uniovi.rag.service.preset", "com.uniovi.rag.application.service.preset"),
    ("com.uniovi.rag.service.ranker", "com.uniovi.rag.application.service.runtime.ranking"),
    ("com.uniovi.rag.service.account", "com.uniovi.rag.application.service.account"),
    ("com.uniovi.rag.service.config", "com.uniovi.rag.application.service.config"),
    ("com.uniovi.rag.service.admin", "com.uniovi.rag.application.service.admin"),
    ("com.uniovi.rag.service.expand", "com.uniovi.rag.application.service.runtime.query.expand"),
    ("com.uniovi.rag.service.guard", "com.uniovi.rag.application.service.runtime.query.guard"),
    ("com.uniovi.rag.service.chat", "com.uniovi.rag.application.service.chat"),
]
changed = 0
for dirpath, _, files in os.walk(root):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(dirpath, fn)
        with open(path, encoding="utf-8") as f:
            content = f.read()
        new = content
        for old, newp in pairs:
            new = new.replace(old, newp)
        if new != content:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new)
            changed += 1
print(f"updated {changed} files")
PY

log "Done git mv + package replace"
