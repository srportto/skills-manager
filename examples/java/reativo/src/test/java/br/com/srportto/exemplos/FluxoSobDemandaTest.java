package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class FluxoSobDemandaTest {
    @Test void deveEmitirSomenteDemandaECancelarOrigem() {
        var emitidos = new AtomicInteger();
        var cancelado = new AtomicBoolean();
        StepVerifier.create(FluxoSobDemanda.numeros(10, emitidos, cancelado), 0)
                .then(() -> assertEquals(0, emitidos.get()))
                .thenRequest(2).expectNext(1, 2)
                .then(() -> assertEquals(2, emitidos.get()))
                .thenRequest(1).expectNext(3).thenCancel().verify(Duration.ofSeconds(2));
        assertTrue(cancelado.get());
        assertEquals(3, emitidos.get());
    }

    @Test void fonteTemporizadaDeveUsarBufferLimitadoEContarDescartesComTempoVirtual() {
        var descartados = new AtomicInteger();
        // O publisher temporizado é criado dentro do supplier para usar o relógio virtual.
        StepVerifier.withVirtualTime(() -> FluxoSobDemanda.ticksComBufferLimitado(Duration.ofSeconds(1), 2, descartados), 0)
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(5))
                .then(() -> assertEquals(3, descartados.get()))
                .thenRequest(2).expectNext(0L, 1L)
                .thenRequest(1).thenAwait(Duration.ofSeconds(1)).expectNext(5L)
                .thenCancel().verify(Duration.ofSeconds(2));
    }

    @Test void devePropagarErroDeOverflowDeFonteNaoRegulavel() {
        StepVerifier.create(FluxoSobDemanda.fonteNaoRegulavel(), 0)
                .expectError(IllegalStateException.class).verify(Duration.ofSeconds(2));
    }
}
