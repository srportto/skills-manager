package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class ControleConcorrencia {
    private final Semaphore permissoes;

    public ControleConcorrencia(int limite) {
        if (limite <= 0) throw new IllegalArgumentException("Limite deve ser positivo");
        permissoes = new Semaphore(limite);
    }

    private void adquirir() {
        if (!permissoes.tryAcquire()) throw new RejectedExecutionException("Recurso sem capacidade");
    }

    public <T> T executar(Callable<T> operacao) throws Exception {
        Objects.requireNonNull(operacao);
        adquirir();
        try { return operacao.call(); }
        finally { permissoes.release(); }
    }

    public <T> CompletionStage<T> executarAsync(Supplier<CompletionStage<T>> operacao) {
        Objects.requireNonNull(operacao);
        adquirir();
        CompletionStage<T> origem;
        try { origem = Objects.requireNonNull(operacao.get()); }
        catch (RuntimeException | Error erro) { permissoes.release(); throw erro; }
        var resposta = new CompletableFuture<T>();
        // Cancelar a resposta não libera a permissão do trabalho ainda ativo.
        origem.whenComplete((valor, erro) -> {
            permissoes.release();
            if (erro == null) resposta.complete(valor);
            else resposta.completeExceptionally(erro);
        });
        return resposta;
    }
}

