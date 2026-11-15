FROM node:24.19.0-bookworm-slim AS web-build

WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS server-build

WORKDIR /build/server
COPY server/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY server/ ./
COPY --from=web-build /build/web/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 finger-memory

WORKDIR /app
COPY --from=server-build /build/server/target/finger-memory-server-0.1.0-SNAPSHOT.jar /app/app.jar

USER finger-memory
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
