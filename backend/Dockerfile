FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -ntp clean package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 qyd
WORKDIR /app
COPY --from=build /workspace/qyd-bootstrap/target/qyd-bootstrap-1.0.0-SNAPSHOT.jar app.jar
USER qyd
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
