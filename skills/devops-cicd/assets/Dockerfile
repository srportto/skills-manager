# Dockerfile multi-stage Java 25 — pronto para copiar. O esqueleto de criar-aplicacao-java carrega uma cópia
# idêntica em skills/criar-aplicacao-java/assets/esqueleto/Dockerfile (mantenha os dois iguais).
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
# Heap como fração do limite de memória do container (evita OOMKill por heap superdimensionado).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
