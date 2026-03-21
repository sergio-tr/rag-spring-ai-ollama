# Build stage. Base images from rag-service/.env (RAG_JAVA_JDK_BASE_IMAGE, RAG_JAVA_JRE_BASE_IMAGE).
# Both ARGs must be declared before the first FROM so every FROM can resolve a non-empty base (Docker / linters).
ARG JDK_BASE_IMAGE=eclipse-temurin:21-jdk
ARG JRE_BASE_IMAGE=eclipse-temurin:21-jre-alpine
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
ARG SERVER_PORT=9000
ENV SERVER_PORT=${SERVER_PORT}
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
