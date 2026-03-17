# Build stage. Base images from rag-service/.env (RAG_JAVA_JDK_BASE_IMAGE, RAG_JAVA_JRE_BASE_IMAGE).
ARG JDK_BASE_IMAGE=eclipse-temurin:21-jdk
FROM ${JDK_BASE_IMAGE} AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B package -DskipTests

# Run stage
ARG JRE_BASE_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${JRE_BASE_IMAGE}
ARG SERVER_PORT=9000
ENV SERVER_PORT=${SERVER_PORT}
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
