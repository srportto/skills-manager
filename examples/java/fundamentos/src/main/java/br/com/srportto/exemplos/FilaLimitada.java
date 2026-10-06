package br.com.srportto.exemplos;

import java.util.*;
import java.util.function.ToLongFunction;

public final class FilaLimitada<T> {
    public enum Admissao { ACEITO, REJEITADO_POR_CAPACIDADE }
    private record Item<T>(T valor, long bytes) {}
    private final ArrayDeque<Item<T>> fila = new ArrayDeque<>();
    private final int capacidade;
    private final long limiteBytes;
    private final ToLongFunction<T> tamanho;
    private long bytes;

    public FilaLimitada(int capacidade) { this(capacidade, Long.MAX_VALUE, item -> 1); }

    public FilaLimitada(int capacidade, long limiteBytes, ToLongFunction<T> tamanho) {
        if (capacidade <= 0 || limiteBytes <= 0) throw new IllegalArgumentException("Limites devem ser positivos");
        this.capacidade = capacidade;
        this.limiteBytes = limiteBytes;
        this.tamanho = Objects.requireNonNull(tamanho);
    }

    public synchronized Admissao oferecer(T valor) {
        long peso = tamanho.applyAsLong(Objects.requireNonNull(valor));
        if (peso <= 0) throw new IllegalArgumentException("Tamanho deve ser positivo");
        if (fila.size() == capacidade || peso > limiteBytes - bytes) return Admissao.REJEITADO_POR_CAPACIDADE;
        fila.addLast(new Item<>(valor, peso));
        bytes += peso;
        return Admissao.ACEITO;
    }

    public synchronized Optional<T> retirar() {
        Item<T> item = fila.pollFirst();
        if (item == null) return Optional.empty();
        bytes -= item.bytes();
        return Optional.of(item.valor());
    }

    public synchronized int tamanho() { return fila.size(); }
    public int capacidade() { return capacidade; }
    public synchronized long bytes() { return bytes; }
}

