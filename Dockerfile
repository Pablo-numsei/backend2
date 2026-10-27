# TableHub API — Render Docker deployment (Java 21 + Spring Boot).
# Builds in Render; no local Docker installation required.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

# Limit Java heap so the app has a chance to start on Render's small Free instance.
# Spring Boot + Hibernate may still exceed 512 MB; use a larger instance if needed.
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
# Render supplies PORT. Bind to all interfaces; localhost-only is not reachable.
ENTRYPOINT ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT:-10000} --server.address=0.0.0.0"]
