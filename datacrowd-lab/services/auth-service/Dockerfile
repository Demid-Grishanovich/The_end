# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# curl нужен для healthcheck (compose)
RUN apk add --no-cache curl && addgroup -S app && adduser -S appuser -G app

COPY --from=builder /workspace/target/*.jar /app/app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]