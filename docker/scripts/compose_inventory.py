#!/usr/bin/env python3
"""
Inventory all Docker Compose files under docker/ and list services with image vs build.

Usage:
  ./docker/scripts/compose_inventory.py [--root DIR] [--format text|markdown]

Does not validate policy; use compose_guard.py for that.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    import yaml
except ImportError as e:  # pragma: no cover
    print("PyYAML is required: pip install pyyaml", file=sys.stderr)
    raise SystemExit(1) from e


def service_summary(svc: dict) -> tuple[str, str]:
    """Return (kind, detail) where kind is image|build|partial|empty."""
    if not isinstance(svc, dict):
        return "empty", ""
    if "image" in svc:
        return "image", str(svc["image"])
    if "build" in svc:
        b = svc["build"]
        if isinstance(b, str):
            return "build", f"context={b!r} (implicit dockerfile)"
        if isinstance(b, dict):
            ctx = b.get("context", "")
            df = b.get("dockerfile", "")
            return "build", f"context={ctx!r} dockerfile={df!r}"
        return "build", repr(b)
    return "partial", "(override only — no image/build in this fragment)"


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, default=None, help="Repository root")
    p.add_argument("--format", choices=("text", "markdown"), default="text")
    args = p.parse_args()
    root = args.root or Path(__file__).resolve().parent.parent.parent
    docker_dir = root / "docker"
    if not docker_dir.is_dir():
        print(f"Error: {docker_dir} not found", file=sys.stderr)
        return 1

    files = sorted(
        list(docker_dir.glob("docker-compose*.yml")) + list(docker_dir.glob("compose*.yml"))
    )
    rows: list[tuple[str, str, str, str]] = []

    for path in files:
        try:
            data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except Exception as e:
            rows.append((path.name, "—", "error", str(e)))
            continue
        services = data.get("services") or {}
        if not services:
            rows.append((path.name, "—", "empty", "no services key"))
            continue
        for name, spec in services.items():
            kind, detail = service_summary(spec if isinstance(spec, dict) else {})
            rows.append((path.name, name, kind, detail))

    if args.format == "markdown":
        print("| Compose file | Service | Kind | Detail |")
        print("| --- | --- | --- | --- |")
        for f, s, k, d in rows:
            esc = d.replace("|", r"\|")
            print(f"| {f} | {s} | {k} | {esc} |")
    else:
        prev = None
        for f, s, k, d in rows:
            if f != prev:
                print(f"\n== {f} ==")
                prev = f
            print(f"  {s:30}  {k:10}  {d}")

    print(f"\nTotal: {len(files)} compose file(s), {len(rows)} service row(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
