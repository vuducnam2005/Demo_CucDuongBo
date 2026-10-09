# Multi-stage Dockerfile cho Backend Spring Boot 3 + Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Sao chép pom.xml và maven wrapper
COPY pom.xml mvnw ./
COPY .mvn .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Sao chép mã nguồn và đóng gói
COPY src src/
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Tạo non-root user và cấp quyền thư mục dữ liệu
RUN addgroup -S kcht && adduser -S kcht -G kcht \
    && mkdir -p /app/data/storage /tmp/data/storage \
    && chown -R kcht:kcht /app /tmp/data/storage
USER kcht:kcht

COPY --from=builder /build/target/*.jar app.jar

ENV SERVER_PORT=8089 \
    TZ=UTC

EXPOSE 8089

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8089}/actuator/health || exit 0

ENTRYPOINT ["java", "-Xmx320m", "-Xms128m", "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=128m", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
