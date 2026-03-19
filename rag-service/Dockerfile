# Build stage. Base images from rag-service/.env (RAG_JAVA_JDK_BASE_IMAGE, RAG_JAVA_JRE_BASE_IMAGE).
# Both ARGs must be declared before the first FROM so every FROM can resolve a non-empty base (Docker / linters).
ARG JDK_BASE_IMAGE=eclipse-temurin:21-jdk
ARG JRE_BASE_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${JDK_BASE_IMAGE} AS build
WORKDIR /app

# Maven Wrapper (mvnw + .mvn/) — avoids requiring Maven on the host or base image.
# mvnw and .mvn/ are present inside rag-service/ (copies of the repo-root wrapper).
COPY mvnw ./mvnw
COPY .mvn ./.mvn
RUN chmod +x mvnw

COPY pom.xml .
RUN ./mvnw -B dependency:go-offline -DskipTests
COPY src ./src
RUN ./mvnw -B package -DskipTests

# Run stage
FROM ${JRE_BASE_IMAGE}
ARG SERVER_PORT=9000
ENV SERVER_PORT=${SERVER_PORT}
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
