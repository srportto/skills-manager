package br.com.srportto.exemplos;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

public final class CacheProtegido<T> {
    @FunctionalInterface public interface Carregador<T> { T carregar(String chave) throws Exception; }
    private final Function<String, T> ler;
    private final BiConsumer<String, T> gravar;
    private final Carregador<T> origem;
    private final ControleConcorrencia banco;
    private final ReentrantLock[] faixas;

    public CacheProtegido(Function<String, T> ler, BiConsumer<String, T> gravar,
                          Carregador<T> origem, int limiteBanco, int quantidadeFaixas) {
        if (quantidadeFaixas <= 0) throw new IllegalArgumentException("Faixas devem ser positivas");
        this.ler = Objects.requireNonNull(ler); this.gravar = Objects.requireNonNull(gravar);
        this.origem = Objects.requireNonNull(origem); banco = new ControleConcorrencia(limiteBanco);
        faixas = new ReentrantLock[quantidadeFaixas];
        Arrays.setAll(faixas, indice -> new ReentrantLock());
    }

    private T lerDisponivel(String chave) {
        try { return ler.apply(chave); }
        catch (RuntimeException indisponivel) { return null; }
    }

    public T obter(String chave) throws Exception {
        Objects.requireNonNull(chave);
        T valor = lerDisponivel(chave);
        if (valor != null) return valor;
        var faixa = faixas[Math.floorMod(chave.hashCode(), faixas.length)];
        // Faixas fixas evitam um mapa de locks com cardinalidade ilimitada.
        if (!faixa.tryLock()) throw new RejectedExecutionException("Recomputação em andamento");
        try {
            valor = lerDisponivel(chave);
            if (valor != null) return valor;
            T carregado = banco.executar(() -> origem.carregar(chave));
            if (carregado != null) {
                try { gravar.accept(chave, carregado); }
                catch (RuntimeException indisponivel) { /* O valor retornado continua válido sem cache. */ }
            }
            return carregado;
        } finally { faixa.unlock(); }
    }
}

