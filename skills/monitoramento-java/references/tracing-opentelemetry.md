# Tracing distribuído com OpenTelemetry

Leia este arquivo ao configurar tracing distribuído (Micrometer Tracing + OTLP), definir amostragem, criar spans customizados ou propagar contexto entre microsserviços.

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

