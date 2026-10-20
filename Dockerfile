FROM node:24-bookworm-slim AS web
WORKDIR /build/apps/web
COPY apps/web/package*.json ./
RUN npm ci --no-fund --no-audit
COPY apps/web/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /build
COPY apps/server/pom.xml apps/server/pom.xml
COPY apps/server/src apps/server/src
COPY --from=web /build/apps/web/dist apps/web/dist
RUN mvn -B -f apps/server/pom.xml verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system copilot && useradd --system --gid copilot --home-dir /app copilot
COPY --from=server --chown=copilot:copilot /build/apps/server/target/meeting-copilot-server-*.jar /app/app.jar
USER copilot
ENV SPRING_PROFILES_ACTIVE=prod PORT=10000
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
