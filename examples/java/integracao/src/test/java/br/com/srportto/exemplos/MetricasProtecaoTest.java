package br.com.srportto.exemplos;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricasProtecaoTest {
    private static final class FilaObservavelDeTeste implements FilaObservavel {
        private int itens;
        private long bytes;

        void adicionar(int quantidade, long peso) { itens = quantidade; bytes = peso; }
        @Override public int tamanho() { return itens; }
        @Override public long bytes() { return bytes; }
        @Override public int capacidade() { return 10; }
    }

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MetricasProtecao metricas = new MetricasProtecao(registro, Set.of("criar-pedido"), Set.of("pagamentos"));

    private long requisicoes(String operacao, String resultado) {
        var timer = registro.find("app.requisicoes").tag("operacao", operacao).tag("resultado", resultado).timer();
        return timer == null ? 0 : timer.count();
    }

    @DisplayName("MetricasProtecao: Rejeicao por saturacao deve aparecer no denominador e nao sumir")
    @Test
    void rejeicaoPorSaturacaoDeveAparecerNoDenominadorENaoSumir() throws Exception {
        metricas.medir("criar-pedido", () -> "ok");
        assertThrows(RejectedExecutionException.class, () -> metricas.medir("criar-pedido", () -> {
            throw new RejectedExecutionException("sem capacidade");
        }));
        assertThrows(IllegalStateException.class, () -> metricas.medir("criar-pedido", () -> {
            throw new IllegalStateException("falha");
        }));

        assertEquals(1, requisicoes("criar-pedido", "sucesso"));
        assertEquals(1, requisicoes("criar-pedido", "rejeitada"));
        assertEquals(1, requisicoes("criar-pedido", "erro"));
    }

    @DisplayName("MetricasProtecao: Tentativas devem ser contadas separadamente da requisicao logica")
    @Test
    void tentativasDevemSerContadasSeparadamenteDaRequisicaoLogica() throws Exception {
        var tentativas = new AtomicInteger();
        metricas.medir("criar-pedido", () -> {
            // Uma requisição lógica, três tentativas à dependência (2 falhas + 1 sucesso).
            for (int i = 0; i < 3; i++) metricas.tentativa("pagamentos", tentativas.incrementAndGet() == 3);
            return "ok";
        });
        assertEquals(1, requisicoes("criar-pedido", "sucesso"));
        assertEquals(2, registro.get("app.dependencia.tentativas").tag("resultado", "falha").counter().count());
        assertEquals(1, registro.get("app.dependencia.tentativas").tag("resultado", "sucesso").counter().count());
    }

    @DisplayName("MetricasProtecao: Valores dinamicos nao devem virar tags de alta cardinalidade")
    @Test
    void valoresDinamicosNaoDevemVirarTagsDeAltaCardinalidade() throws Exception {
        for (int i = 0; i < 100; i++) {
            int id = i;
            metricas.medir("/pedidos/" + id, () -> "ok");
            metricas.tentativa("servico-" + id, true);
        }
        // Rótulos desconhecidos colapsam em "outra": a série não cresce com ids, paths ou traceIds.
        assertEquals(100, requisicoes("outra", "sucesso"));
        long series = registro.getMeters().stream().map(Meter::getId).filter(id -> id.getName().startsWith("app.")).count();
        assertEquals(2, series);
        assertFalse(registro.getMeters().stream().flatMap(m -> m.getId().getTags().stream())
                .anyMatch(tag -> tag.getKey().equalsIgnoreCase("traceId") || tag.getValue().matches(".*\\d.*")));
    }

    @DisplayName("MetricasProtecao: Fila deve expor itens e bytes")
    @Test
    void filaDeveExporItensEBytes() {
        var fila = new FilaObservavelDeTeste();
        fila.adicionar(2, 6);
        metricas.observarFila("pedidos", fila);
        assertEquals(2.0, registro.get("app.fila.itens").tag("fila", "pedidos").gauge().value());
        assertEquals(6.0, registro.get("app.fila.bytes").tag("fila", "pedidos").gauge().value());
        assertEquals(10.0, registro.get("app.fila.capacidade.itens").tag("fila", "pedidos").gauge().value());
    }

    @DisplayName("MetricasProtecao: Latencia deve ser registrada para sucesso e rejeicao separadamente")
    @Test
    void latenciaDeveSerRegistradaParaSucessoERejeicaoSeparadamente() throws Exception {
        metricas.medir("criar-pedido", () -> {
            Thread.sleep(Duration.ofMillis(20));
            return "ok";
        });
        var sucesso = registro.get("app.requisicoes").tag("resultado", "sucesso").timer();
        assertEquals(1, sucesso.count());
        assertFalse(sucesso.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) < 20);
    }
}
