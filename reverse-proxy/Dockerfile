FROM nginx:1.27-alpine

RUN apk add --no-cache openssl gettext \
  && mkdir -p /etc/nginx/certs \
  && rm -rf /var/cache/apk/*

COPY openssl-san.cnf /etc/nginx/openssl-san.cnf
COPY openssl-san.cnf.template /etc/nginx/openssl-san.cnf.template
COPY generate-self-signed-cert.sh /generate-self-signed-cert.sh
COPY nginx.conf /etc/nginx/nginx.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh /generate-self-signed-cert.sh

CMD ["/docker-entrypoint.sh"]
