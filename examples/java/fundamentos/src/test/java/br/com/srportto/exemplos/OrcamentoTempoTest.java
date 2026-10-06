package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrcamentoTempoTest {
    @Test
    void deveDescontarTempoDecorridoENuncaFicarNegativo() {
        var tempo = new AtomicLong(1_000);
        var orcamento = new OrcamentoTempo(Duration.ofMillis(100), tempo::get);

        tempo.addAndGet(Duration.ofMillis(30).toNanos());
        assertEquals(Duration.ofMillis(70), orcamento.restante());
        assertDoesNotThrow(orcamento::exigirDisponivel);

        tempo.addAndGet(Duration.ofSeconds(5).toNanos());
        assertEquals(Duration.ZERO, orcamento.restante());
        assertThrows(TimeoutException.class, orcamento::exigirDisponivel);
    }

    @Test
    void deveRecusarPrazoInvalidoERelogioQueRegride() {
        assertThrows(IllegalArgumentException.class, () -> new OrcamentoTempo(Duration.ZERO, () -> 0L));
        var tempo = new AtomicLong(10);
        var orcamento = new OrcamentoTempo(Duration.ofSeconds(1), tempo::get);
        tempo.set(5);
        assertThrows(IllegalStateException.class, orcamento::restante);
    }
}
