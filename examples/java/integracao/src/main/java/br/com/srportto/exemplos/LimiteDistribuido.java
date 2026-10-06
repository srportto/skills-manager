package br.com.srportto.exemplos;

import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Quota global por identidade (janela fixa) num coordenador compartilhado entre réplicas.
 * Incremento e expiração acontecem numa única operação atômica: não existe "chave sem TTL" que vire quota
 * permanente se o processo cair no meio. Se o coordenador falhar, aplica um limite local conservador por
 * instância — nem libera tudo (fail-open), nem bloqueia tudo (fail-closed).
 */
public final class LimiteDistribuido {
    public enum Decisao { PERMITIDO, NEGADO_QUOTA, PERMITIDO_DEGRADADO, NEGADO_DEGRADADO }

    @FunctionalInterface
    public interface Coordenador {
        /** Incrementa o contador da janela e garante a expiração, atomicamente; devolve o valor após o incremento. */
        long incrementar(String chave, Duration janela) throws Exception;
    }

    // INCR + PEXPIRE no mesmo script: atomicidade garantida pelo servidor. PTTL < 0 também corrige chaves
    // antigas que ficaram sem expiração (ex.: implementação anterior em dois comandos).
    private static final String SCRIPT = """
            local atual = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return atual
            """;

    public static Coordenador redis(UnifiedJedis redis) {
        Objects.requireNonNull(redis);
        return (chave, janela) -> (Long) redis.eval(SCRIPT, List.of(chave), List.of(String.valueOf(janela.toMillis())));
    }

    public static String chave(String identidade) {
        return "ratelimit:" + identidade;
    }

    private final Coordenador coordenador;
    private final int limite;
    private final Duration janela;
    private final int capacidadeLocal;
    private final double taxaLocalPorSegundo;
    private final LongSupplier relogio;
    private final Map<String, TokenBucket> locais;
    private final AtomicLong falhas = new AtomicLong();

    public LimiteDistribuido(Coordenador coordenador, int limite, Duration janela, int capacidadeLocal,
                             double taxaLocalPorSegundo, int maxIdentidadesLocais, LongSupplier relogio) {
        if (limite <= 0 || maxIdentidadesLocais <= 0) throw new IllegalArgumentException("Limites devem ser positivos");
        this.coordenador = Objects.requireNonNull(coordenador);
        this.limite = limite;
        this.janela = Objects.requireNonNull(janela);
        this.capacidadeLocal = capacidadeLocal;
        this.taxaLocalPorSegundo = taxaLocalPorSegundo;
        this.relogio = Objects.requireNonNull(relogio);
        // LRU limitado: identidades arbitrárias não crescem a memória sem limite durante a degradação.
        this.locais = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TokenBucket> maisAntiga) {
                return size() > maxIdentidadesLocais;
            }
        };
    }

    public Decisao avaliar(String identidade) {
        Objects.requireNonNull(identidade, "Identidade autenticada obrigatória");
        try {
            long atual = coordenador.incrementar(chave(identidade), janela);
            return atual <= limite ? Decisao.PERMITIDO : Decisao.NEGADO_QUOTA;
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            return avaliarLocal(identidade);
        } catch (Exception indisponivel) {
            return avaliarLocal(identidade);
        }
    }

    private Decisao avaliarLocal(String identidade) {
        falhas.incrementAndGet();
        TokenBucket balde;
        synchronized (locais) {
            balde = locais.computeIfAbsent(identidade, id -> new TokenBucket(capacidadeLocal, taxaLocalPorSegundo, relogio));
        }
        return balde.tentar() ? Decisao.PERMITIDO_DEGRADADO : Decisao.NEGADO_DEGRADADO;
    }

    public long falhasCoordenador() { return falhas.get(); }

    public int identidadesLocais() {
        synchronized (locais) {
            return locais.size();
        }
    }
}
