# Build: Maven + JDK 21. Tests already ran in CI (ci.yml), which blocks the image if they fail.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime: JRE 21 only, non-root user, no secrets (everything comes from environment variables)
FROM eclipse-temurin:21.0.8_9-jre-noble
RUN groupadd --system training && useradd --system --gid training --no-create-home training
WORKDIR /app
COPY --from=build /workspace/target/ms07-training-and-ai-assistant-*.jar app.jar
USER training
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
