# syntax=docker/dockerfile:1
# One image for trying the app with the demo club, and for running it on any machine with Docker.
# It holds the app with the UI built in; `simulate` runs the fake decoder from the same image.
# See docs/docker.md.

FROM node:22-bookworm-slim AS node

FROM eclipse-temurin:21-jdk AS builder
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
WORKDIR /build
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties VERSION ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
COPY app/ app/
COPY decoder-protocol/ decoder-protocol/
COPY decoder-simulator/ decoder-simulator/
COPY frontend/ frontend/
# A Windows checkout can leave CRLF in gradlew, which makes its shebang read "sh\r" ("not found")
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew -PbundleFrontend :app:bootJar -x test -x generateJooq --no-daemon

FROM eclipse-temurin:21-jre
# The base image already has curl, which the health check uses
RUN useradd --system --uid 10001 --home-dir /data rctiming \
 && install -d -o rctiming -g rctiming /data
WORKDIR /app
COPY --from=builder /build/app/build/libs/app.jar app.jar
# The database, backups, uploads, logs and the sign-in key all live here; mount a volume on it
ENV RCTIMING_DATA_DIR=/data
VOLUME /data
USER rctiming
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD curl -sf http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
