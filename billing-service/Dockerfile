# =========================
# BUILD STAGE
# =========================

FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# cache dependencies
COPY pom.xml .

RUN mvn dependency:go-offline

# source code
COPY src ./src

# build jar
RUN mvn clean package -DskipTests

# =========================
# RUNTIME STAGE
# =========================

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8083

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]