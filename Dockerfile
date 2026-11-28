
# Etapa 1: compilación con Maven y Java 25
FROM maven:3.9-eclipse-temurin-25 AS compilacion

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Etapa 2: ejecución con Java 25
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=compilacion /app/target/sillon-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
