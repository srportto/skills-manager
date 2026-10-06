package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class TokenBucket {
    private final int capacidade;
    private final double porSegundo;
    private final LongSupplier relogio;
    private double tokens;
    private long ultimaAtualizacao;

    public TokenBucket(int capacidade, double porSegundo, LongSupplier relogio) {
        if (capacidade <= 0 || !Double.isFinite(porSegundo) || porSegundo <= 0)
            throw new IllegalArgumentException("Capacidade e taxa devem ser positivas");
        this.capacidade = capacidade;
        this.porSegundo = porSegundo;
        this.relogio = Objects.requireNonNull(relogio);
        tokens = capacidade;
        ultimaAtualizacao = relogio.getAsLong();
    }

    public synchronized boolean tentar() {
        long agora = relogio.getAsLong();
        long decorrido = agora - ultimaAtualizacao;
        if (decorrido < 0) throw new IllegalStateException("Relógio monotônico regrediu");
        tokens = Math.min(capacidade, tokens + decorrido / 1_000_000_000.0 * porSegundo);
        ultimaAtualizacao = agora;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }
}

