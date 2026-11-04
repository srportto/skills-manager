package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayControladoTest {
    private final AtomicLong tempo = new AtomicLong();

    private ReplayControlado<String> replay(String... itens) {
        var pendentes = new FilaLimitada<String>(10);
        for (String item : itens) pendentes.oferecer(item);
        // Burst de 2 e 1 item/s: o destino recuperado não recebe tudo de uma vez.
        return new ReplayControlado<>(pendentes, new TokenBucket(2, 1, tempo::get));
    }

    @DisplayName("ReplayControlado: Replay deve respeitar a taxa disponivel no destino")
    @Test
    void replayDeveRespeitarATaxaDisponivelNoDestino() {
        var replay = replay("a", "b", "c", "d");
        var entregues = new ArrayList<String>();
        assertTrue(replay.proximo(entregues::add));
        assertTrue(replay.proximo(entregues::add));
        assertFalse(replay.proximo(entregues::add));
        tempo.set(1_000_000_000L);
        assertTrue(replay.proximo(entregues::add));
        assertFalse(replay.proximo(entregues::add));
        assertEquals(List.of("a", "b", "c"), entregues);
    }

    @DisplayName("ReplayControlado: Falha do destino nao deve perder o item em replay")
    @Test
    void falhaDoDestinoNaoDevePerderOItemEmReplay() {
        var replay = replay("a", "b");
        assertThrows(IllegalStateException.class, () -> replay.proximo(item -> { throw new IllegalStateException("fora"); }));
        tempo.set(5_000_000_000L);
        var entregues = new ArrayList<String>();
        assertTrue(replay.proximo(entregues::add));
        assertTrue(replay.proximo(entregues::add));
        assertEquals(List.of("a", "b"), entregues);
    }

    @DisplayName("ReplayControlado retorna falso e não chama o destino quando a fila está vazia")
    @Test
    void filaVaziaDeveRetornarFalsoSemChamarDestino() {
        var replay = replay();
        var chamadas = new AtomicInteger();
        assertFalse(replay.proximo(item -> chamadas.incrementAndGet()));
        assertEquals(0, chamadas.get());
    }

    @DisplayName("ReplayControlado serializa chamadas concorrentes para entregar cada item uma vez")
    @Test
    void chamadasConcorrentesDevemProcessarCadaItemUmaVez() throws Exception {
        var replay = replay("a");
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var entregues = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var primeira = executor.submit(() -> replay.proximo(item -> {
                entrou.countDown();
                try {
                    if (!liberar.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("tempo excedido");
                    entregues.incrementAndGet();
                } catch (InterruptedException erro) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(erro);
                }
            }));
            assertTrue(entrou.await(5, TimeUnit.SECONDS));
            var segunda = executor.submit(() -> replay.proximo(item -> entregues.incrementAndGet()));
            liberar.countDown();
            assertTrue(primeira.get(5, TimeUnit.SECONDS));
            assertFalse(segunda.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, entregues.get());
    }
}
