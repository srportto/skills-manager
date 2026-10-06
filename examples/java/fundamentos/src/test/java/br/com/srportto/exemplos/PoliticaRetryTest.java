package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PoliticaRetryTest {
    private final AtomicLong tempo = new AtomicLong();
    private final List<Duration> esperas = new ArrayList<>();

    private PoliticaRetry politica(int tentativas, double jitter) {
        // A espera avança o relógio falso: nenhum sleep real no teste.
        return new PoliticaRetry(tentativas, Duration.ofMillis(10), Duration.ofMillis(100), () -> jitter,
                espera -> { esperas.add(espera); tempo.addAndGet(espera.toNanos()); }, () -> true);
    }

    @Test
    void deveRepetirFalhaTransitoriaComBackoffExponencialEJitter() throws Exception {
        var tentativas = new AtomicInteger();
        String resultado = politica(3, 0.5).executar(() -> {
            if (tentativas.incrementAndGet() < 3) throw new IllegalStateException("transitória");
            return "ok";
        }, erro -> erro instanceof IllegalStateException, new OrcamentoTempo(Duration.ofSeconds(1), tempo::get));

        assertEquals("ok", resultado);
        assertEquals(3, tentativas.get());
        // Tetos 10 ms e 20 ms; full jitter com fração 0,5.
        assertEquals(List.of(Duration.ofMillis(5), Duration.ofMillis(10)), esperas);
    }

    @Test
    void tetoDoBackoffDeveSerRespeitado() throws Exception {
        var tentativas = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> politica(6, 0.99).executar(() -> {
            tentativas.incrementAndGet();
            throw new IllegalStateException();
        }, erro -> true, new OrcamentoTempo(Duration.ofSeconds(10), tempo::get)));

        assertEquals(6, tentativas.get());
        assertTrue(esperas.stream().allMatch(espera -> espera.compareTo(Duration.ofMillis(100)) < 0), esperas::toString);
    }

    @Test
    void naoDeveDormirSeAEsperaConsumirODeadline() {
        var tentativas = new AtomicInteger();
        assertThrows(TimeoutException.class, () -> politica(3, 0.5).executar(() -> {
            tentativas.incrementAndGet();
            throw new IllegalStateException();
        }, erro -> true, new OrcamentoTempo(Duration.ofMillis(2), tempo::get)));
        assertEquals(1, tentativas.get());
        assertEquals(List.of(), esperas);
    }

    @Test
    void naoDeveRepetirErroPermanenteNemSemQuotaAgregada() {
        var chamadas = new AtomicInteger();
        var semQuota = new PoliticaRetry(3, Duration.ofMillis(1), Duration.ofMillis(2), () -> 0.5,
                espera -> fail("Não deve esperar"), () -> false);
        assertThrows(IllegalArgumentException.class, () -> semQuota.executar(() -> {
            chamadas.incrementAndGet();
            throw new IllegalArgumentException();
        }, erro -> true, new OrcamentoTempo(Duration.ofSeconds(1), System::nanoTime)));
        assertEquals(1, chamadas.get());

        chamadas.set(0);
        assertThrows(IllegalArgumentException.class, () -> politica(3, 0.5).executar(() -> {
            chamadas.incrementAndGet();
            throw new IllegalArgumentException("permanente");
        }, erro -> false, new OrcamentoTempo(Duration.ofSeconds(1), tempo::get)));
        assertEquals(1, chamadas.get());
    }

    @Test
    void interrupcaoNaoDeveVirarRetry() {
        try {
            assertThrows(InterruptedException.class, () -> politica(3, 0.5).executar(() -> {
                throw new InterruptedException();
            }, erro -> true, new OrcamentoTempo(Duration.ofSeconds(1), tempo::get)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
