package br.com.srportto.exemplos;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Caso executável integrado (estudo de checkout): admissão limitada com 503 + Retry-After, deadline, idempotência
 * transacional com outbox, métricas de proteção e probes. Uma única aplicação; o desenho completo e as alternativas
 * estão em design-system-architecture → estudos de caso.
 */
@SpringBootApplication(proxyBeanMethods = false)
public class CheckoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }

    /** Porta de saída para o provedor de pagamento (simulado neste exemplo). */
    @FunctionalInterface
    public interface ProvedorPagamento {
        void autorizar() throws Exception;
    }

    public record PedidoRequest(long centavos) {}

    public record PedidoResponse(String id) {}

    @Bean
    ProcessadorIdempotente processadorIdempotente(DataSource banco) throws SQLException {
        var processador = new ProcessadorIdempotente(banco);
        processador.preparar();
        return processador;
    }

    /** Capacidade de requisições simultâneas por instância (somar réplicas no orçamento do banco). */
    @Bean
    AdmissaoPorPrioridade admissao(@Value("${checkout.capacidade:16}") int capacidade) {
        return new AdmissaoPorPrioridade(capacidade, 0, Duration.ofMillis(5));
    }

    @Bean
    MetricasProtecao metricasProtecao(MeterRegistry registro) {
        return new MetricasProtecao(registro, Set.of("criar-pedido"), Set.of("pagamentos"));
    }

    /** Latência simulada do provedor: só para laboratório (ensaio de carga), nunca default de produção. */
    @Bean
    ProvedorPagamento provedorPagamento(@Value("${checkout.pagamento.latencia:0ms}") Duration latencia) {
        return () -> {
            if (!latencia.isZero()) Thread.sleep(latencia);
        };
    }

    @RestController
    static class PedidosController {
        private static final Duration DEADLINE = Duration.ofSeconds(2);

        private final AdmissaoPorPrioridade admissao;
        private final MetricasProtecao metricas;
        private final ProvedorPagamento pagamento;
        private final ProcessadorIdempotente processador;

        PedidosController(AdmissaoPorPrioridade admissao, MetricasProtecao metricas, ProvedorPagamento pagamento,
                          ProcessadorIdempotente processador) {
            this.admissao = admissao;
            this.metricas = metricas;
            this.pagamento = pagamento;
            this.processador = processador;
        }

        @PostMapping("/pedidos")
        ResponseEntity<PedidoResponse> criar(@RequestHeader("Idempotency-Key") String chave,
                                             @RequestHeader("X-Tenant") String tenant,
                                             @RequestBody PedidoRequest pedido) throws Exception {
            // X-Tenant simplifica o exemplo; em produção o tenant vem do token autenticado.
            String id = metricas.medir("criar-pedido", () -> {
                // Admissão dentro da medição: rejeição entra no denominador do SLI.
                try (var permissao = admissao.admitir(AdmissaoPorPrioridade.Prioridade.CRITICA, DEADLINE)) {
                    pagamento.autorizar();
                    metricas.tentativa("pagamentos", true);
                    return processador.processar(tenant, chave, pedido.centavos());
                }
            });
            return ResponseEntity.status(HttpStatus.CREATED).body(new PedidoResponse(id));
        }

        @GetMapping("/disponibilidade")
        Map<String, String> disponibilidade() {
            return Map.of("aplicacao", "checkout", "status", "DISPONIVEL");
        }

        @ExceptionHandler(AdmissaoPorPrioridade.Rejeitada.class)
        ResponseEntity<ProblemDetail> saturado(AdmissaoPorPrioridade.Rejeitada rejeicao) {
            var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "Checkout temporariamente sem capacidade; tente novamente.");
            problema.setProperty("motivo", rejeicao.motivo().name());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1").body(problema);
        }

        @ExceptionHandler(ProcessadorIdempotente.ConflitoDeChave.class)
        ResponseEntity<ProblemDetail> conflito(ProcessadorIdempotente.ConflitoDeChave conflito) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outro payload."));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<ProblemDetail> invalido(IllegalArgumentException erro) {
            // 422 (RFC 9110: "Unprocessable Content").
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Pedido inválido."));
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        ResponseEntity<ProblemDetail> cabecalhoAusente(MissingRequestHeaderException erro) {
            return ResponseEntity.badRequest()
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Cabeçalho obrigatório ausente: " + erro.getHeaderName()));
        }
    }
}
