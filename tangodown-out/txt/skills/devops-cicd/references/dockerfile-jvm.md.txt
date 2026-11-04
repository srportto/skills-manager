# Dockerfile JVM (multi-stage)

Leia este arquivo quando for escrever ou revisar o Dockerfile de uma app Java (variante `docker`): multi-stage, JRE enxuta, usuário não-root, HEALTHCHECK e `.dockerignore`. Arquivos prontos: [Dockerfile](../assets/Dockerfile) e [.dockerignore](../assets/.dockerignore).

## Dockerfile multi-stage (Java 25)

```dockerfile
# Stage 1: build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
# Os testes rodam no job de CI ANTES do build da imagem (gate obrigatório); aqui só se empacota o que já
# passou. Imagem construída com -DskipTests não é evidência de qualidade nem de resiliência.
RUN mvn clean package -DskipTests

# Stage 2: runtime — -jre-alpine é mais enxuta e já traz wget via busybox (variante -jre
# Ubuntu/Debian NÃO tem wget/curl, o que quebra o HEALTHCHECK só em runtime, não no build).
FROM eclipse-temurin:25-jre-alpine
# Alpine/busybox cria usuário com addgroup/adduser, não groupadd/useradd (exigem pacote shadow).
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
USER app
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

## `.dockerignore` (sempre!)

```
target/
.git/
*.log
.env
```

## Regras não negociáveis

- **Multi-stage sempre** — nunca incluir Maven/JDK na imagem de runtime.
- **Usuário não-root** na imagem final.
- **HEALTHCHECK** apontando para a liveness do Actuator (`/actuator/health/liveness`) — nunca para algo que
  dependa de banco ou serviço externo; confirme que `wget`/`curl` existe na imagem final. (Em Kubernetes o
  HEALTHCHECK do Docker é ignorado; valem as probes.)

## Validação

```bash
docker build -t minha-app:test .
docker run --rm -p 8080:8080 minha-app:test
curl http://localhost:8080/actuator/health/readiness   # pronto para tráfego?
curl http://localhost:8080/disponibilidade             # smoke test funcional do esqueleto (legado)
```

## Flags de memória da JVM no container

```dockerfile
# ANTES — heap padrão da JVM pode ignorar o limite do container
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# DEPOIS — heap dimensionado como fração do limite (resto fica para metaspace, threads e overhead)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

No Kubernetes o mesmo valor pode vir por `JAVA_TOOL_OPTIONS` (ver [kubernetes-manifests](kubernetes-manifests.md));
os dois são idênticos de propósito.

## Arquivos prontos e fonte única

- [Dockerfile](../assets/Dockerfile) — versão com `EXPOSE` e `MaxRAMPercentage`. O esqueleto de
  `criar-aplicacao-java` carrega uma **cópia idêntica** em
  [esqueleto/Dockerfile](../../criar-aplicacao-java/assets/esqueleto/Dockerfile) (buildada como prova por
  `mvn -f examples/java/pom.xml verify`); altere os dois juntos.
- [.dockerignore](../assets/.dockerignore) — mantém `target/`, `.git/`, logs e `.env` fora do contexto de build.
