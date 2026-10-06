package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

public final class OrcamentoTempo {
    private final long inicio;
    private final long limite;
    private final LongSupplier relogio;

    public OrcamentoTempo(Duration duracao, LongSupplier relogio) {
        if (duracao.isNegative() || duracao.isZero()) throw new IllegalArgumentException("Prazo deve ser positivo");
        limite = duracao.toNanos();
        this.relogio = Objects.requireNonNull(relogio);
        inicio = relogio.getAsLong();
    }

    public Duration restante() {
        long decorrido = relogio.getAsLong() - inicio;
        if (decorrido < 0) throw new IllegalStateException("Relógio monotônico regrediu");
        return Duration.ofNanos(Math.max(0, limite - decorrido));
    }

    public void exigirDisponivel() throws TimeoutException {
        if (restante().isZero()) throw new TimeoutException("Deadline esgotado");
    }
}

