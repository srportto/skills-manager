package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public final class FluxoSobDemanda {
    private FluxoSobDemanda() {}

    /** Fonte regulável: só emite o que foi pedido e propaga o cancelamento até a origem. */
    public static Flux<Integer> numeros(int quantidade, AtomicInteger emitidos, AtomicBoolean cancelado) {
        return Flux.range(1, quantidade)
                .doOnNext(item -> emitidos.incrementAndGet())
                .doOnCancel(() -> cancelado.set(true));
    }

    /** Fonte que ignora a demanda; ERROR torna a sobrecarga explícita em vez de acumular. */
    public static Flux<Integer> fonteNaoRegulavel() {
        return Flux.create(emissor -> {
            emissor.next(1);
            emissor.next(2);
            emissor.complete();
        }, FluxSink.OverflowStrategy.ERROR);
    }

    /**
     * Fonte temporizada (não regulável) com buffer limitado: excedente é descartado e contado.
     * Adequado a amostras/telemetria; pedidos de negócio exigem rejeição explícita ou persistência.
     */
    public static Flux<Long> ticksComBufferLimitado(Duration intervalo, int capacidade, AtomicInteger descartados) {
        return Flux.interval(intervalo)
                .onBackpressureBuffer(capacidade, descartado -> descartados.incrementAndGet(),
                        BufferOverflowStrategy.DROP_LATEST);
    }
}
