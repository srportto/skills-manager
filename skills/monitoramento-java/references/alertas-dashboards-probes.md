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
| readiness | Esta réplica pode atender agora? | `readinessState` + dependências **necessárias** para atender | Dependências opcionais/degradáveis; métricas de carga | Saída do balanceador (sem reinício) |

Liveness com dependência externa transforma uma queda do banco em **reinício de todos os pods** — falha em
cascata que não conserta nada. Readiness baseada em carga (CPU, fila cheia) tira réplicas saturadas e
concentra o tráfego nas restantes; saturação vai para alerta e load shedding, não para readiness.

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
          include: readinessState,db        # só o que é necessário para atender
        operacional:
          include: backlog                  # saturação: alerta/dashboard, fora das probes
  server:
    port: 8081                              # porta de management separada do tráfego (opcional)
```

Spring Boot publica `/actuator/health/liveness` e `/actuator/health/readiness`; no shutdown gracioso, a
readiness passa a `REFUSING_TRAFFIC` automaticamente. Exemplo testado (banco fora → liveness 200, readiness
503; backlog cheio → grupo operacional 503 sem derrubar readiness):
[SaudeAplicacao](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/SaudeAplicacao.java) e
[SaudeAplicacaoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/SaudeAplicacaoTest.java).
Endpoints legados de disponibilidade (ex.: `/disponibilidade` do esqueleto `criar-aplicacao-java`) podem
continuar como smoke test, mas as probes do Kubernetes usam os grupos acima.

