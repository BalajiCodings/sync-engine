# ==========================================
# STAGE 1: Build and Package the Application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 1. Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Grant execution rights to the Maven wrapper
RUN chmod +x mvnw

# Download dependencies offline (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# 2. Copy source code and build the JAR (skipping tests since CI already verified them)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# STAGE 2: Lightweight, Secure Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# 1. Security: Create an unprivileged user and group
RUN addgroup -S syncgroup && adduser -S syncuser -G syncgroup

# 2. Install curl for health checking
RUN apk --no-cache add curl

# 3. Copy only the compiled JAR from Stage 1
COPY --from=builder /build/target/*.jar app.jar

# 4. Change file ownership to our non-root user
RUN chown -R syncuser:syncgroup /app

# 5. Switch to unprivileged user
USER syncuser

# 6. Expose Spring Boot default port
EXPOSE 8080

# 7. JVM Flags: Container-aware memory limits (uses up to 75% of assigned container RAM)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# 8. Container Healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 9. Start the Sync Engine
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]