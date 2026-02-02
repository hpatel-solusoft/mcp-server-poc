# --- Stage 1: The Builder (Compiles the code) ---
# We use Maven with JDK 21 because your pom.xml specifies <java.version>21
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 1. Copy POM and download dependencies first (Optimization)
# This layer is cached so we don't re-download jars every time we change a line of code
COPY pom.xml .
RUN mvn dependency:go-offline

# 2. Copy Source Code (including the WSDL in src/main/resources)
COPY src ./src

# 3. Build the JAR
# This triggers 'cxf-codegen-plugin' to generate sources from Case360.wsdl
RUN mvn clean package -DskipTests

# --- Stage 2: The Runtime (Runs the app) ---
# We use a lightweight Java 21 JRE for the final image
FROM eclipse-temurin:21-jre-alpine

# Security: Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# Copy the built JAR from the builder stage
# We use a wildcard *.jar because the version in pom.xml might change (0.0.1-SNAPSHOT)
COPY --from=builder /app/target/*.jar app.jar

# Your application.properties says server.port=9090
EXPOSE 9090

# Entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]