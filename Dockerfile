FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S familyvault && adduser -S familyvault -G familyvault \
    && mkdir -p /app/uploads \
    && chown -R familyvault:familyvault /app

COPY --from=build --chown=familyvault:familyvault /workspace/target/familyvault.jar /app/app.jar

USER familyvault
VOLUME ["/app/uploads"]
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
