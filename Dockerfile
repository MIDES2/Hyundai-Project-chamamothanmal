FROM node:22-bookworm-slim AS web
WORKDIR /src
COPY apps/web/ ./
RUN npm ci && npm run build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src
COPY services/api/ ./
RUN sh gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /src/build/libs/message-api-0.1.0-SNAPSHOT.jar /app/api.jar
COPY --from=web /src/dist/ /app/web/
ENV API_HOST=0.0.0.0 API_PORT=10000 APP_ENV=development API_AUTH_MODE=local API_STORAGE_MODE=local
ENV API_DB_URL="jdbc:h2:file:/var/data/messages;DB_CLOSE_ON_EXIT=FALSE" API_UPLOAD_DIR=/var/data/uploads
ENV SPRING_CONFIG_IMPORT="" SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/web/
RUN mkdir -p /var/data
EXPOSE 10000
ENTRYPOINT ["java","-jar","/app/api.jar"]
