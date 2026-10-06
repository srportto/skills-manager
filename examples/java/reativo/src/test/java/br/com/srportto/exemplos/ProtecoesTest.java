package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.bulkhead.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtecoesTest {
    @Test void breakerDeveAbrirRecusarEFecharComSondaSaudavel() {
        var config = CircuitBreakerConfig.custom().slidingWindowSize(2).minimumNumberOfCalls(2)
                .failureRateThreshold(50).permittedNumberOfCallsInHalfOpenState(1).build();
        var breaker = CircuitBreaker.of("pagamentos", config);
        var chamadas = new AtomicInteger();
        var falha = CircuitBreaker.decorateSupplier(breaker, () -> {
            chamadas.incrementAndGet(); throw new IllegalStateException("Dependência indisponível");
        });
        assertThrows(IllegalStateException.class, falha::get);
        assertThrows(IllegalStateException.class, falha::get);
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertThrows(CallNotPermittedException.class, falha::get);
        assertEquals(2, chamadas.get());
        // A transição explícita elimina espera de relógio neste teste de recuperação.
        breaker.transitionToHalfOpenState();
        assertEquals("ok", breaker.executeSupplier(() -> "ok"));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test void bulkheadsDevemIsolarRelatoriosDeCheckout() {
        var config = BulkheadConfig.custom().maxConcurrentCalls(1).build();
        var relatorios = Bulkhead.of("relatorios", config);
        var checkout = Bulkhead.of("checkout", config);
        assertTrue(relatorios.tryAcquirePermission());
        try {
            assertThrows(BulkheadFullException.class, () -> relatorios.executeSupplier(() -> "excedente"));
            assertEquals("ok", checkout.executeSupplier(() -> "ok"));
        } finally { relatorios.onComplete(); }
        assertEquals("recuperado", relatorios.executeSupplier(() -> "recuperado"));
    }
}

