package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

public final class PoliticaRetry {
    @FunctionalInterface public interface Espera { void aguardar(Duration duracao) throws InterruptedException; }
    private final int maxTentativas;
    private final long baseNanos;
    private final long tetoNanos;
    private final DoubleSupplier jitter;
    private final Espera espera;
    private final BooleanSupplier quotaRetry;

    public PoliticaRetry(int maxTentativas, Duration base, Duration teto, DoubleSupplier jitter,
                         Espera espera, BooleanSupplier quotaRetry) {
        if (maxTentativas < 1 || base.isZero() || base.isNegative() || teto.compareTo(base) < 0)
            throw new IllegalArgumentException("Política inválida");
        this.maxTentativas = maxTentativas;
        baseNanos = base.toNanos();
        tetoNanos = teto.toNanos();
        this.jitter = Objects.requireNonNull(jitter);
        this.espera = Objects.requireNonNull(espera);
        this.quotaRetry = Objects.requireNonNull(quotaRetry);
    }

    public <T> T executar(Callable<T> operacao, Predicate<Exception> elegivel, OrcamentoTempo orcamento) throws Exception {
        long atrasoMaximo = baseNanos;
        for (int tentativa = 1; ; tentativa++) {
            orcamento.exigirDisponivel();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Execução interrompida");
            try { return operacao.call(); }
            catch (InterruptedException erro) { Thread.currentThread().interrupt(); throw erro; }
            catch (Exception erro) {
                if (tentativa >= maxTentativas || !elegivel.test(erro)) throw erro;
                double fracao = jitter.getAsDouble();
                if (!Double.isFinite(fracao) || fracao < 0 || fracao >= 1) throw new IllegalArgumentException("Jitter fora de [0,1)");
                Duration atraso = Duration.ofNanos((long) (atrasoMaximo * fracao));
                if (atraso.compareTo(orcamento.restante()) >= 0) throw new TimeoutException("Retry excede deadline");
                if (!quotaRetry.getAsBoolean()) throw erro;
                try { espera.aguardar(atraso); }
                catch (InterruptedException interrupcao) { Thread.currentThread().interrupt(); throw interrupcao; }
                atrasoMaximo = atrasoMaximo > tetoNanos / 2 ? tetoNanos : Math.min(tetoNanos, atrasoMaximo * 2);
            }
        }
    }
}

