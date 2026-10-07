# Métricas com Micrometer + Prometheus

Leia este arquivo ao criar ou revisar métricas de aplicação: RED, instrumentação custom com Micrometer, endpoint de scrape, escolha do tipo de métrica, cardinalidade e saturação. SLO, burn rate e alertas estão em `slo-saturacao-java.md`.

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
[MetricasProtecao](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/MetricasProtecao.java).
SLO, burn rate, alertas e runbook: [SLO e saturação](slo-saturacao-java.md).

