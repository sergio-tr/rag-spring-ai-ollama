#!/bin/sh
# Generate a self-signed TLS certificate with Subject Alternative Names when missing.
# Skips regeneration if tls.crt and tls.key already exist (unless TLS_CERT_FORCE_REGENERATE=1).
set -eu

CERT_DIR="${TLS_CERT_DIR:-/etc/nginx/certs}"
CERT_PATH="${TLS_CERT_PATH:-${CERT_DIR}/tls.crt}"
KEY_PATH="${TLS_KEY_PATH:-${CERT_DIR}/tls.key}"
GENERATED_CONFIG="${OPENSSL_CONFIG:-/etc/nginx/openssl-san.cnf}.generated"
DAYS="${TLS_CERT_DAYS:-365}"

TLS_CERT_COUNTRY="${TLS_CERT_COUNTRY:-ES}"
TLS_CERT_STATE="${TLS_CERT_STATE:-Asturias}"
TLS_CERT_LOCALITY="${TLS_CERT_LOCALITY:-Oviedo}"
TLS_CERT_ORGANIZATION="${TLS_CERT_ORGANIZATION:-University of Oviedo}"
TLS_CERT_ORGANIZATIONAL_UNIT="${TLS_CERT_ORGANIZATIONAL_UNIT:-RAG System}"
TLS_CERT_COMMON_NAME="${TLS_CERT_COMMON_NAME:-localhost}"

# SAN defaults: localhost for dev; override via compose/.env for production (ngrok hostname, uniovi.es, …).
TLS_CERT_DNS_1="${TLS_CERT_DNS_1:-localhost}"
TLS_CERT_DNS_2="${TLS_CERT_DNS_2:-}"
TLS_CERT_DNS_3="${TLS_CERT_DNS_3:-}"
TLS_CERT_DNS_4="${TLS_CERT_DNS_4:-}"
TLS_CERT_IP_1="${TLS_CERT_IP_1:-127.0.0.1}"
TLS_CERT_IP_2="${TLS_CERT_IP_2:-}"

mkdir -p "$CERT_DIR"

if [ "${TLS_CERT_FORCE_REGENERATE:-0}" != "1" ] && [ -f "$CERT_PATH" ] && [ -f "$KEY_PATH" ]; then
  echo "TLS certificate already present at ${CERT_PATH}; skipping generation."
  exit 0
fi

write_openssl_config() {
  cat <<EOF
[req]
default_bits = 4096
prompt = no
default_md = sha256
x509_extensions = v3_req
distinguished_name = dn

[dn]
C = ${TLS_CERT_COUNTRY}
ST = ${TLS_CERT_STATE}
L = ${TLS_CERT_LOCALITY}
O = ${TLS_CERT_ORGANIZATION}
OU = ${TLS_CERT_ORGANIZATIONAL_UNIT}
CN = ${TLS_CERT_COMMON_NAME}

[v3_req]
subjectAltName = @alt_names
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

[alt_names]
EOF
  dns_index=1
  for dns in "$TLS_CERT_DNS_1" "$TLS_CERT_DNS_2" "$TLS_CERT_DNS_3" "$TLS_CERT_DNS_4"; do
    if [ -n "$dns" ]; then
      printf 'DNS.%s = %s\n' "$dns_index" "$dns"
      dns_index=$((dns_index + 1))
    fi
  done
  ip_index=1
  for ip in "$TLS_CERT_IP_1" "$TLS_CERT_IP_2"; do
    if [ -n "$ip" ]; then
      printf 'IP.%s = %s\n' "$ip_index" "$ip"
      ip_index=$((ip_index + 1))
    fi
  done
}

write_openssl_config > "$GENERATED_CONFIG"

openssl req -x509 -nodes -newkey rsa:4096 \
  -keyout "$KEY_PATH" \
  -out "$CERT_PATH" \
  -days "$DAYS" \
  -config "$GENERATED_CONFIG"

chmod 600 "$KEY_PATH"
chmod 644 "$CERT_PATH"

echo "Self-signed TLS certificate written to ${CERT_PATH} (CN=${TLS_CERT_COMMON_NAME})."
