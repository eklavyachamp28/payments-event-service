# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY pom.xml .
RUN apt-get update -qq && apt-get install -y -qq maven >/dev/null && mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /workspace/target/payments-event-service-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
