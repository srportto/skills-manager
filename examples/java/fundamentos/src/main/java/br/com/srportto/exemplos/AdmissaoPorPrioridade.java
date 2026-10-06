package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Load shedding: rejeita cedo por saturação ou por deadline insuficiente, preservando uma reserva de
 * capacidade para o fluxo crítico. A rejeição é imediata e contabilizada por motivo.
 */
public final class AdmissaoPorPrioridade {
    public enum Prioridade { CRITICA, NORMAL }

    public enum Rejeicao { SATURADO, DEADLINE_INSUFICIENTE }

    public static final class Rejeitada extends RuntimeException {
        private final Rejeicao motivo;

        Rejeitada(Rejeicao motivo) {
            super("Requisição rejeitada: " + motivo, null, false, false);
            this.motivo = motivo;
        }

        public Rejeicao motivo() { return motivo; }
    }

    /** Permissão devolvida exatamente uma vez, mesmo que close() seja chamado repetidamente. */
    public final class Permissao implements AutoCloseable {
        private final AtomicBoolean liberada = new AtomicBoolean();

        @Override
        public void close() {
            if (liberada.compareAndSet(false, true)) liberar();
        }
    }

    private final int capacidade;
    private final int reservaCritica;
    private final Duration custoMinimo;
    private final Map<Rejeicao, Long> rejeicoes = new EnumMap<>(Rejeicao.class);
    private int ativas;

    public AdmissaoPorPrioridade(int capacidade, int reservaCritica, Duration custoMinimo) {
        if (capacidade <= 0 || reservaCritica < 0 || reservaCritica >= capacidade) {
            throw new IllegalArgumentException("Capacidade positiva e reserva menor que a capacidade");
        }
        this.capacidade = capacidade;
        this.reservaCritica = reservaCritica;
        this.custoMinimo = Objects.requireNonNull(custoMinimo);
    }

    public synchronized Permissao admitir(Prioridade prioridade, Duration restante) {
        Objects.requireNonNull(prioridade);
        // Trabalho que não termina dentro do deadline é inútil: descartar antes de consumir capacidade.
        if (restante.compareTo(custoMinimo) < 0) throw rejeitar(Rejeicao.DEADLINE_INSUFICIENTE);
        int limite = prioridade == Prioridade.CRITICA ? capacidade : capacidade - reservaCritica;
        if (ativas >= limite) throw rejeitar(Rejeicao.SATURADO);
        ativas++;
        return new Permissao();
    }

    private Rejeitada rejeitar(Rejeicao motivo) {
        rejeicoes.merge(motivo, 1L, Long::sum);
        return new Rejeitada(motivo);
    }

    private synchronized void liberar() {
        ativas--;
    }

    public synchronized int ativas() { return ativas; }

    public synchronized long rejeicoes(Rejeicao motivo) { return rejeicoes.getOrDefault(motivo, 0L); }
}
