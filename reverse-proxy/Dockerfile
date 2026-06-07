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
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

CMD ["/docker-entrypoint.sh"]
