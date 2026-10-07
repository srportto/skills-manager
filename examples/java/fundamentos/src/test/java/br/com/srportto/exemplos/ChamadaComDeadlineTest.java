package br.com.srportto.exemplos;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChamadaComDeadlineTest {
    private HttpServer servidor;
    private final CountDownLatch liberarLenta = new CountDownLatch(1);
    private final CountDownLatch lentaTerminou = new CountDownLatch(1);

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/rapida", troca -> responder(troca, "ok"));
        servidor.createContext("/lenta", troca -> {
            try {
                // Simula dependência que só responde quando o teste permitir.
                liberarLenta.await(10, TimeUnit.SECONDS);
                responder(troca, "tarde");
            } catch (InterruptedException erro) {
                Thread.currentThread().interrupt();
            } finally {
                lentaTerminou.countDown();
            }
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        liberarLenta.countDown();
        servidor.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.sendResponseHeaders(200, bytes.length);
        try (var saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private URI uri(String caminho) {
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + caminho);
    }

    @DisplayName("ChamadaComDeadline: Deve responder dentro do deadline")
    @Test
    void deveResponderDentroDoDeadline() throws Exception {
        var chamada = new ChamadaComDeadline(Duration.ofSeconds(2));
        assertEquals("ok", chamada.obter(uri("/rapida"), new OrcamentoTempo(Duration.ofSeconds(2), System::nanoTime)));
    }

    @DisplayName("ChamadaComDeadline: Dependencia lenta nao deve reter o chamador alem do deadline")
    @Test
    void dependenciaLentaNaoDeveReterOChamadorAlemDoDeadline() throws Exception {
        var chamada = new ChamadaComDeadline(Duration.ofSeconds(2));
        long inicio = System.nanoTime();
        assertThrows(TimeoutException.class,
                () -> chamada.obter(uri("/lenta"), new OrcamentoTempo(Duration.ofMillis(200), System::nanoTime)));
        long decorrido = System.nanoTime() - inicio;
        assertTrue(decorrido < Duration.ofSeconds(3).toNanos(), "chamador ficou preso por " + decorrido + " ns");
        // Desistir da resposta não encerra o trabalho remoto: efeitos exigem idempotência/reconciliação.
        assertFalse(lentaTerminou.await(0, TimeUnit.MILLISECONDS));
    }

    @DisplayName("ChamadaComDeadline: Nao deve chamar quando o orcamento ja esgotou")
    @Test
    void naoDeveChamarQuandoOOrcamentoJaEsgotou() {
        var tempo = new AtomicLong();
        var orcamento = new OrcamentoTempo(Duration.ofMillis(10), tempo::get);
        tempo.set(Duration.ofMillis(10).toNanos());
        var chamada = new ChamadaComDeadline(Duration.ofSeconds(2));
        assertThrows(TimeoutException.class, () -> chamada.obter(uri("/rapida"), orcamento));
    }
}
