FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/index.html ./
COPY frontend/js ./js
COPY frontend/css ./css
COPY frontend/scripts ./scripts
COPY frontend/test ./test
RUN npm test && API_BASE_URL= npm run build

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN ./mvnw -B verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
USER app
ENV SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=4 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
