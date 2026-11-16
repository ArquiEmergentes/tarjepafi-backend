# TarjePAFI backend image. Build context: the repository root.
#   docker build -t tarjepafi-backend .
# Tests are not run here (they need Docker for Testcontainers): CI runs `mvn verify` before building the image.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
# Dependencies first, so that a source-only change reuses this layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/tarjepafi-backend-*.jar extract --layers --destination /workspace/layers

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build --chown=app:app /workspace/layers/dependencies/ ./
COPY --from=build --chown=app:app /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/layers/application/ ./
USER app

# The listening port comes from PORT (default 8080). Azure App Service needs WEBSITES_PORT to match it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/tarjepafi-backend-0.1.0-SNAPSHOT.jar"]
