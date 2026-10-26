FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY mongo-fields-annotations mongo-fields-annotations
COPY mongo-fields-processor mongo-fields-processor
COPY libs libs
COPY src src
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

FROM build AS test
RUN --mount=type=cache,target=/root/.gradle ./gradlew test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup && mkdir /app/uploads && chown -R appuser:appgroup /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
