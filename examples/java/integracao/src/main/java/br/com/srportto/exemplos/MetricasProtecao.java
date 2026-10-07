package br.com.srportto.exemplos;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Métricas de proteção com cardinalidade controlada:
 * <ul>
 *   <li>{@code app.requisicoes} (timer) — requisições <b>lógicas</b> por operação e resultado
 *       (sucesso | erro | rejeitada). Rejeição por saturação entra no denominador do SLI, não some.</li>
 *   <li>{@code app.dependencia.tentativas} (counter) — cada tentativa à dependência, separada da requisição
 *       lógica (retries não inflam nem escondem a taxa de erro do usuário).</li>
 *   <li>{@code app.fila.*} (gauges) — ocupação em itens e bytes e capacidade.</li>
 * </ul>
 * Tags só aceitam valores de conjuntos fechados; ids, paths e traceIds vão para logs/traces/exemplars.
 */
public final class MetricasProtecao {
    private static final String OUTRA = "outra";

    private final MeterRegistry registro;
    private final Set<String> operacoes;
    private final Set<String> dependencias;

    public MetricasProtecao(MeterRegistry registro, Set<String> operacoes, Set<String> dependencias) {
        this.registro = Objects.requireNonNull(registro);
        this.operacoes = Set.copyOf(operacoes);
        this.dependencias = Set.copyOf(dependencias);
    }

    public <T> T medir(String operacao, Callable<T> trabalho) throws Exception {
        String tagOperacao = operacoes.contains(operacao) ? operacao : OUTRA;
        long inicio = System.nanoTime();
        String resultado = "erro";
        try {
            T valor = trabalho.call();
            resultado = "sucesso";
            return valor;
        } catch (RejectedExecutionException | AdmissaoPorPrioridade.Rejeitada rejeicao) {
            resultado = "rejeitada";
            throw rejeicao;
        } finally {
            Timer.builder("app.requisicoes")
                    .description("Requisições lógicas por operação e resultado")
                    .tag("operacao", tagOperacao)
                    .tag("resultado", resultado)
                    .publishPercentileHistogram()
                    .register(registro)
                    .record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
        }
    }

    public void tentativa(String dependencia, boolean sucesso) {
        Counter.builder("app.dependencia.tentativas")
                .description("Tentativas (inclusive retries) por dependência")
                .tag("dependencia", dependencias.contains(dependencia) ? dependencia : OUTRA)
                .tag("resultado", sucesso ? "sucesso" : "falha")
                .register(registro)
                .increment();
    }

    /** O nome da fila deve ser constante do código (cardinalidade fixa), nunca dado de requisição. */
    public void observarFila(String nome, FilaObservavel fila) {
        Gauge.builder("app.fila.itens", fila, FilaObservavel::tamanho).tag("fila", nome).register(registro);
        Gauge.builder("app.fila.bytes", fila, FilaObservavel::bytes).baseUnit("bytes").tag("fila", nome).register(registro);
        Gauge.builder("app.fila.capacidade.itens", fila, FilaObservavel::capacidade).tag("fila", nome).register(registro);
    }
}
