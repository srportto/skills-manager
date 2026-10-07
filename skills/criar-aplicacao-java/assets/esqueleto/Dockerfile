# Multi-stage Java 25 — mesmo padrão de devops-cicd (seção Docker).
# Stage 1: build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
# Os testes rodam no CI ANTES do build da imagem (gate); aqui só se empacota o que já passou.
RUN mvn clean package -DskipTests

# Stage 2: runtime — -jre-alpine já traz wget (busybox), necessário ao HEALTHCHECK.
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
