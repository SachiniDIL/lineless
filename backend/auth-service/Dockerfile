FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S lineless && adduser -S lineless -G lineless

WORKDIR /app
COPY --from=build --chown=lineless:lineless /workspace/target/auth-service-*.jar app.jar

USER lineless

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
