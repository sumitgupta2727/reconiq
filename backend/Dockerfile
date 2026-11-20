# ---- Build stage: compile the Spring Boot jar (includes H2 + MySQL drivers) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests -Ph2 package

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/reconiq-backend-1.0.0.jar app.jar
EXPOSE 8080
# Hosts like Render/Railway provide PORT; fall back to 8080 locally.
CMD ["sh", "-c", "java -Xmx400m -Dserver.port=${PORT:-8080} -jar app.jar"]
