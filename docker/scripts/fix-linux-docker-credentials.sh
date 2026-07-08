#!/usr/bin/env bash
# Remove Docker Desktop credential helpers from ~/.docker/config.json on Linux servers.
# Docker Desktop helpers (docker-credential-desktop.exe) break `docker build` on native Linux.
set -euo pipefail

CONFIG="${DOCKER_CONFIG:-$HOME/.docker}/config.json"

if [ ! -f "$CONFIG" ]; then
  echo "No Docker config at $CONFIG (OK for public image pulls)."
  exit 0
fi

if ! grep -qE 'docker-credential-desktop|credsStore|credHelpers' "$CONFIG" 2>/dev/null; then
  echo "Docker config does not reference Desktop credential helpers (OK)."
  exit 0
fi

python3 - "$CONFIG" <<'PY'
import json
import shutil
import sys
from pathlib import Path

path = Path(sys.argv[1])
raw = path.read_text(encoding="utf-8")
try:
    data = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"::warning::Could not parse {path}: {exc}", file=sys.stderr)
    sys.exit(0)

def is_desktop_helper(value) -> bool:
    if not isinstance(value, str):
        return False
    lowered = value.lower()
    return "desktop" in lowered or "docker-credential-desktop" in lowered

changed = False
for key in ("credsStore", "credHelpers"):
    if key not in data:
        continue
    val = data[key]
    if is_desktop_helper(val):
        del data[key]
        changed = True
    elif isinstance(val, dict):
        filtered = {k: v for k, v in val.items() if not is_desktop_helper(v)}
        if len(filtered) != len(val):
            changed = True
            if filtered:
                data[key] = filtered
            else:
                del data[key]

if not changed:
    print("Docker config unchanged (no Desktop credential helper detected).")
    sys.exit(0)

backup = path.with_suffix(path.suffix + ".bak")
shutil.copy2(path, backup)
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print(f"Removed Docker Desktop credential helper from {path} (backup: {backup}).")
PY
