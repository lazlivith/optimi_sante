# syntax=docker/dockerfile:1
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# Étape dépendances séparée : mise en cache tant que pom.xml ne change pas.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
# Cache du dépôt Maven monté : les rebuilds ne re-téléchargent pas les dépendances.
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
