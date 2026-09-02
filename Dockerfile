FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S jobportal && adduser -S jobportal -G jobportal
WORKDIR /app
COPY --from=build /workspace/target/jobportal-0.0.1-SNAPSHOT.jar app.jar

USER jobportal
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
