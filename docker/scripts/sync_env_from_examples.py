#!/usr/bin/env python3
"""
Merge missing keys from each module's .env.example into the corresponding .env.

- If .env does not exist, copy .env.example to .env (same behaviour as create-env-*.sh).
- If .env exists, append any KEY=value from .env.example whose KEY is not yet defined in .env
  (first occurrence wins; does not overwrite existing values).

Comments and blank lines in .env.example are skipped for key extraction but preserved when
copying a brand-new file.

Usage:
  ./docker/scripts/sync_env_from_examples.py [--dry-run] [--root DIR]

Exit code: 0 on success, 1 on missing .env.example or I/O error.
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

# Repository-relative paths: (subdir containing .env files, optional label)
COMPONENTS: list[tuple[str, str]] = [
    ("db", "db"),
    ("classifier-service", "classifier-service"),
    ("rag-service", "rag-service"),
    ("webapp", "webapp"),
    ("ollama", "ollama"),
    ("observability", "observability"),
]

KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=")


def parse_keys_and_lines(path: Path) -> tuple[dict[str, str], list[str]]:
    """Return mapping key -> first line defining it, and raw lines."""
    keys: dict[str, str] = {}
    lines: list[str] = path.read_text(encoding="utf-8", errors="replace").splitlines()
    for line in lines:
        m = KEY_RE.match(line.strip())
        if m and not line.strip().startswith("#"):
            k = m.group(1)
            if k not in keys:
                keys[k] = line
    return keys, lines


def extract_example_entries(example_path: Path) -> list[str]:
    """Return non-comment, non-blank KEY=value lines from .env.example."""
    out: list[str] = []
    for line in example_path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if KEY_RE.match(stripped):
            out.append(line)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=None,
        help="Repository root (default: parent of docker/scripts)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print actions without writing files",
    )
    args = parser.parse_args()
    root = args.root
    if root is None:
        root = Path(__file__).resolve().parent.parent.parent

    dry = args.dry_run
    updated = 0

    for _label, sub in COMPONENTS:
        ex = root / sub / ".env.example"
        env_path = root / sub / ".env"
        if not ex.is_file():
            print(f"Error: missing {ex}", file=sys.stderr)
            return 1

        if not env_path.is_file():
            if dry:
                print(f"[dry-run] would copy {ex} -> {env_path}")
            else:
                shutil.copyfile(ex, env_path)
                print(f"Created {env_path} from {ex}")
            updated += 1
            continue

        existing_keys, _ = parse_keys_and_lines(env_path)
        to_append: list[str] = []
        for line in extract_example_entries(ex):
            m = KEY_RE.match(line.strip())
            if not m:
                continue
            key = m.group(1)
            if key not in existing_keys:
                to_append.append(line)

        if not to_append:
            print(f"{env_path}: already contains all keys from {ex.name}")
            continue

        block = "\n".join(to_append)
        if dry:
            print(f"[dry-run] would append to {env_path}:\n{block}\n")
        else:
            text = env_path.read_text(encoding="utf-8", errors="replace")
            if text and not text.endswith("\n"):
                text += "\n"
            text += "\n# Keys merged from .env.example (sync_env_from_examples.py)\n"
            text += block + "\n"
            env_path.write_text(text, encoding="utf-8")
            print(f"{env_path}: appended {len(to_append)} key(s) from {ex.name}")
        updated += 1

    print(f"Done ({updated} file(s) touched or reported).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
