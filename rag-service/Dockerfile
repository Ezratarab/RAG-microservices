# =========================
# BUILD STAGE
# =========================

FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# cache dependencies first
COPY pom.xml .

RUN mvn dependency:go-offline

# copy source
COPY src ./src

# build jar
RUN mvn clean package -DskipTests

# =========================
# RUNTIME STAGE
# =========================

FROM eclipse-temurin:17-jre

WORKDIR /app

# copy jar
COPY --from=builder /app/target/*.jar app.jar

# expose service port
EXPOSE 8083

# JVM tuning for containers + big workloads (RAG + embeddings)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# start app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]