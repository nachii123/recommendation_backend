# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and dependency files first (layer caching)
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle

# Download dependencies (cached unless build.gradle changes)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q || true

# Copy source and build the fat JAR
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Render injects PORT env var; Spring reads it via ${PORT:8080}
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
