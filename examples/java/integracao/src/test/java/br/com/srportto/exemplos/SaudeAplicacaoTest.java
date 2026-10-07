package br.com.srportto.exemplos;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.health.contributor.Health;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Health groups reais (Actuator): liveness não depende de serviço externo; readiness não inclui o banco
 * compartilhado (a queda dele tiraria todas as réplicas do Service), que degrada explicitamente na rota;
 * saturação/backlog fica num grupo operacional (alerta), sem tirar pods do ar.
 */
@SpringBootTest(classes = SaudeAplicacaoTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.name=saude-aplicacao-teste")
class SaudeAplicacaoTest {
    static final AtomicBoolean BANCO_FORA = new AtomicBoolean();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SaudeAplicacao.class, PedidosController.class})
    static class App {
        @Bean
        DataSource dataSource() {
            var h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
            // Banco "desligável" para simular indisponibilidade da dependência.
            return new DelegatingDataSource(h2) {
                @Override
                public Connection getConnection() throws SQLException {
                    if (BANCO_FORA.get()) throw new SQLException("Banco indisponível");
                    return super.getConnection();
                }
            };
        }

        @Bean
        FilaLimitada<String> filaPedidos() {
            return new FilaLimitada<>(4);
        }
    }

    /** Rota que depende do banco degrada com 503 + Retry-After; rota sem banco continua atendendo. */
    @RestController
    static class PedidosController {
        private final JdbcTemplate jdbc;

        PedidosController(DataSource dataSource) {
            this.jdbc = new JdbcTemplate(dataSource);
        }

        @GetMapping("/pedidos/total")
        Integer total() {
            return jdbc.queryForObject("select 1", Integer.class);
        }

        @GetMapping("/versao")
        String versao() {
            return "1";
        }

        @ExceptionHandler(DataAccessResourceFailureException.class)
        ResponseEntity<ProblemDetail> bancoIndisponivel(DataAccessResourceFailureException erro) {
            var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dependência indisponível; tente novamente em instantes.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").body(problema);
        }
    }

    @LocalServerPort
    int porta;

    @Autowired
    FilaLimitada<String> fila;

    private final HttpClient http = HttpClient.newHttpClient();

    @DisplayName("SaudeAplicacao informa fila vazia como UP com os detalhes de ocupação")
    @Test
    void filaVaziaDeveEstarUpComDetalhesDeOcupacao() {
        var filaLocal = new FilaLimitada<String>(10);
        var filas = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(filas.getIfUnique()).thenReturn(filaLocal);
        Health resultado = new SaudeAplicacao().backlogHealthIndicator(filas).health();
        assertEquals("UP", resultado.getStatus().getCode());
        assertEquals(0, resultado.getDetails().get("itens"));
        assertEquals(10, resultado.getDetails().get("capacidade"));
    }

    @DisplayName("SaudeAplicacao informa estado UP quando nenhuma fila está configurada")
    @Test
    void filaNaoConfiguradaDeveEstarUpComDetalheExplicito() {
        var filas = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(filas.getIfUnique()).thenReturn(null);
        Health resultado = new SaudeAplicacao().backlogHealthIndicator(filas).health();
        assertEquals("UP", resultado.getStatus().getCode());
        assertEquals("não configurada", resultado.getDetails().get("fila"));
    }

    private HttpResponse<String> get(String caminho) throws Exception {
        var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private int status(String caminho) throws Exception {
        return get(caminho).statusCode();
    }

    @AfterEach
    void restaurar() {
        BANCO_FORA.set(false);
        while (fila.retirar().isPresent()) { }
    }

    @DisplayName("SaudeAplicacao: Banco compartilhado fora degrada a rota sem derrubar liveness nem readiness")
    @Test
    void bancoCompartilhadoForaDeveDegradarRotaSemDerrubarProbes() throws Exception {
        assertEquals(200, status("/actuator/health/readiness"));
        assertEquals(200, status("/pedidos/total"));

        BANCO_FORA.set(true);
        // Liveness continua UP: reiniciar todos os pods não conserta o banco (evita falha em cascata).
        assertEquals(200, status("/actuator/health/liveness"));
        // Readiness continua UP: o banco é o mesmo para todas as réplicas; tirá-las esvaziaria o Service.
        assertEquals(200, status("/actuator/health/readiness"));
        // O estado do banco segue visível para alerta, fora das probes.
        assertEquals(503, status("/actuator/health/dependencias"));
        // Degradação explícita: a rota que precisa do banco responde 503 + Retry-After em Problem Details...
        var degradada = get("/pedidos/total");
        assertEquals(503, degradada.statusCode());
        assertEquals("5", degradada.headers().firstValue("Retry-After").orElse(null));
        assertTrue(degradada.body().contains("\"status\":503"), degradada.body());
        // ...e a rota que não precisa dele continua atendendo.
        assertEquals(200, status("/versao"));
    }

    @DisplayName("SaudeAplicacao: Backlog saturado deve alertar sem derrubar readiness")
    @Test
    void backlogSaturadoDeveAlertarSemDerrubarReadiness() throws Exception {
        for (int i = 0; i < 4; i++) fila.oferecer("pedido-" + i);

        assertEquals(503, status("/actuator/health/operacional"));
        // Tirar réplicas saturadas da rotação concentraria a carga nas restantes: readiness permanece UP.
        assertEquals(200, status("/actuator/health/readiness"));
        assertEquals(200, status("/actuator/health/liveness"));
    }
}
