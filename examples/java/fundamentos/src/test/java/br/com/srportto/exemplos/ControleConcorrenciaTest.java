package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControleConcorrenciaTest {
    @Test
    void deveLiberarCapacidadeDepoisDeFalha() throws Exception {
        var controle = new ControleConcorrencia(1);

        assertThrows(IllegalStateException.class, () -> controle.executar(() -> {
            throw new IllegalStateException("Falha simulada da dependência");
        }));

        assertEquals("recuperado", controle.executar(() -> "recuperado"));
    }

    @Test
    void deveRejeitarEnquantoUnicaPermissaoEstiverOcupada() throws Exception {
        var controle = new ControleConcorrencia(1);

        controle.executar(() -> {
            assertThrows(RejectedExecutionException.class, () -> controle.executar(() -> "excedente"));
            return null;
        });
    }

    @Test
    void deveLimitarTrabalhoRealComVirtualThreads() throws Exception {
        var controle = new ControleConcorrencia(2);
        var iniciadas = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        var ativas = new AtomicInteger();
        var maximo = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tarefas = new ArrayList<Future<?>>();
            try {
                for (int i = 0; i < 2; i++) {
                    tarefas.add(executor.submit(() -> controle.executar(() -> {
                        maximo.accumulateAndGet(ativas.incrementAndGet(), Math::max);
                        iniciadas.countDown();
                        try {
                            if (!liberar.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
                            return "ok";
                        } finally {
                            ativas.decrementAndGet();
                        }
                    })));
                }
                assertTrue(iniciadas.await(5, TimeUnit.SECONDS));
                // Com as duas permissões presas, o excedente é rejeitado imediatamente.
                assertThrows(RejectedExecutionException.class, () -> controle.executar(() -> "excedente"));
                assertEquals(2, maximo.get());
            } finally {
                liberar.countDown();
            }
            for (Future<?> tarefa : tarefas) tarefa.get(5, TimeUnit.SECONDS);
        }
        assertEquals("ok", controle.executar(() -> "ok"));
    }

    @Test
    void cancelarEsperaNaoDeveLiberarOperacaoAindaAtiva() {
        var controle = new ControleConcorrencia(1);
        var trabalho = new CompletableFuture<String>();
        var espera = controle.executarAsync(() -> trabalho).toCompletableFuture();
        espera.cancel(true);

        // O chamador desistiu, mas o trabalho continua ocupando a permissão.
        assertThrows(RejectedExecutionException.class,
                () -> controle.executarAsync(() -> CompletableFuture.completedFuture("excedente")));
        trabalho.complete("terminou");
        assertEquals("ok", controle.executarAsync(() -> CompletableFuture.completedFuture("ok"))
                .toCompletableFuture().join());
    }

    @Test
    void falhaAoIniciarOperacaoAssincronaDeveLiberarPermissao() {
        var controle = new ControleConcorrencia(1);
        assertThrows(IllegalStateException.class, () -> controle.executarAsync(() -> {
            throw new IllegalStateException("Falha antes de criar o future");
        }));
        assertEquals("ok", controle.executarAsync(() -> CompletableFuture.completedFuture("ok"))
                .toCompletableFuture().join());
    }
}
