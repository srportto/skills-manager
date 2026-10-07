package br.com.srportto.exemplos;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {CheckoutApplication.class, CheckoutApplicationTest.PagamentoDeTeste.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"checkout.capacidade=2", "spring.datasource.url=jdbc:h2:mem:checkout-${random.uuid};DB_CLOSE_DELAY=-1"})
class CheckoutApplicationTest {
    /** Provedor de pagamento controlado pelo teste: pode segurar chamadas para simular dependência lenta. */
    @TestConfiguration(proxyBeanMethods = false)
    static class PagamentoDeTeste {
        static final AtomicReference<CountDownLatch> BLOQUEIO = new AtomicReference<>(new CountDownLatch(0));

        @Bean
        @Primary
        CheckoutApplication.ProvedorPagamento provedorDeTeste() {
            return () -> BLOQUEIO.get().await(10, TimeUnit.SECONDS);
        }
    }

    @LocalServerPort
    int porta;

    @Autowired
    AdmissaoPorPrioridade admissao;

    @Autowired
    MeterRegistry registro;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void liberar() {
        PagamentoDeTeste.BLOQUEIO.get().countDown();
        PagamentoDeTeste.BLOQUEIO.set(new CountDownLatch(0));
    }

    private HttpRequest pedido(String chave, long centavos) {
        var construtor = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/pedidos"))
                .header("Content-Type", "application/json")
                .header("X-Tenant", "tenant-a")
                .POST(HttpRequest.BodyPublishers.ofString("{\"centavos\":" + centavos + "}"));
        if (chave != null) construtor.header("Idempotency-Key", chave);
        return construtor.build();
    }

    private HttpResponse<String> enviar(HttpRequest pedido) throws Exception {
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    @DisplayName("Checkout devolve 201 e o mesmo corpo quando a mesma Idempotency-Key repete o payload")
    @Test
    void repeticaoComMesmaChaveDeveDevolverOMesmoPedidoSemDuplicarEfeito() throws Exception {
        String chave = UUID.randomUUID().toString();
        var primeira = enviar(pedido(chave, 1500));
        var segunda = enviar(pedido(chave, 1500));

        assertEquals(201, primeira.statusCode());
        assertEquals(201, segunda.statusCode());
        assertEquals(primeira.body(), segunda.body());
    }

    @DisplayName("CheckoutApplication: Mesma chave com payload diferente deve ser conflito")
    @Test
    void mesmaChaveComPayloadDiferenteDeveSerConflito() throws Exception {
        String chave = UUID.randomUUID().toString();
        assertEquals(201, enviar(pedido(chave, 1500)).statusCode());
        assertEquals(409, enviar(pedido(chave, 9999)).statusCode());
    }

    @DisplayName("CheckoutApplication: Sem chave de idempotencia ou com valor invalido deve ser recusado")
    @Test
    void semChaveDeIdempotenciaOuComValorInvalidoDeveSerRecusado() throws Exception {
        assertEquals(400, enviar(pedido(null, 1500)).statusCode());
        assertEquals(422, enviar(pedido(UUID.randomUUID().toString(), 0)).statusCode());
    }

    @DisplayName("CheckoutApplication: Saturacao deve responder503 rapido com retry after e metrica de rejeicao")
    @Test
    void saturacaoDeveResponder503RapidoComRetryAfterEMetricaDeRejeicao() throws Exception {
        PagamentoDeTeste.BLOQUEIO.set(new CountDownLatch(1));
        CompletableFuture<HttpResponse<String>> a = http.sendAsync(pedido(UUID.randomUUID().toString(), 100), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> b = http.sendAsync(pedido(UUID.randomUUID().toString(), 200), HttpResponse.BodyHandlers.ofString());
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (admissao.ativas() < 2 && System.nanoTime() < limite) Thread.onSpinWait();
        assertEquals(2, admissao.ativas());

        long inicio = System.nanoTime();
        var rejeitada = enviar(pedido(UUID.randomUUID().toString(), 300));
        long duracaoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        assertEquals(503, rejeitada.statusCode());
        assertTrue(rejeitada.headers().firstValue("Retry-After").isPresent());
        // A rejeição precisa ser barata: não espera a dependência lenta.
        assertTrue(duracaoMs < 1_000, "rejeição levou " + duracaoMs + " ms");
        assertEquals(1, registro.get("app.requisicoes").tag("resultado", "rejeitada").timer().count());

        PagamentoDeTeste.BLOQUEIO.get().countDown();
        assertEquals(201, a.get(10, TimeUnit.SECONDS).statusCode());
        assertEquals(201, b.get(10, TimeUnit.SECONDS).statusCode());
    }

    @DisplayName("CheckoutApplication: Probes e smoke test devem responder")
    @Test
    void probesESmokeTestDevemResponder() throws Exception {
        for (String caminho : new String[]{"/disponibilidade", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            var resposta = enviar(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build());
            assertEquals(200, resposta.statusCode(), caminho);
        }
    }
}
