# Render (and most free PaaS tiers) cannot build a JVM app natively — only Docker. This is that
# build. It uses the committed Gradle wrapper rather than a `gradle:` base image so the Gradle
# version here can never drift from the one the repo and CI use.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Only the build inputs are copied by name. gradle.properties is deliberately not among them: it is
# a gitignored, machine-local file that pins org.gradle.java.home to a macOS JDK path, which does
# not exist in this image.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

# Tests need Docker (Testcontainers) and cannot run inside the build container. CI covers them.
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/build/libs/*.jar app.jar
USER app

# The free tier is a hard 512 MB. Left alone the JVM sizes its heap against the host's memory, not
# the container's, and gets OOM-killed under the first real request.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

# The platform assigns the port at runtime via $PORT; 8080 is the local fallback.
ENTRYPOINT ["sh", "-c", "exec java -jar app.jar --server.port=${PORT:-8080}"]
