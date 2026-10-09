# The fat jar is built outside the image, because configuring :server pulls in :shared,
# which applies AGP (convention.kmp.multiplatform) and so needs the Android SDK.
# Build the jar first, then the image, from the repo root:
#   ./gradlew :server:buildFatJar
#   docker build -f server/Dockerfile -t tana-spa-server .
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY server/build/libs/*-all.jar app.jar
USER app
EXPOSE 8081
# UTC everywhere: the fat jar is launched directly, so applicationDefaultJvmArgs does not apply here.
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar"]
