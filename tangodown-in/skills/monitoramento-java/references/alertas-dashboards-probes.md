# Alertas, dashboards e probes de saúde

Leia este arquivo ao escrever regras de alerta Prometheus, escolher dashboards RED/USE ou configurar probes liveness/readiness/startup. O arquivo pronto de regras está em `assets/alertas-prometheus.yml` e o de propriedades em `assets/application-observabilidade.yml`.

# Alerting rules (Prometheus)

```yaml
groups:
  - name: app-pedidos.rules
    rules:
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{application="pedidos", status=~"5.."}[5m])
          / rate(http_server_requests_seconds_count{application="pedidos"}[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Error rate acima de 5% em {{ $labels.uri }} por 2 minutos consecutivos"
      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket{application="pedidos"}[5m])) by (le, uri)
          ) > 1.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "p99 acima de 1s em {{ $labels.uri }}"
```

# Dashboards

| Method | Quando usar | Métricas (PromQL) |
|---|---|---|
| **RED** | Cada endpoint/serviço user-facing | Rate: `rate(http_server_requests_seconds_count[1m])`; Errors: `rate(..._count{status=~"5.."}[1m]) / rate(_count[1m])`; Duration: `histogram_quantile(0.95, rate(..._bucket[5m]))` |
| **USE** | Cada recurso/infra (CPU, memória, disco, pool de conexões) | Utilization: % em uso; Saturation: fila/espera; Errors: eventos de erro |

# Health & readiness probes

Semântica única do catálogo (usada por `devops-cicd`, `arquitetura-limpa-java` e `criar-aplicacao-java`):

| Probe | Pergunta | Inclui | Nunca inclui | Falha causa |
|---|---|---|---|---|
| startup | A aplicação terminou de subir? | estado de inicialização | — | Espera, sem reiniciar cedo demais |
| liveness | O processo está são? | `livenessState` | Banco, broker, cache, APIs externas | **Reinício** do container |
| readiness | **Esta réplica** pode atender agora? | `readinessState` + o que só esta réplica pode perder (sidecar/proxy local, cache ou arquivo local, aquecimento) | Dependências **compartilhadas** por todas as réplicas (o mesmo banco, broker, API externa); dependências opcionais; métricas de carga | Saída do balanceador (sem reinício) |

Liveness com dependência externa transforma uma queda do banco em **reinício de todos os pods** — falha em
cascata que não conserta nada. Readiness baseada em carga (CPU, fila cheia) tira réplicas saturadas e
concentra o tráfego nas restantes; saturação vai para alerta e load shedding, não para readiness.

**Dependência compartilhada fora da readiness.** Se o banco comum a todas as réplicas entra na readiness, a
queda dele deixa **todas** as réplicas unready ao mesmo tempo: o Service fica sem endpoints, o cliente recebe
conexão recusada ou um 502/503 genérico do ingress (sem `Retry-After`, sem Problem Details) e até as rotas que não
precisam do banco param. Readiness só resolve o que outra réplica consegue atender. Para dependência
compartilhada, a decisão é **degradação explícita na aplicação**:

| Dependência fora | Comportamento declarado |
|---|---|
| Rota que precisa dela | Falha rápida (timeout de pool/cliente curto, breaker aberto) → **503** + `Retry-After` + Problem Details, contado como `rejeitada`/`dependencia_indisponivel` |
| Rota com fallback honesto | Responde degradado e sinalizado (dado em cache com idade, lista parcial). Nunca inventa sucesso de efeito |
| Rota que não precisa dela | Continua 200 |
| Probes | Liveness 200, readiness 200; o estado da dependência vai para um grupo de diagnóstico (`dependencias`) com alerta, fora das probes |

Inclua na readiness apenas a falha que **só esta réplica** sofre. Exemplos: o sidecar dela caiu, o certificado
local expirou, o cache local ainda está aquecendo. Se mesmo assim decidir pôr uma dependência compartilhada na
readiness, registre no ADR que o Service inteiro sai do ar quando ela cair e o que o cliente recebe nesse caso.

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true            # automático em Kubernetes; explícito para rodar igual fora dele
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState               # dependência compartilhada (db) fica fora: degradação na aplicação
        dependencias:
          include: db                           # diagnóstico + alerta; não é probe
        operacional:
          include: backlog                      # saturação: alerta/dashboard, fora das probes
  server:
    port: 8081                              # porta de management separada do tráfego (opcional)
```

```java
// Degradação explícita: banco fora → 503 + Retry-After em Problem Details só nas rotas que precisam dele
@RestControllerAdvice
class DependenciaIndisponivelHandler {
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> bancoIndisponivel(Exception erro) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Dependência indisponível; tente novamente em instantes.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problema);
    }
}
```

`CannotGetJdbcConnectionException` (JDBC) é subclasse de `DataAccessResourceFailureException`; com
`@Transactional`, a falha ao abrir a conexão chega como `CannotCreateTransactionException`. O
`connection-timeout` curto do pool (ex.: 2 s) é o que torna a falha rápida.

Spring Boot publica `/actuator/health/liveness` e `/actuator/health/readiness`; no shutdown gracioso, a
readiness passa a `REFUSING_TRAFFIC` automaticamente. Exemplo testado (banco fora → liveness 200, readiness
200, grupo `dependencias` 503 e rota que usa o banco 503 + `Retry-After`, enquanto a rota sem banco segue 200;
backlog cheio → grupo operacional 503 sem derrubar readiness):
[SaudeAplicacao](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/SaudeAplicacao.java) e
[SaudeAplicacaoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/SaudeAplicacaoTest.java).
Endpoints legados de disponibilidade (ex.: `/disponibilidade` do esqueleto `criar-aplicacao-java`) podem
continuar como smoke test, mas as probes do Kubernetes usam os grupos acima.
