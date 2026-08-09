# Build stage — compiles the jar. Uses the Maven cache layer trick: copy just
# the pom first so `mvn dependency:go-offline` is cached across builds where
# only source files changed, not dependencies.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Runtime stage — slim JRE only, no build tooling, no source.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Runs as a non-root user rather than the container default root.
RUN addgroup -S rbms && adduser -S rbms -G rbms
COPY --from=build /build/target/rbms-*.jar app.jar
RUN mkdir -p /app/uploads && chown -R rbms:rbms /app
USER rbms

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
