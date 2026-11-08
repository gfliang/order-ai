# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/order-ai-backend-0.0.1-SNAPSHOT.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
