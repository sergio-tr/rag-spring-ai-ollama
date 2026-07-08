#!/usr/bin/env python3
"""
Write GitHub Actions repository secrets (passed as environment variables) into
component .env files on the self-hosted deploy host.

- Never prints secret values.
- Updates existing keys or appends new ones.
- Intended to run after create-env-all.sh on each deploy.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=")


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent.parent


def first_non_blank(*names: str) -> str | None:
    for name in names:
        value = os.environ.get(name)
        if value is not None and value.strip():
            return value.strip()
    return None


def format_env_value(value: str) -> str:
    if re.search(r'[\s#"\']', value):
        escaped = value.replace("\\", "\\\\").replace('"', '\\"')
        return f'"{escaped}"'
    return value


def set_env_key(env_path: Path, key: str, value: str) -> bool:
    line = f"{key}={format_env_value(value)}"
    if env_path.is_file():
        text = env_path.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        replaced = False
        out: list[str] = []
        for existing in lines:
            m = KEY_RE.match(existing.strip())
            if m and m.group(1) == key and not existing.strip().startswith("#"):
                out.append(line)
                replaced = True
            else:
                out.append(existing)
        if not replaced:
            if out and out[-1].strip():
                out.append("")
            out.append(f"# Applied by apply_deploy_secrets.py (deploy workflow)")
            out.append(line)
        env_path.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8")
    else:
        env_path.parent.mkdir(parents=True, exist_ok=True)
        env_path.write_text(f"# Created by apply_deploy_secrets.py\n{line}\n", encoding="utf-8")
    try:
        env_path.chmod(0o600)
    except OSError:
        pass
    return True


def apply_mapping(root: Path, rel_file: str, key: str, *secret_names: str) -> bool:
    value = first_non_blank(*secret_names)
    if value is None:
        return False
    path = root / rel_file
    set_env_key(path, key, value)
    print(f"Updated {rel_file}: {key}")
    return True


def main() -> int:
    root = repo_root()
    applied = 0

    mappings: list[tuple[str, str, tuple[str, ...]]] = [
        ("db/.env", "POSTGRES_PASSWORD", ("POSTGRES_PASSWORD",)),
        (
            "rag-service/.env",
            "SPRING_DATASOURCE_PASSWORD",
            ("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD"),
        ),
        ("rag-service/.env", "RAG_JWT_SECRET", ("JWT_SECRET", "JWT_SERVICE")),
        (
            "rag-service/.env",
            "OPENAI_COMPATIBLE_API_KEY",
            ("LITELLM_API_KEY", "OPENAI_API_KEY"),
        ),
        ("rag-service/.env", "SPRING_MAIL_PASSWORD", ("MAIL_PASSWORD",)),
        (
            "rag-service/.env",
            "RAG_AUTH_OAUTH_GOOGLE_CLIENT_ID",
            ("GOOGLE_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_ID"),
        ),
        (
            "rag-service/.env",
            "GOOGLE_OAUTH_CLIENT_ID",
            ("GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_CLIENT_ID"),
        ),
        (
            "rag-service/.env",
            "RAG_AUTH_OAUTH_GOOGLE_CLIENT_SECRET",
            ("GOOGLE_CLIENT_SECRET", "GOOGLE_OAUTH_CLIENT_SECRET"),
        ),
        (
            "rag-service/.env",
            "GOOGLE_OAUTH_CLIENT_SECRET",
            ("GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_CLIENT_SECRET"),
        ),
        ("rag-service/.env", "RAG_BOOTSTRAP_ADMIN_PASSWORD", ("ADMIN_PASSWORD",)),
        ("rag-service/.env", "RAG_BOOTSTRAP_ADMIN_EMAIL", ("ADMIN_EMAIL",)),
        ("observability/.env", "GRAFANA_ADMIN_PASSWORD", ("GRAFANA_ADMIN_PASSWORD",)),
    ]

    for rel_file, key, secret_names in mappings:
        if apply_mapping(root, rel_file, key, *secret_names):
            applied += 1

    # SESSION_SECRET is reserved in GitHub but not consumed by current Compose/Spring config.
    if first_non_blank("SESSION_SECRET"):
        print("Note: SESSION_SECRET is set in GitHub but has no mapped .env key in this repo.")

    required_checks: list[tuple[str, str, tuple[str, ...]]] = [
        ("db/.env", "POSTGRES_PASSWORD", ("POSTGRES_PASSWORD",)),
        ("rag-service/.env", "RAG_JWT_SECRET", ("JWT_SECRET", "JWT_SERVICE")),
        (
            "rag-service/.env",
            "OPENAI_COMPATIBLE_API_KEY",
            ("LITELLM_API_KEY", "OPENAI_API_KEY"),
        ),
    ]
    missing: list[str] = []
    for rel_file, key, secret_names in required_checks:
        if first_non_blank(*secret_names) is None:
            missing.append(f"{key} (GitHub secret: {' or '.join(secret_names)})")

    if missing:
        print("::error::Missing required GitHub secrets for production deploy:", file=sys.stderr)
        for item in missing:
            print(f"  - {item}", file=sys.stderr)
        return 1

    print(f"Applied {applied} secret mapping(s) to .env files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
