package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.function.Consumer;

public final class ReplayControlado<T> {
    private final FilaLimitada<T> pendentes;
    private final TokenBucket quota;
    private T emTentativa;

    public ReplayControlado(FilaLimitada<T> pendentes, TokenBucket quota) {
        this.pendentes = Objects.requireNonNull(pendentes); this.quota = Objects.requireNonNull(quota);
    }

    public synchronized boolean proximo(Consumer<T> destino) {
        if (emTentativa == null) emTentativa = pendentes.retirar().orElse(null);
        if (emTentativa == null || !quota.tentar()) return false;
        destino.accept(emTentativa);
        emTentativa = null;
        return true;
    }
}

