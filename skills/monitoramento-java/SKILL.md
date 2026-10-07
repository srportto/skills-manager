---

name: monitoramento-java
description: "Configura os três pilares de observabilidade em Java/Spring Boot — logs estruturados, métricas Micrometer + Prometheus (RED/USE), tracing distribuído com OpenTelemetry — e a stack ao redor (Grafana, alerting rules, probes liveness/readiness). Use ao instrumentar serviço, adicionar métricas, configurar alertas ou investigar incidente. Uso: agents `engenheiro-devops` (variante `k8s`)/`especialista-monitoramento` ou `/monitoramento-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.1.0"
  domain: observability
  triggers: monitoramento, observabilidade, Prometheus, Grafana, OpenTelemetry, tracing distribuído, alert, MTTR, Micrometer, RED, USE
  role: specialist
  scope: observability
  output-format: code
  related-skills: padrao-de-logs-java, arquitetura-limpa-java, devops-cicd
---

# Monitoramento de Aplicações Java

## Visão geral

Guia de observabilidade para aplicações Java/Spring Boot: as **três pillars** (logs estruturados,
métricas, tracing distribuído) e como instrumentá-las no stack deste catálogo (Spring Boot 4,
Micrometer, OpenTelemetry, Prometheus, Grafana).

**Quando NÃO usar:** para o **padrão de logging** (formato JSON, MDC, o que logar por camada), use
`padrao-de-logs-java` — esta skill só mostra como exportar/consultar os logs numa stack de
observabilidade. Para **correlação ponta a ponta entre microsserviços** (correlation ID middleware,
propagação de `X-Trace-Id`), use `arquitetura-limpa-java`. Para tuning de banco, use
`banco-de-dados-performance`.

## Workflow de instrumentação

1. **Avalie** — SLIs do serviço, caminhos críticos, métricas de negócio (não só técnicas).
2. **Instrumente** — adicione logs, métricas e traces (ver exemplos abaixo).
3. **Colete** — agregação/storage (Prometheus scrape, log shipper, OTLP); **valide que o dado chega**.
4. **Visualize** — dashboards RED para serviços user-facing, USE para recursos.
5. **Alerte** — threshold + anomalia em caminhos críticos; **valide que não há falso positivo**.

# Logs estruturados (resumo)

Já cobertos em detalhe na skill `padrao-de-logs-java`. Em uma stack de observabilidade o que muda
é **para onde os logs vão**: `logging.structured.format.console: logstash` (ou `ecs` para Elastic
Common Schema) já produz 1 linha JSON por evento com MDC `traceId` automático:
`{"timestamp":"...","level":"INFO","logger":"...","message":"Order created","traceId":"trace-xyz",
"orderId":12345,"duration_ms":45}`.

**Não confunda "campo no JSON" com "placeholder no log".** O que vira campo JSON de verdade é o
que está no MDC (ver `padrao-de-logs-java`, seção 4) — placeholders `{}` viram texto do campo
`message`, não campos separados.

# Métricas com Micrometer + Prometheus

Spring Boot 4 já traz `spring-boot-starter-actuator` + Micrometer; expõe `/actuator/prometheus`
automaticamente ao adicionar `micrometer-registry-prometheus`. As métricas mais importantes para um
serviço HTTP são as **RED**: **R**ate (requests/s por endpoint), **E**rrors (ratio de erros por
endpoint), **D**uration (latência p50/p95/p99).

## Instrumentação custom

```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class PedidoService {
    private final Counter pedidosCriados;
    private final Timer processamentoTimer;

    public PedidoService(MeterRegistry registry) {
        // Nomes Micrometer usam ponto; o registry Prometheus converte para pedidos_criados_total.
        this.pedidosCriados = Counter.builder("pedidos.criados")
                .description("Total de pedidos criados com sucesso").register(registry);
        this.processamentoTimer = Timer.builder("pedido.processamento")
                .description("Latência do processamento de pedido")
                .publishPercentileHistogram().register(registry);
    }

    public Pedido criar(Pedido pedido) {
        return processamentoTimer.record(() -> {
            Pedido criado = salvar(pedido);
            pedidosCriados.increment();
            return criado;
        });
    }
}
```

