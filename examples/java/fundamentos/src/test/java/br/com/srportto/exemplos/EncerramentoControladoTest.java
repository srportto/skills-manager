package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncerramentoControladoTest {
    @Test
    void deveRecusarTrabalhoNovoEDrenarOTrabalhoEmAndamento() throws Exception {
        var concluidas = new AtomicInteger();
        var iniciou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        try (var encerramento = new EncerramentoControlado()) {
            assertTrue(encerramento.submeter(() -> {
                iniciou.countDown();
                aguardar(liberar);
                concluidas.incrementAndGet();
            }));
            assertTrue(iniciou.await(5, TimeUnit.SECONDS));

            // Libera a tarefa logo após o início do encerramento: ela deve terminar dentro do prazo.
            Thread.ofVirtual().start(liberar::countDown);
            var relatorio = encerramento.encerrar(Duration.ofSeconds(5));

            assertFalse(encerramento.submeter(concluidas::incrementAndGet));
            assertEquals(0, relatorio.pendentes());
            assertEquals(1, concluidas.get());
        }
    }

    @Test
    void trabalhoQueExcedeOPrazoDeveSerRelatadoComoPendenteNaoPerdidoEmSilencio() throws Exception {
        var iniciou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        try (var encerramento = new EncerramentoControlado()) {
            encerramento.submeter(() -> {
                iniciou.countDown();
                aguardar(liberar);
            });
            assertTrue(iniciou.await(5, TimeUnit.SECONDS));
            var relatorio = encerramento.encerrar(Duration.ofMillis(100));
            assertEquals(1, relatorio.pendentes());
            assertFalse(relatorio.drenado());
        } finally {
            liberar.countDown();
        }
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Teste não liberou a tarefa");
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
        }
    }
}
