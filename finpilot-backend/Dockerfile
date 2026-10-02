# Build stage: compile the Spring Boot jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S finpilot && adduser -S finpilot -G finpilot

COPY --from=build /app/target/*.jar app.jar

USER finpilot

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