## Endpoint de scrape

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true   # habilita p50/p95/p99 no histograma de latencia
```

Prometheus scrape config (exemplo mínimo): `job_name`, `metrics_path: /actuator/prometheus`,
`scrape_interval: 15s`, `static_configs.targets: ['app-pedidos:8080']`.

## Tipos de métrica — quando usar cada um

| Tipo | Quando usar | Exemplo |
|---|---|---|
| **Counter** | Valor que só cresce (total de eventos) | `pedidos_criados_total`, `erros_total` |
| **Gauge** | Valor que oscila (tamanho atual, valor instantâneo) | `pedidos_em_processamento`, `conexoes_ativas` |
| **Histogram / Summary** | Distribuição de valores (latência, tamanho) | `pedido_processamento_seconds` (com p50/p95/p99) |
| **Timer** | Caso especial de Histogram para duração | Tempo de uma operação |

**Erro comum:** usar **Gauge** para algo que deveria ser **Counter**. Contadores são a base de
todas as agregações por `rate()` no PromQL — usar Gauge para "total de eventos" quebra o `rate()`.

## Cardinalidade e saturação

- **Labels só com conjuntos fechados** (operação, resultado, dependência, status class). `traceId`, id de
  pedido/usuário, path com id ou mensagem de erro **nunca** viram label: cada valor novo cria uma série
  temporal nova e derruba o Prometheus. Para chegar de uma métrica a um exemplo concreto use **exemplars**
  (o Micrometer anexa o `traceId` ao bucket do histograma quando há tracing ativo), logs e traces.
- **Requisição lógica × tentativa:** conte a requisição do usuário uma vez (resultado final) e as tentativas à
  dependência em outra métrica; retries não podem esconder nem inflar a taxa de erro.
- **Rejeição é resultado, não ausência:** 429/503 por saturação entram no denominador do SLI.
- **Saturação** (o "S" de USE) é o sinal que antecede a queda: fila em itens/bytes/**idade**, tarefas ativas
  vs. limite, espera por conexão do pool, lag de consumo, breaker aberto, uso do fallback, tamanho da DLQ.

Exemplo executável com essas regras (testes provam cardinalidade fechada, rejeição no denominador e
separação lógica × tentativa):
[MetricasProtecao](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/MetricasProtecao.java).
SLO, burn rate, alertas e runbook: [SLO e saturação](references/slo-saturacao-java.md).

# Tracing distribuído com OpenTelemetry

Tracing distribuído segue uma requisição **fim a fim** entre microsserviços, mostrando onde o tempo
foi gasto. Cada **span** é uma unidade de trabalho com timestamps de início/fim; um **trace** é uma
árvore de spans com o mesmo `traceId`.

## Dependências

Dependências Maven: `io.micrometer:micrometer-tracing-bridge-otel` +
`io.opentelemetry:opentelemetry-exporter-otlp`.

## Configuração

```yaml
management:
  tracing:
    sampling:
      probability: 0.1   # decisão de volume/custo/diagnóstico — ver abaixo
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

**Sampling é decisão, não número universal.** Considere volume de requisições, custo de armazenamento e o que
precisa ser diagnosticado: serviço de baixo volume pode manter 100%; alto volume costuma usar 1–10% de
*head sampling* na aplicação. **Tail sampling** no OpenTelemetry Collector (guardar 100% dos traces com erro ou
lentos e uma amostra dos demais) preserva justamente os casos que importam. O `traceId` continua em todos os
logs, mesmo de requisições não amostradas. Confira o nome das propriedades na versão do Boot em uso. Atributos
de alta cardinalidade (id do pedido) são bem-vindos em **spans** e logs — não em labels de métricas.

## Spans customizados em código

```java
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;

@Service
public class PedidoService {
    private final Tracer tracer;

    public PedidoService(Tracer tracer) { this.tracer = tracer; }

    public Pedido criar(Pedido pedido) {
        Span span = tracer.nextSpan().name("pedido.criar").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("pedido.id", pedido.id().toString());
            Pedido criado = salvar(pedido);
            span.event("pedido.salvo");
            return criado;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

## Propagação de contexto (entre microsserviços)

O Micrometer Tracing + OpenTelemetry injeta automaticamente o `traceparent` header (W3C Trace
Context) nas requisições HTTP de saída via `RestClient` (Boot 4) ou `WebClient`. Baggage (campos
extras que viajam com o trace, ex.: `userId`) se configura em
`management.tracing.baggage.remote-fields`.

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
[SaudeAplicacao](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/SaudeAplicacao.java) e
[SaudeAplicacaoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/SaudeAplicacaoTest.java).
Endpoints legados de disponibilidade (ex.: `/disponibilidade` do esqueleto `criar-aplicacao-java`) podem
continuar como smoke test, mas as probes do Kubernetes usam os grupos acima.

# Constraints

## MUST DO
- Use logs estruturados (JSON) — texto livre é parseável, mas estruturado é **filtrável**.
- Inclua `traceId` em logs e traces; em métricas, só via exemplars (nunca como label).
- Defina SLI/SLO por operação e alerte por consumo do orçamento de erro (burn rate), com runbook.
- Meça saturação (fila/idade, tarefas ativas, espera de pool, lag) e rejeições.
- Configure alertas em caminhos críticos (latência, taxa de erro, saturação).
- Monitore **métricas de negócio**, não só técnicas (`pedidos_criados_total` > `jvm_memory_used`).
- Use o tipo de métrica correto (counter/gauge/histogram/timer).
- Implemente endpoints de health check (liveness **e** readiness separados).
- Propague `traceparent` entre microsserviços (W3C Trace Context).

## MUST NOT DO
- Logar dados sensíveis (senhas, tokens, PII) — ver `padrao-de-logs-java`.
- Alertar em todo erro (alert fatigue) — defina threshold + `for` duration para evitar flapping.
- Usar Gauge onde Counter é o correto (quebra `rate()` no PromQL).
- Pular correlation ID em sistemas distribuídos.
- Escolher taxa de sampling sem considerar volume, custo e necessidade de diagnóstico (prefira tail
  sampling para manter erros e lentidão).
- Usar id, path dinâmico, `traceId` ou mensagem de erro como label de métrica.
- Acoplar liveness a dependências externas ou readiness a métricas de carga.
- Misturar dashboards RED e USE sem critério — defina por serviço qual faz sentido.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Adicionar métrica Micrometer em código | sessão principal | esta skill |
| Configurar stack Prometheus + Grafana + OTel Collector | sessão principal | esta skill |
| Padronizar formato de log + MDC | sessão principal | `padrao-de-logs-java` |
| Auditar instrumentação existente de um serviço | agent `java-revisor` (modo `auditoria`) | esta skill + `padrao-de-logs-java` |
| Definir/alertas de SLO | sessão principal | esta skill |
