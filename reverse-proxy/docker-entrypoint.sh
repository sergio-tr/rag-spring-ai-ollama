#!/bin/sh
set -eu

# Docker embedded DNS (127.0.0.11) does not read /etc/hosts. The "resolve" upstream flag
# forces that resolver, so hostnames from --add-host (e.g. host-gateway.internal in CI)
# must omit "resolve" and rely on libc resolution at config load time instead.
upstream_resolve_suffix() {
  host="$1"
  case "$host" in
    *[!0-9.]*|'') ;;
    *)
      echo "$host" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' && return 0
      ;;
  esac
  if awk -v h="$host" '$2 == h { found = 1 } END { exit !found }' /etc/hosts 2>/dev/null; then
    return 0
  fi
  echo " resolve"
}

export BACKEND_HOST="${BACKEND_HOST:-backend}"
export WEBAPP_HOST="${WEBAPP_HOST:-webapp}"
export BACKEND_RESOLVE="$(upstream_resolve_suffix "$BACKEND_HOST")"
export WEBAPP_RESOLVE="$(upstream_resolve_suffix "$WEBAPP_HOST")"
export BACKEND_INTERNAL_PORT="${BACKEND_INTERNAL_PORT:-9000}"
export WEBAPP_INTERNAL_PORT="${WEBAPP_INTERNAL_PORT:-3000}"
export TLS_CERT_PATH="${TLS_CERT_PATH:-/etc/nginx/certs/tls.crt}"
export TLS_KEY_PATH="${TLS_KEY_PATH:-/etc/nginx/certs/tls.key}"
export REVERSE_PROXY_ENFORCE_HTTPS="${REVERSE_PROXY_ENFORCE_HTTPS:-0}"
export REVERSE_PROXY_HTTPS_PORT_SUFFIX="${REVERSE_PROXY_HTTPS_PORT_SUFFIX:-}"
export API_PROXY_CONNECT_TIMEOUT="${API_PROXY_CONNECT_TIMEOUT:-10s}"
export API_PROXY_SEND_TIMEOUT="${API_PROXY_SEND_TIMEOUT:-180s}"
export API_PROXY_READ_TIMEOUT="${API_PROXY_READ_TIMEOUT:-180s}"
export WEB_PROXY_CONNECT_TIMEOUT="${WEB_PROXY_CONNECT_TIMEOUT:-10s}"
export WEB_PROXY_SEND_TIMEOUT="${WEB_PROXY_SEND_TIMEOUT:-180s}"
export WEB_PROXY_READ_TIMEOUT="${WEB_PROXY_READ_TIMEOUT:-180s}"
export RAG_API_PRODUCT_BASE_PATH="${RAG_API_PRODUCT_BASE_PATH:-/api/v5}"
export API_CLIENT_MAX_BODY_SIZE="${API_CLIENT_MAX_BODY_SIZE:-50m}"
export WEB_CLIENT_MAX_BODY_SIZE="${WEB_CLIENT_MAX_BODY_SIZE:-20m}"
export CONTENT_SECURITY_POLICY="${CONTENT_SECURITY_POLICY:-default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; object-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; font-src 'self' data:; connect-src 'self' https: http: ws: wss:}"

envsubst '$BACKEND_HOST $BACKEND_INTERNAL_PORT $BACKEND_RESOLVE $WEBAPP_HOST $WEBAPP_INTERNAL_PORT $WEBAPP_RESOLVE $TLS_CERT_PATH $TLS_KEY_PATH $REVERSE_PROXY_ENFORCE_HTTPS $REVERSE_PROXY_HTTPS_PORT_SUFFIX $API_PROXY_CONNECT_TIMEOUT $API_PROXY_SEND_TIMEOUT $API_PROXY_READ_TIMEOUT $WEB_PROXY_CONNECT_TIMEOUT $WEB_PROXY_SEND_TIMEOUT $WEB_PROXY_READ_TIMEOUT $RAG_API_PRODUCT_BASE_PATH $API_CLIENT_MAX_BODY_SIZE $WEB_CLIENT_MAX_BODY_SIZE $CONTENT_SECURITY_POLICY' \
  < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

exec nginx -g 'daemon off;'
