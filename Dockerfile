# ---- Build stage ----
FROM maven:3.6.3-jdk-8 AS builder
WORKDIR /build
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -B -q

# ---- Run stage ----
FROM eclipse-temurin:8-jre-jammy

RUN mkdir -p /app/appsystems /app/applogs /app/appsystems/config \
  /app/appsystems/qr /app/applogs/dump /app/frontend

COPY src/main/resources/start.sh /app/start.sh
COPY --from=builder /build/target/*.jar /app/appsystems/
COPY frontend /app/frontend

ENV FRONTEND_DIR=/app/frontend
ENV QR_DIR=/app/appsystems/qr

RUN chmod +x /app/start.sh

WORKDIR /app/appsystems
ENTRYPOINT ["/bin/sh", "-c", "/app/start.sh"]
