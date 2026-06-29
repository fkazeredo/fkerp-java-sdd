# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the project's Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# Normalize the wrapper to LF (it may be checked out with CRLF on Windows) and build.
# Tests/gates run in CI and `./mvnw verify`; the image build only packages the jar.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -ntp -DskipTests clean package

# ---- Runtime stage: slim JRE running the repackaged Spring Boot jar ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
