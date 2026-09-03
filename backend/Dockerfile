FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Run as a non-root user (audit L4).
RUN addgroup -S orbit && adduser -S orbit -G orbit && chown -R orbit:orbit /app
USER orbit
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
