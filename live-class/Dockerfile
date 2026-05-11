# Stage 1: builder — compile and package the Spring Boot fat-jar
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy dependency descriptors first to exploit layer caching
COPY gradlew ./
COPY gradle/wrapper/ gradle/wrapper/
COPY build.gradle settings.gradle ./

# Conditionally copy gradle.properties if it exists
COPY gradle.properties* ./

# Resolve and cache all dependencies before copying source
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet || true

# Copy application source
COPY src/ src/

# Build the fat-jar; tests run in CI, skip here to keep image build fast
RUN ./gradlew clean bootJar -x test --no-daemon

# Stage 2: runtime — minimal JRE image
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a non-root user to run the application
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy only the built artifact from the builder stage
COPY --from=builder /workspace/build/libs/*.jar app.jar

RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# JAVA_OPTS can be injected at runtime via environment variable
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
