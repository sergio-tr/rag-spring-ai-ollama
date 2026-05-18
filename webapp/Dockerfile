FROM node:22-bookworm-slim AS deps
WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund

FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_RAG_API_PREFIX
ARG NEXT_PUBLIC_TIMEZONE=UTC
ARG NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED
ARG NEXT_PUBLIC_LEGAL_PRIVACY_VERSION
ARG NEXT_PUBLIC_LEGAL_TERMS_VERSION
ARG NEXT_PUBLIC_AUTH_ACCESS_COOKIE_NAME
ARG NEXT_PUBLIC_AUTH_REFRESH_COOKIE_NAME
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_RAG_API_PREFIX=$NEXT_PUBLIC_RAG_API_PREFIX
ENV NEXT_PUBLIC_TIMEZONE=$NEXT_PUBLIC_TIMEZONE
ENV NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED=$NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED
ENV NEXT_PUBLIC_LEGAL_PRIVACY_VERSION=$NEXT_PUBLIC_LEGAL_PRIVACY_VERSION
ENV NEXT_PUBLIC_LEGAL_TERMS_VERSION=$NEXT_PUBLIC_LEGAL_TERMS_VERSION
ENV NEXT_PUBLIC_AUTH_ACCESS_COOKIE_NAME=$NEXT_PUBLIC_AUTH_ACCESS_COOKIE_NAME
ENV NEXT_PUBLIC_AUTH_REFRESH_COOKIE_NAME=$NEXT_PUBLIC_AUTH_REFRESH_COOKIE_NAME
RUN npm run build

FROM node:22-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
# Overridden at runtime by docker-compose (WEBAPP_CONTAINER_PORT / PORT).
ENV PORT=3000
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force
COPY --from=build /app/public ./public
COPY --from=build /app/.next ./.next
COPY --from=build /app/next.config.ts ./next.config.ts

EXPOSE 3000
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=6 \
  CMD node -e "fetch('http://127.0.0.1:' + (process.env.PORT || '3000') + '/en/login').then((r) => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

# PORT and listen host must match docker-compose port mapping and nginx WEBAPP_INTERNAL_PORT.
CMD ["sh", "-c", "exec npm run start -- -p \"${PORT:-3000}\" -H 0.0.0.0"]
