# A08 — Software and Data Integrity Failures e dependências vulneráveis

Leia este arquivo quando tratar desserialização, migração de schema em produção ou configurar varredura de CVEs (Dependency-Check, Snyk, Dependabot) e SLA de remediação.

## A08 — Integridade

- **Dependências:** escanear CVEs conhecidos — ver seção "Dependências vulneráveis (CVEs)" abaixo.
- **Updates de schema:** Flyway/Liquibase com checksum verificado, nunca `ddl-auto: update` em
  produção.
- **Deserialização:** nunca `ObjectInputStream`/`readObject` com dados do client; prefira JSON com
  Jackson (`@JsonIgnoreProperties(ignoreUnknown = true)`).

```java
// ANTES - desserializacao nativa de bytes vindos do cliente: execucao de codigo via gadget chain
try (var in = new ObjectInputStream(request.getInputStream())) {
    Pedido pedido = (Pedido) in.readObject();
}

// DEPOIS - JSON para um record tipado; so os campos conhecidos, sem instanciar classe arbitraria
public record PedidoRequest(@NotBlank String sku, @Positive int quantidade) {}

@PostMapping("/pedidos")
ResponseEntity<Void> criar(@Valid @RequestBody PedidoRequest req) { /* ... */ return ResponseEntity.accepted().build(); }
```

```yaml
# ANTES - Hibernate altera o schema sozinho em producao (sem revisao, sem rollback)
spring.jpa.hibernate.ddl-auto: update

# DEPOIS - schema so muda por migration versionada e validada
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    validate-on-migrate: true   # falha se o checksum de uma migration ja aplicada mudou
```

## Dependências vulneráveis (CVEs)

Inclua na rotina do projeto (CI ou pre-commit):

```bash
# OWASP Dependency-Check (Maven)
mvn org.owasp:dependency-check-maven:check

# Snyk (CLI ou via GitHub Action)
snyk test

# GitHub Dependabot (PRs automaticas para versoes novas)
# .github/dependabot.yml
```

Atualize dependências com CVEs críticos/altos **antes do merge** — mantenha uma SLA de
remediação (ex.: crítico em 24h, alto em 7 dias).

```yaml
# .github/dependabot.yml — PRs semanais para Maven e GitHub Actions
version: 2
updates:
  - package-ecosystem: maven
    directory: "/"
    schedule:
      interval: weekly
  - package-ecosystem: github-actions
    directory: "/"
    schedule:
      interval: weekly
```

```xml
<!-- pom.xml — o build falha com CVSS >= 7 (alto/critico): o gate bloqueia o merge -->
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <configuration>
    <failBuildOnCVSS>7</failBuildOnCVSS>
  </configuration>
</plugin>
```

Use a versão atual do plugin (declare `<version>` fixada, de preferência via `pluginManagement`);
a primeira execução baixa a base NVD e é lenta — configure a chave de API da NVD no CI.
