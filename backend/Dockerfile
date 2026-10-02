FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY maven-settings.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -s maven-settings.xml package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/review-assistant-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
