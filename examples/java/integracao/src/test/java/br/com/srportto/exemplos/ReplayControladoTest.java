package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
}
