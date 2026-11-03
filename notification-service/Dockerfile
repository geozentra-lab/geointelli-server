FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system notification && useradd --system --gid notification --home-dir /app notification
WORKDIR /app
COPY --from=build --chown=notification:notification /build/target/notification-service-*.jar /app/app.jar
USER notification
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
