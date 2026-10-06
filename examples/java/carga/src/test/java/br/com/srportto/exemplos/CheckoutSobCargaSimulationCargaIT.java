package br.com.srportto.exemplos;

import br.com.srportto.exemplos.CheckoutSobCargaSimulation.Fase;
import br.com.srportto.exemplos.CheckoutSobCargaSimulation.ResultadoFase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensaio sintético de laboratório (perfil `carga`): prova invariantes sob sobrecarga — rejeição rápida e visível,
 * latência dos aceitos limitada, nenhum efeito duplicado e recuperação após o pico. Os números dependem da
 * máquina e NÃO são SLO de produção; o relatório registra parâmetros e resultados.
 */
class CheckoutSobCargaSimulationCargaIT {
    // Capacidade do alvo ≈ 8 simultâneas ÷ 20 ms ≈ 400 req/s (hipótese do laboratório).
    static final int CAPACIDADE = 8;
    static final Duration LATENCIA_PAGAMENTO = Duration.ofMillis(20);
    static ConfigurableApplicationContext checkout;

    @BeforeAll
    static void iniciar() {
        // Argumentos de linha de comando têm precedência sobre o application.yaml (default properties não teriam).
        checkout = SpringApplication.run(CheckoutApplication.class,
                "--server.port=0",
                "--checkout.capacidade=" + CAPACIDADE,
                "--checkout.pagamento.latencia=" + LATENCIA_PAGAMENTO.toMillis() + "ms",
                "--spring.datasource.url=jdbc:h2:mem:carga-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
    }

    @AfterAll
    static void parar() {
        if (checkout != null) checkout.close();
    }

    @Test
    void sobrecargaDeveSerRejeitadaCedoSemDuplicarEfeitosERecuperar() throws Exception {
        String porta = checkout.getEnvironment().getProperty("local.server.port");
        var simulacao = new CheckoutSobCargaSimulation(URI.create("http://localhost:" + porta + "/pedidos"), 300);

        List<ResultadoFase> resultados = simulacao.executar(List.of(
                new Fase("baseline", 50, Duration.ofSeconds(2)),
                new Fase("rampa", 150, Duration.ofSeconds(2)),
                new Fase("pico-acima-da-capacidade", 1_000, Duration.ofSeconds(3)),
                new Fase("retorno", 50, Duration.ofSeconds(2))));
        Map<String, ResultadoFase> porFase = resultados.stream().collect(Collectors.toMap(ResultadoFase::nome, r -> r));
        registrar(resultados);

        var baseline = porFase.get("baseline");
        var pico = porFase.get("pico-acima-da-capacidade");
        var retorno = porFase.get("retorno");

        assertTrue(baseline.rejeitadas() <= baseline.oferecidas() / 100, "baseline rejeitou: " + baseline);
        // Sob sobrecarga a rejeição aparece (503) e não há erro de outro tipo.
        assertTrue(pico.rejeitadas() > 0, "pico sem rejeição: " + pico);
        assertEquals(0, resultados.stream().mapToInt(ResultadoFase::erros).sum(), resultados::toString);
        // Quem é aceito não espera numa fila crescente: latência limitada mesmo no pico.
        assertTrue(pico.p99AceitasMs() < 1_000, "p99 dos aceitos no pico: " + pico.p99AceitasMs() + " ms");
        // Recuperação: depois do pico, praticamente tudo volta a ser aceito.
        assertTrue(retorno.aceitas() >= retorno.oferecidas() * 99 / 100, "retorno: " + retorno);
        // Efeito único: cada requisição aceita (chave única) gerou exatamente um pedido.
        long pedidos = checkout.getBean(ProcessadorIdempotente.class).quantidadePedidos();
        assertEquals(resultados.stream().mapToInt(ResultadoFase::aceitas).sum(), pedidos);
    }

    private static void registrar(List<ResultadoFase> resultados) throws Exception {
        var linhas = new StringBuilder("Ensaio de carga do checkout (laboratório)\n")
                .append("JDK ").append(Runtime.version()).append(", CPUs ").append(Runtime.getRuntime().availableProcessors())
                .append(", capacidade ").append(CAPACIDADE).append(", latência simulada ").append(LATENCIA_PAGAMENTO.toMillis()).append(" ms\n");
        resultados.forEach(r -> linhas.append(r).append('\n'));
        Path relatorio = Path.of("target", "carga-relatorio.txt");
        Files.createDirectories(relatorio.getParent());
        Files.writeString(relatorio, linhas);
        System.out.print(linhas);
    }
}
