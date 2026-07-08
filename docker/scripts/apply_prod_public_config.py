#!/usr/bin/env python3
"""
Apply production public URL and reverse-proxy settings to webapp/.env and rag-service/.env.

Defaults target the reserved ngrok public URL (Google OAuth requires a hostname, not an IP).
Override via GitHub Actions repository variables (PRODUCTION_PUBLIC_HOST, etc.) on deploy.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=")

DEFAULT_HOST = "hatchback-obsession-staring.ngrok-free.dev"
DEFAULT_HTTPS_PORT = "443"
DEFAULT_HTTP_PORT = "80"
DEFAULT_LITELLM_BASE_URL = "http://156.35.160.78:4000"
DEFAULT_LITELLM_CHAT_MODEL = "qwen3.5:9b"
DEFAULT_LITELLM_EMBEDDING_MODEL = "bge-m3"
DEFAULT_MAIL_USERNAME = "support.rag@gmail.es"


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent.parent


def format_env_value(value: str) -> str:
    if value == "":
        return '""'
    if re.search(r'[\s#"\']', value):
        escaped = value.replace("\\", "\\\\").replace('"', '\\"')
        return f'"{escaped}"'
    return value


def set_env_key(env_path: Path, key: str, value: str) -> None:
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
            out.append("# Applied by apply_prod_public_config.py (deploy workflow)")
            out.append(line)
        env_path.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8")
    else:
        env_path.parent.mkdir(parents=True, exist_ok=True)
        env_path.write_text(f"# Created by apply_prod_public_config.py\n{line}\n", encoding="utf-8")
    try:
        env_path.chmod(0o600)
    except OSError:
        pass


def public_https_base(host: str, https_port: int) -> str:
    if https_port == 443:
        return f"https://{host}"
    return f"https://{host}:{https_port}"


def https_port_suffix(https_port: int) -> str:
    return "" if https_port == 443 else f":{https_port}"


def main() -> int:
    host = os.environ.get("PRODUCTION_PUBLIC_HOST", DEFAULT_HOST).strip() or DEFAULT_HOST
    https_port = int(os.environ.get("PRODUCTION_HTTPS_PORT", DEFAULT_HTTPS_PORT).strip() or DEFAULT_HTTPS_PORT)
    http_port = int(os.environ.get("PRODUCTION_HTTP_PORT", DEFAULT_HTTP_PORT).strip() or DEFAULT_HTTP_PORT)
    enforce_https = os.environ.get("PRODUCTION_ENFORCE_HTTPS", "0").strip() not in ("0", "false", "False")

    public_base = public_https_base(host, https_port)
    suffix = https_port_suffix(https_port)
    api_base = f"{public_base}/api/v5"

    webapp_env = repo_root() / "webapp" / ".env"
    rag_env = repo_root() / "rag-service" / ".env"

    webapp_updates = {
        "REVERSE_PROXY_HTTP_PORT": str(http_port),
        "REVERSE_PROXY_HTTPS_PORT": str(https_port),
        "REVERSE_PROXY_ENFORCE_HTTPS": "1" if enforce_https else "0",
        "REVERSE_PROXY_HTTPS_PORT_SUFFIX": suffix,
        "REVERSE_PROXY_SERVER_NAME": host,
        "TLS_CERT_COMMON_NAME": host,
        "TLS_CERT_DNS_1": host,
        "TLS_CERT_IP_1": "127.0.0.1",
        "TLS_CERT_IP_2": "",
        "PUBLIC_APP_URL": public_base,
        "PUBLIC_API_URL": api_base,
        "NEXT_PUBLIC_APP_URL": public_base,
        "NEXT_PUBLIC_API_BASE_URL": "",
        "NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED": "true",
    }
    for key, value in webapp_updates.items():
        set_env_key(webapp_env, key, value)
        print(f"Updated webapp/.env: {key}")

    rag_updates = {
        "SPRING_PROFILES_ACTIVE": "prod,docker,infra",
        "RAG_LLM_DEFAULT_PROVIDER": "OPENAI_COMPATIBLE",
        "RAG_LLM_DEFAULT_CHAT_PROVIDER": "OPENAI_COMPATIBLE",
        "RAG_LLM_DEFAULT_EMBEDDING_PROVIDER": "OPENAI_COMPATIBLE",
        "LITELLM_BASE_URL": os.environ.get("LITELLM_BASE_URL", DEFAULT_LITELLM_BASE_URL).strip()
        or DEFAULT_LITELLM_BASE_URL,
        "LITELLM_CHAT_MODEL": os.environ.get("LITELLM_CHAT_MODEL", DEFAULT_LITELLM_CHAT_MODEL).strip()
        or DEFAULT_LITELLM_CHAT_MODEL,
        "LITELLM_EMBEDDING_MODEL": os.environ.get(
            "LITELLM_EMBEDDING_MODEL", DEFAULT_LITELLM_EMBEDDING_MODEL
        ).strip()
        or DEFAULT_LITELLM_EMBEDDING_MODEL,
        "RAG_AUTH_WEBAPP_BASE_URL": public_base,
        "RAG_AUTH_BACKEND_BASE_URL": public_base,
        "RAG_CORS_ALLOWED_ORIGINS": public_base,
        "RAG_AUTH_OAUTH_ENABLED": "true",
        "RAG_AUTH_MAIL_DELIVERY_MODE": "smtp",
        "SPRING_MAIL_HOST": "smtp.gmail.com",
        "SPRING_MAIL_PORT": "587",
        "SPRING_MAIL_USERNAME": os.environ.get("PRODUCTION_MAIL_USERNAME", DEFAULT_MAIL_USERNAME).strip()
        or DEFAULT_MAIL_USERNAME,
        "RAG_AUTH_MAIL_FROM": os.environ.get("PRODUCTION_MAIL_USERNAME", DEFAULT_MAIL_USERNAME).strip()
        or DEFAULT_MAIL_USERNAME,
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH": "true",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE": "true",
        "COOKIE_SECURE": "true",
    }
    for key, value in rag_updates.items():
        set_env_key(rag_env, key, value)
        print(f"Updated rag-service/.env: {key}")

    health_url = f"https://127.0.0.1:{https_port}/actuator/health/liveness"
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"production_public_url={public_base}\n")
            out.write(f"deploy_health_url={health_url}\n")

    print(f"Production public URL: {public_base}")
    print(f"Suggested DEPLOY_HEALTH_URL (self-signed TLS): {health_url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
