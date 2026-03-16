# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Defaults; override via env in compose
ENV SERVER_PORT=9000
EXPOSE 9000

ENTRYPOINT ["java", "-jar", "app.jar"]
