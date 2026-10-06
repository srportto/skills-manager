package br.com.srportto.exemplos;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encerramento gracioso: recusa trabalho novo, drena o que está em andamento até um prazo e relata o que
 * ficou pendente, para que o chamador não confirme (ack/commit) trabalho não concluído.
 */
public final class EncerramentoControlado implements AutoCloseable {
    public record Relatorio(int pendentes) {
        public boolean drenado() { return pendentes == 0; }
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ReentrantLock trava = new ReentrantLock();
    private final Condition semAtivas = trava.newCondition();
    private boolean aceitando = true;
    private int ativas;

    /** Devolve false quando o encerramento já começou: o chamador decide rejeitar ou devolver a mensagem. */
    public boolean submeter(Runnable tarefa) {
        Objects.requireNonNull(tarefa);
        trava.lock();
        try {
            if (!aceitando) return false;
            ativas++;
        } finally {
            trava.unlock();
        }
        executor.execute(() -> {
            try {
                tarefa.run();
            } finally {
                concluir();
            }
        });
        return true;
    }

    private void concluir() {
        trava.lock();
        try {
            ativas--;
            if (ativas == 0) semAtivas.signalAll();
        } finally {
            trava.unlock();
        }
    }

    public Relatorio encerrar(Duration prazo) throws InterruptedException {
        trava.lock();
        try {
            aceitando = false;
            long restante = prazo.toNanos();
            // Espera por sinal de conclusão, limitada ao prazo; nenhum sleep arbitrário.
            while (ativas > 0 && restante > 0) restante = semAtivas.awaitNanos(restante);
            return new Relatorio(ativas);
        } finally {
            trava.unlock();
        }
    }

    @Override
    public void close() throws InterruptedException {
        encerrar(Duration.ZERO);
        // Interrompe o que restou; quem chamou já recebeu o relatório de pendências.
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }
}
