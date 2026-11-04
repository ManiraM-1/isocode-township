# syntax=docker/dockerfile:1

FROM node:22.17.1-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build && npm test -- --run

FROM eclipse-temurin:21-jdk-jammy AS api
WORKDIR /app
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S township && adduser -S township -G township
WORKDIR /app
COPY --from=api /app/target/township-*[0-9].jar app.jar
USER township
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s CMD wget -qO- "http://localhost:${PORT:-8080}/api/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
