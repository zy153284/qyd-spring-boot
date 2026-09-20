FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace/backend
COPY backend/ .
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home qyd
USER qyd
WORKDIR /app
COPY --from=build /workspace/backend/qyd-bootstrap/target/qyd-bootstrap-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar","--spring.profiles.active=prod"]
