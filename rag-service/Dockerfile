# Build stage. Base images from rag-service/.env (RAG_JAVA_JDK_BASE_IMAGE, RAG_JAVA_JRE_BASE_IMAGE).
# Both ARGs must be declared before the first FROM so every FROM can resolve a non-empty base (Docker / linters).
ARG JDK_BASE_IMAGE=eclipse-temurin:21-jdk
ARG JRE_BASE_IMAGE=eclipse-temurin:21-jre-alpine
# Used by scripts/full-stack-verify.*: `docker build --target build` then `mvnw verify` in that image (same JDK as Compose backend).
FROM ${JDK_BASE_IMAGE} AS build
WORKDIR /app

# Maven Wrapper (mvnw + .mvn/) — no Maven install required on host or base image.
# Wrapper lives only under rag-service/ (single copy in the repo).
COPY mvnw ./mvnw
COPY .mvn ./.mvn
# Windows CRLF checkouts break the shell shebang and wrapper URLs (e.g. jar%0D → 404).
RUN sed -i 's/\r$//' mvnw && find .mvn -type f -exec sed -i 's/\r$//' {} + && chmod +x mvnw

COPY pom.xml .
RUN ./mvnw -B dependency:go-offline -Dmaven.test.skip=true
COPY src ./src
# Production image: skip compiling tests (-DskipTests only skips execution, not compilation).
RUN ./mvnw -B package -Dmaven.test.skip=true

# Run stage
FROM ${JRE_BASE_IMAGE}
# curl: reliable Docker healthcheck against /actuator/health/readiness (HTTP 503 when not ready)
RUN if command -v apk >/dev/null 2>&1; then apk add --no-cache curl; \
    elif command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*; \
    fi
ARG SERVER_PORT=9000
ENV SERVER_PORT=${SERVER_PORT}
# Default profile when the image runs without Compose env: avoid OTLP export to localhost:4318 inside the container.
ENV SPRING_PROFILES_ACTIVE=docker
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
