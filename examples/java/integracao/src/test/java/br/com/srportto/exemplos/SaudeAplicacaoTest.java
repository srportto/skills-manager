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
import org.springframework.jdbc.datasource.DelegatingDataSource;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Health groups reais (Actuator): liveness não depende de serviço externo; readiness considera só o que é
 * necessário para atender; saturação/backlog fica num grupo operacional (alerta), sem tirar pods do ar.
 */
@SpringBootTest(classes = SaudeAplicacaoTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.name=saude-aplicacao-teste")
class SaudeAplicacaoTest {
    static final AtomicBoolean BANCO_FORA = new AtomicBoolean();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SaudeAplicacao.class)
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

    private int status(String caminho) throws Exception {
        var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @AfterEach
    void restaurar() {
        BANCO_FORA.set(false);
        while (fila.retirar().isPresent()) { }
    }

    @DisplayName("SaudeAplicacao: Dependencia fora nao deve derrubar liveness mas deve tirar da rotacao")
    @Test
    void dependenciaForaNaoDeveDerrubarLivenessMasDeveTirarDaRotacao() throws Exception {
        assertEquals(200, status("/actuator/health/liveness"));
        assertEquals(200, status("/actuator/health/readiness"));

        BANCO_FORA.set(true);
        // Liveness continua UP: reiniciar todos os pods não conserta o banco (evita falha em cascata).
        assertEquals(200, status("/actuator/health/liveness"));
        // Readiness DOWN: sem banco a réplica não atende pedidos; sai do balanceador sem reiniciar.
        assertEquals(503, status("/actuator/health/readiness"));
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
