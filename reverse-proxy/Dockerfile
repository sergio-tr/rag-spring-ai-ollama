FROM nginx:1.27-alpine

# Generate a self-signed certificate at build time for prod local TLS.
# (No requirement for OpenSSL on the host.)
RUN apk add --no-cache openssl \
  && mkdir -p /etc/nginx/certs \
  && openssl req -x509 -nodes -newkey rsa:2048 \
    -keyout /etc/nginx/certs/tls.key \
    -out /etc/nginx/certs/tls.crt \
    -days 365 \
    -subj "/CN=localhost" \
  && rm -rf /var/cache/apk/*

# Template uses env-substituted upstream/timeout/TLS/CSP variables at container start.
COPY nginx.conf /etc/nginx/nginx.conf.template

# Entrypoint: substitute only listed vars so nginx $host / $remote_addr are not touched.
CMD ["/bin/sh", "-c", \
  "export WEBAPP_HOST=\"${WEBAPP_HOST:-webapp}\" \
    BACKEND_INTERNAL_PORT=\"${BACKEND_INTERNAL_PORT:-9000}\" \
    WEBAPP_INTERNAL_PORT=\"${WEBAPP_INTERNAL_PORT:-3000}\" \
    TLS_CERT_PATH=\"${TLS_CERT_PATH:-/etc/nginx/certs/tls.crt}\" \
    TLS_KEY_PATH=\"${TLS_KEY_PATH:-/etc/nginx/certs/tls.key}\" \
    REVERSE_PROXY_ENFORCE_HTTPS=\"${REVERSE_PROXY_ENFORCE_HTTPS:-0}\" \
    API_PROXY_CONNECT_TIMEOUT=\"${API_PROXY_CONNECT_TIMEOUT:-10s}\" \
    API_PROXY_SEND_TIMEOUT=\"${API_PROXY_SEND_TIMEOUT:-180s}\" \
    API_PROXY_READ_TIMEOUT=\"${API_PROXY_READ_TIMEOUT:-180s}\" \
    API_CLIENT_MAX_BODY_SIZE=\"${API_CLIENT_MAX_BODY_SIZE:-50m}\" \
    WEB_CLIENT_MAX_BODY_SIZE=\"${WEB_CLIENT_MAX_BODY_SIZE:-20m}\" \
    CONTENT_SECURITY_POLICY=\"${CONTENT_SECURITY_POLICY:-default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; object-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; font-src 'self' data:; connect-src 'self' https: http: ws: wss:}\" \
    && envsubst '$BACKEND_HOST $BACKEND_INTERNAL_PORT $WEBAPP_HOST $WEBAPP_INTERNAL_PORT $TLS_CERT_PATH $TLS_KEY_PATH $REVERSE_PROXY_ENFORCE_HTTPS $API_PROXY_CONNECT_TIMEOUT $API_PROXY_SEND_TIMEOUT $API_PROXY_READ_TIMEOUT $API_CLIENT_MAX_BODY_SIZE $WEB_CLIENT_MAX_BODY_SIZE $CONTENT_SECURITY_POLICY' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf \
    && nginx -g 'daemon off;'"]

