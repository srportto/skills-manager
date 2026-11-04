# Logs: MDC e correlação

Leia este arquivo ao propagar `traceId` e outros campos de contexto nos logs via MDC (filtro HTTP, consumer de fila, threads assíncronas) e ao decidir o que vira campo JSON. Regras de formato e nível ficam em `logs-estruturados.md`.

## 4. MDC e correlação

MDC (`Mapped Diagnostic Context`, do SLF4J) é um contexto por thread: tudo que você coloca nele com
`MDC.put(chave, valor)` aparece automaticamente como campo de nível superior no JSON de todas as
linhas de log emitidas naquela thread, sem repetir o valor em cada chamada de `log.info`. Combinado
com `logging.structured.format.console: logstash` (seção 2), não exige configuração extra.

**Padrão preferido: Micrometer Tracing (OpenTelemetry).** Com `micrometer-tracing-bridge-otel` no classpath,
o Spring Boot propaga o header **W3C Trace Context** (`traceparent`) entre serviços — HTTP e, com observação
habilitada nos listeners, Kafka/SQS — e coloca `traceId`/`spanId` no MDC sozinho; o JSON estruturado já os
inclui. Não crie um segundo identificador concorrente. Ver `monitoramento-java` (tracing).

**Sem tracing** (app mínima), o filtro abaixo popula um `traceId` no MDC na borda (driving adapter,
`infrastructure/web`), reaproveita um id recebido via header quando **válido** e sempre limpa o MDC no
`finally` — sem isso, como o servlet container reaproveita threads de um pool, o `traceId` vazaria para a
próxima requisição. Header vindo do cliente é entrada não confiável: valide formato e tamanho antes de
colocá-lo em log (evita log injection e ids gigantes):

```java
package br.com.srportto.appbase.shared.filters;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

// popula traceId no MDC para toda a requisicao; o campo aparece sozinho no log JSON (logstash)
@Component
public class TraceIdFilter implements Filter {

    private static final String CABECALHO_TRACE_ID = "X-Trace-Id";
    private static final String CHAVE_MDC = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String recebido = ((HttpServletRequest) request).getHeader(CABECALHO_TRACE_ID);
        // Aceita só um formato conhecido e curto; qualquer outra coisa gera um id novo.
        String traceId = (recebido != null && recebido.matches("[A-Za-z0-9-]{8,64}"))
                ? recebido : UUID.randomUUID().toString();
        MDC.put(CHAVE_MDC, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // MDC e por thread; sem clear() o valor vaza para a proxima requisicao no mesmo pool
            MDC.clear();
        }
    }
}
```

`@Component` é suficiente — o Spring Boot registra automaticamente qualquer bean `Filter` na cadeia
de filtros da aplicação, sem precisar de `FilterRegistrationBean`.

**Logs não são métricas:** `traceId`, ids de pedido e usuário são ótimos campos de log (alta cardinalidade é
normal em logs); como **label de métrica**, derrubam o Prometheus — ver `monitoramento-java` (cardinalidade).
Em rejeições por sobrecarga, logue de forma **amostrada ou agregada** (um log por segundo com a contagem), não
uma linha por requisição rejeitada: sob pico, o log vira mais uma fonte de saturação.

**Entrypoints sem servlet (SQS/Kafka):** listeners como `PedidoSqsListener` não passam por essa
cadeia de filtro HTTP. O mesmo padrão se aplica manualmente no início do método do listener —
`MDC.put("traceId", ...)` (gerado ou lido de um atributo da mensagem) antes de chamar o service, e
`MDC.clear()` no `finally` — pelo mesmo motivo: threads de consumer também são reaproveitadas de um
pool.

