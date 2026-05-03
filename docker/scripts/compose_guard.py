#!/usr/bin/env python3
"""
Policy guard for Docker Compose files (incremental migration helper).

Rules (all are errors; exit code 1 if any violation):

1. **image:** — forbidden (use local build + Dockerfile per repo policy).
2. **build:** — if present as a mapping, **context** is required. If **dockerfile** is missing,
   only the short form ``build: <path>`` (string) is accepted as an implicit Dockerfile at context root.
3. **environment** — values must be parameterized with ``${...}`` or be a safe literal
   (booleans, small non-negative integers, empty).
4. **ports** — host side of each port mapping must reference ``${`` (no fixed host port literals).
5. **expose** — each entry must not be a bare numeric port (must use indirection in future; flagged).
6. **healthcheck** — ``interval``, ``timeout``, ``retries``, ``start_period`` must use ``${`` or
   be explicitly listed in HEALTHCHECK_DURATION_ALLOWLIST for grandfathering during migration.
7. **healthcheck.test** — warn-level entries for obvious literal URLs/IPs (informational in same output).

This script analyzes **each YAML file independently** (not merged stacks). Override-only fragments
without ``image``/``build`` are expected for many services.

Usage:
  ./docker/scripts/compose_guard.py [--root DIR] [--json] [--only-rules RULE1,RULE2,...]

``--only-rules`` restricts which violation types fail the run (CI uses structural rules while
``environment_literal`` / ``healthcheck_*`` migration is ongoing). Default: all rules.

Requires: PyYAML
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError as e:  # pragma: no cover
    print("PyYAML is required: pip install pyyaml", file=sys.stderr)
    raise SystemExit(1) from e

# Safe environment literals (string form) during migration.
SAFE_ENV_STRINGS = frozenset(
    {
        "",
        "true",
        "false",
        "True",
        "False",
        "none",
        "null",
        "docker",
        "infra",
        "dev",
        "prod",
    }
)

# Durations seen in healthchecks — allowed until migrated to env (explicit grandfathering).
HEALTHCHECK_DURATION_ALLOWLIST = frozenset(
    {
        "5s",
        "8s",
        "10s",
        "12s",
        "20s",
        "60s",
        "90s",
        "1s",
        "2s",
        "30s",
    }
)

PARAM_RE = re.compile(r"\$\{[^}]+\}")


def is_parameterized(value: str) -> bool:
    return PARAM_RE.search(value) is not None


def check_env_value(file: str, svc: str, key: str, raw: object) -> list[dict]:
    out: list[dict] = []
    if raw is None:
        return out
    if isinstance(raw, (bool, int, float)):
        return out
    if not isinstance(raw, str):
        raw = str(raw)
    s = raw
    if is_parameterized(s):
        return out
    if s in SAFE_ENV_STRINGS:
        return out
    if s.isdigit() or (s.startswith("-") and s[1:].isdigit()):
        return out
    # Allow simple semver-like or model ids without spaces
    if re.fullmatch(r"[A-Za-z0-9._:\-/+]+", s) and len(s) < 120:
        # Likely still a literal (model name, profile list) — flag
        pass
    out.append(
        {
            "file": file,
            "service": svc,
            "rule": "environment_literal",
            "detail": f"{key}={s!r} (use ${'{VAR}'} or ${'{VAR:-default}'})",
        }
    )
    return out


def parse_env_list(entries: list) -> list[tuple[str, str]]:
    """Parse ['K=V', ...] form."""
    pairs: list[tuple[str, str]] = []
    for e in entries:
        if not isinstance(e, str) or "=" not in e:
            continue
        k, v = e.split("=", 1)
        pairs.append((k.strip(), v))
    return pairs


def check_environment(file: str, svc: str, env) -> list[dict]:
    out: list[dict] = []
    if env is None:
        return out
    if isinstance(env, dict):
        for k, v in env.items():
            out.extend(check_env_value(file, svc, str(k), v))
    elif isinstance(env, list):
        for k, v in parse_env_list(env):
            out.extend(check_env_value(file, svc, k, v))
    return out


def check_ports(file: str, svc: str, ports) -> list[dict]:
    out: list[dict] = []
    if ports is None:
        return out
    if not isinstance(ports, list):
        ports = [ports]
    for p in ports:
        if isinstance(p, dict):
            # Long syntax: target, published, protocol
            pub = p.get("published")
            if pub is not None and isinstance(pub, str) and not is_parameterized(pub):
                if str(pub).isdigit():
                    out.append(
                        {
                            "file": file,
                            "service": svc,
                            "rule": "ports_host_literal",
                            "detail": f"published={pub!r}",
                        }
                    )
            continue
        if not isinstance(p, str):
            continue
        # "HOST:CONTAINER" or "IP::CONTAINER"
        if ":" not in p:
            continue
        parts = p.split(":")
        host = parts[0]
        if host == "":
            continue  # ":9300" style — rare
        if is_parameterized(host):
            continue
        # IPv6 in brackets — skip complex
        if host.startswith("["):
            continue
        if re.fullmatch(r"\d+", host):
            out.append(
                {
                    "file": file,
                    "service": svc,
                    "rule": "ports_host_literal",
                    "detail": f"{p!r} (host port must use ${{VAR}})",
                }
            )
    return out


def check_expose(file: str, svc: str, expose) -> list[dict]:
    out: list[dict] = []
    if expose is None:
        return out
    if not isinstance(expose, list):
        expose = [expose]
    for e in expose:
        if isinstance(e, int):
            out.append(
                {
                    "file": file,
                    "service": svc,
                    "rule": "expose_literal",
                    "detail": str(e),
                }
            )
        elif isinstance(e, str) and e.isdigit():
            out.append(
                {
                    "file": file,
                    "service": svc,
                    "rule": "expose_literal",
                    "detail": e,
                }
            )
    return out


def check_healthcheck_durations(file: str, svc: str, hc: dict) -> list[dict]:
    out: list[dict] = []
    if not isinstance(hc, dict):
        return out
    for key in ("interval", "timeout", "retries", "start_period"):
        val = hc.get(key)
        if val is None:
            continue
        if isinstance(val, int) and key == "retries":
            continue
        if not isinstance(val, str):
            continue
        if key == "retries" and val.isdigit():
            continue
        if is_parameterized(val):
            continue
        if val in HEALTHCHECK_DURATION_ALLOWLIST:
            continue
        out.append(
            {
                "file": file,
                "service": svc,
                "rule": "healthcheck_literal",
                "detail": f"{key}={val!r} (not in allowlist; use ${{VAR}} or extend HEALTHCHECK_DURATION_ALLOWLIST)",
            }
        )
    return out


def check_healthcheck_test(file: str, svc: str, hc: dict) -> list[dict]:
    out: list[dict] = []
    if not isinstance(hc, dict):
        return out
    test = hc.get("test")
    if test is None:
        return out
    if isinstance(test, str):
        blob = test
    elif isinstance(test, list):
        blob = " ".join(str(x) for x in test)
    else:
        blob = str(test)
    # Obvious hardcoded IPs (policy: use env)
    if re.search(r"\b\d{1,3}(?:\.\d{1,3}){3}\b", blob) and "host.docker.internal" not in blob:
        if "${" not in blob:
            out.append(
                {
                    "file": file,
                    "service": svc,
                    "rule": "healthcheck_literal_ip",
                    "detail": "test embeds numeric IP without ${}",
                }
            )
    return out


def check_build(file: str, svc: str, build) -> list[dict]:
    out: list[dict] = []
    if build is None:
        return out
    if isinstance(build, str):
        return out  # implicit Dockerfile
    if not isinstance(build, dict):
        out.append(
            {
                "file": file,
                "service": svc,
                "rule": "build_invalid",
                "detail": repr(build),
            }
        )
        return out
    if "context" not in build:
        out.append(
            {
                "file": file,
                "service": svc,
                "rule": "build_missing_context",
                "detail": str(build),
            }
        )
    if "dockerfile" not in build:
        out.append(
            {
                "file": file,
                "service": svc,
                "rule": "build_missing_dockerfile",
                "detail": "mapping form requires explicit dockerfile: (or use build: path string)",
            }
        )
    return out


def check_service(file: str, svc: str, spec: dict) -> list[dict]:
    out: list[dict] = []
    if not isinstance(spec, dict):
        return out
    if "image" in spec:
        out.append(
            {
                "file": file,
                "service": svc,
                "rule": "image_forbidden",
                "detail": f"image={spec['image']!r}",
            }
        )
    if "build" in spec:
        out.extend(check_build(file, svc, spec["build"]))
    out.extend(check_environment(file, svc, spec.get("environment")))
    out.extend(check_ports(file, svc, spec.get("ports")))
    out.extend(check_expose(file, svc, spec.get("expose")))
    hc = spec.get("healthcheck")
    if isinstance(hc, dict):
        out.extend(check_healthcheck_durations(file, svc, hc))
        out.extend(check_healthcheck_test(file, svc, hc))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=Path, default=None)
    ap.add_argument("--json", action="store_true", help="Print violations as JSON array")
    ap.add_argument(
        "--only-rules",
        type=str,
        default=None,
        metavar="RULES",
        help="Comma-separated rule names to enforce (default: all). "
        "Example: image_forbidden,yaml_error,build_invalid,build_missing_context,build_missing_dockerfile",
    )
    args = ap.parse_args()
    root = args.root or Path(__file__).resolve().parent.parent.parent
    docker_dir = root / "docker"
    if not docker_dir.is_dir():
        print(f"Error: {docker_dir} not found", file=sys.stderr)
        return 1

    files = sorted(
        list(docker_dir.glob("docker-compose*.yml")) + list(docker_dir.glob("compose*.yml"))
    )
    violations: list[dict] = []

    for path in files:
        try:
            data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except Exception as e:
            violations.append(
                {
                    "file": path.name,
                    "service": "—",
                    "rule": "yaml_error",
                    "detail": str(e),
                }
            )
            continue
        services = data.get("services") or {}
        for name, spec in services.items():
            if not isinstance(spec, dict):
                continue
            violations.extend(check_service(path.name, name, spec))

    if args.only_rules:
        allowed = frozenset(r.strip() for r in args.only_rules.split(",") if r.strip())
        violations = [v for v in violations if v.get("rule") in allowed]

    if args.json:
        print(json.dumps(violations, indent=2))
    else:
        if not violations:
            print("compose_guard: no violations.")
            return 0
        print(f"compose_guard: {len(violations)} violation(s)\n")
        for v in violations:
            print(f"  [{v['rule']}] {v['file']} :: {v['service']}")
            print(f"      {v['detail']}")
        print("\nSee docker/scripts/README.md (Compose guard) for policy and migration.")

    return 1 if violations else 0


if __name__ == "__main__":
    raise SystemExit(main())
