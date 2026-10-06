FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build
WORKDIR /build
COPY . .
ARG SERVICE
RUN chmod +x mvnw && ./mvnw -B -pl "$SERVICE" -am -Dmaven.test.skip=true package && cp "$SERVICE/target/$SERVICE-0.1.0-SNAPSHOT.jar" /app.jar
FROM eclipse-temurin:21.0.8_9-jre-jammy
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
