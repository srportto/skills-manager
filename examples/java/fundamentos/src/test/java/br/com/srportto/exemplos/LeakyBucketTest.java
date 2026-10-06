package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakyBucketTest {
    private static final long INTERVALO = Duration.ofMillis(100).toNanos();

    @Test
    void deveLiberarUmItemPorIntervaloSemRajadaAposOciosidade() {
        var tempo = new AtomicLong();
        var balde = new LeakyBucket<String>(3, Duration.ofMillis(100), tempo::get);
        assertTrue(balde.oferecer("a"));
        assertTrue(balde.oferecer("b"));
        assertTrue(balde.oferecer("c"));

        assertEquals(List.of("a"), balde.liberar());
        tempo.set(INTERVALO / 2);
        assertEquals(List.of(), balde.liberar());
        tempo.set(INTERVALO);
        assertEquals(List.of("b"), balde.liberar());

        // Depois de muito tempo ocioso, o ritmo continua um por intervalo: não acumula crédito de rajada.
        tempo.set(50 * INTERVALO);
        assertTrue(balde.oferecer("d"));
        assertEquals(List.of("c"), balde.liberar());
        tempo.set(51 * INTERVALO);
        assertEquals(List.of("d"), balde.liberar());
    }

    @Test
    void deveRejeitarQuandoAFilaDeEsperaEstiverCheia() {
        var balde = new LeakyBucket<String>(2, Duration.ofMillis(100), () -> 0L);
        assertTrue(balde.oferecer("a"));
        assertTrue(balde.oferecer("b"));
        assertFalse(balde.oferecer("c"));
        assertEquals(2, balde.pendentes());
    }

    @Test
    void deveRecusarConfiguracaoInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket<String>(0, Duration.ofMillis(1), () -> 0L));
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket<String>(1, Duration.ZERO, () -> 0L));
    }
}
