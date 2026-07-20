# Build stage: Gradle with JDK 25 toolchain
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy gradle wrapper and config first for layer caching
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon -q || true

# Copy source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -q

# Runtime stage: JRE only
FROM eclipse-temurin:25-jre
WORKDIR /app
# curl is used by the compose healthcheck to probe /actuator/health
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
