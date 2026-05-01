# Thin wrapper so Compose uses `build:` (compose_guard forbids service-level `image:`).
# Bump the default when upgrading Mailpit; override at build time via MAILPIT_BASE_IMAGE.
ARG MAILPIT_BASE_IMAGE=axllent/mailpit:v1.29.7
FROM ${MAILPIT_BASE_IMAGE}
