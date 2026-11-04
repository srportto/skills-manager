package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Throttling por leaky bucket: libera no máximo um item por intervalo, com fila de espera limitada.
 * Diferente do token bucket, não acumula crédito de rajada durante a ociosidade.
 */
public final class LeakyBucket<T> {
    private final ArrayDeque<T> fila = new ArrayDeque<>();
    private final int capacidade;
    private final long intervaloNanos;
    private final LongSupplier relogio;
    private long proximaLiberacao;

    public LeakyBucket(int capacidade, Duration intervalo, LongSupplier relogio) {
        if (capacidade <= 0 || intervalo.isZero() || intervalo.isNegative()) {
            throw new IllegalArgumentException("Capacidade e intervalo devem ser positivos");
        }
        this.capacidade = capacidade;
        this.intervaloNanos = intervalo.toNanos();
        this.relogio = Objects.requireNonNull(relogio);
        this.proximaLiberacao = relogio.getAsLong();
    }

    /** Aceita o item se houver espaço; caso contrário o chamador decide rejeitar, pausar ou persistir. */
    public synchronized boolean oferecer(T item) {
        Objects.requireNonNull(item, "Item obrigatório");
        if (fila.size() == capacidade) return false;
        fila.addLast(item);
        return true;
    }

    /** Devolve os itens cuja vez chegou, respeitando o espaçamento mínimo entre liberações. */
    public synchronized List<T> liberar() {
        long agora = relogio.getAsLong();
        var liberados = new ArrayList<T>();
        // Após ociosidade, o próximo horário parte de "agora": sem rajada acumulada.
        if (proximaLiberacao < agora - intervaloNanos) proximaLiberacao = agora;
        while (!fila.isEmpty() && proximaLiberacao <= agora) {
            liberados.add(fila.pollFirst());
            proximaLiberacao += intervaloNanos;
        }
        return liberados;
    }

    public synchronized int pendentes() {
        return fila.size();
    }
}
