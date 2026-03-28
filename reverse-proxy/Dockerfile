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

# nginx.conf uses ${BACKEND_INTERNAL_PORT}; envsubst substitutes it at container start.
COPY nginx.conf /etc/nginx/nginx.conf.template

# Entrypoint: substitute env vars in template, then start nginx
CMD ["/bin/sh", "-c", \
  "envsubst '${BACKEND_INTERNAL_PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf && nginx -g 'daemon off;'"]

