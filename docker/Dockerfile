# Build environment for the original RFTools toolchain (ForgeGradle 2.3 needs Java 8).
FROM eclipse-temurin:8-jdk
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
ENV GRADLE_USER_HOME=/gradle
WORKDIR /src
