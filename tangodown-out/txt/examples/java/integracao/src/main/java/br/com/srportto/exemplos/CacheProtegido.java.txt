package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cache-aside que protege a origem quando o cache falha ou expira:
 * <ul>
 *   <li>uma única recomputação por chave (single-flight); chamadas concorrentes da mesma chave são rejeitadas
 *       e o chamador degrada (valor anterior, 503 ou resposta parcial);</li>
 *   <li>no máximo {@code limiteRecomputacoes} chaves recomputando ao mesmo tempo — o mapa de chaves em voo
 *       nunca passa desse tamanho;</li>
 *   <li>a origem continua limitada por {@code limiteBanco} mesmo com o cache inteiro fora.</li>
 * </ul>
 */
public final class CacheProtegido<T> {
    @FunctionalInterface
    public interface Carregador<T> {
        T carregar(String chave) throws Exception;
    }

    private final Function<String, T> ler;
    private final BiConsumer<String, T> gravar;
    private final Carregador<T> origem;
    private final ControleConcorrencia banco;
    private final Semaphore vagasRecomputacao;
    private final ConcurrentHashMap<String, Boolean> emRecomputacao = new ConcurrentHashMap<>();

    public CacheProtegido(Function<String, T> ler, BiConsumer<String, T> gravar,
                          Carregador<T> origem, int limiteBanco, int limiteRecomputacoes) {
        if (limiteRecomputacoes <= 0) throw new IllegalArgumentException("Limite de recomputações deve ser positivo");
        this.ler = Objects.requireNonNull(ler);
        this.gravar = Objects.requireNonNull(gravar);
        this.origem = Objects.requireNonNull(origem);
        this.banco = new ControleConcorrencia(limiteBanco);
        this.vagasRecomputacao = new Semaphore(limiteRecomputacoes);
    }

    public T obter(String chave) throws Exception {
        Objects.requireNonNull(chave);
        T valor = lerDisponivel(chave);
        if (valor != null) return valor;
        // Perdedor da mesma chave sai antes de disputar vaga: uma rajada na chave quente não rejeita outras chaves.
        if (emRecomputacao.containsKey(chave)) throw new RejectedExecutionException("Recomputação em andamento para a chave");
        // Vaga antes de registrar a chave: assim o mapa nunca guarda mais chaves do que o limite.
        if (!vagasRecomputacao.tryAcquire()) throw new RejectedExecutionException("Limite de recomputações atingido");
        try {
            if (emRecomputacao.putIfAbsent(chave, Boolean.TRUE) != null) {
                throw new RejectedExecutionException("Recomputação em andamento para a chave");
            }
            try {
                return recomputar(chave);
            } finally {
                emRecomputacao.remove(chave);
            }
        } finally {
            vagasRecomputacao.release();
        }
    }

    private T recomputar(String chave) throws Exception {
        // Outro chamador pode ter gravado enquanto esperávamos a vaga.
        T valor = lerDisponivel(chave);
        if (valor != null) return valor;
        T carregado = banco.executar(() -> origem.carregar(chave));
        if (carregado != null) {
            try {
                gravar.accept(chave, carregado);
            } catch (RuntimeException indisponivel) {
                // O valor da origem continua válido sem o cache.
            }
        }
        return carregado;
    }

    private T lerDisponivel(String chave) {
        try {
            return ler.apply(chave);
        } catch (RuntimeException indisponivel) {
            return null;
        }
    }
}
