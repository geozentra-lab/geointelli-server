FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app/property-service

COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY --from=build /app/property-service/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]