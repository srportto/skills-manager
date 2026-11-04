package br.com.srportto.exemplos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Mesma prova do teste local, agora em PostgreSQL real: a restrição única arbitra conexões concorrentes. */
class ProcessadorIdempotenteExternoIT {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(ServicosExternos.POSTGRES);
    static DataSource banco;

    @BeforeAll
    static void iniciar() throws Exception {
        POSTGRES.start();
        banco = ServicosExternos.dataSource(POSTGRES);
        new ProcessadorIdempotente(banco).preparar();
    }

    @AfterAll
    static void parar() {
        POSTGRES.stop();
    }

    @DisplayName("ProcessadorIdempotenteExterno: Duplicatas concorrentes em conexoes distintas devem gerar um unico pedido")
    @Test
    void duplicatasConcorrentesEmConexoesDistintasDevemGerarUmUnicoPedido() throws Exception {
        int concorrentes = 16;
        var largada = new CyclicBarrier(concorrentes);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var respostas = new ArrayList<Future<String>>();
            for (int i = 0; i < concorrentes; i++) {
                respostas.add(executor.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    // Cada instância representa uma réplica diferente da aplicação.
                    return new ProcessadorIdempotente(banco).processar("tenant-pg", "pedido-42", 990);
                }));
            }
            var ids = new HashSet<String>();
            for (var resposta : respostas) ids.add(resposta.get(30, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessadorIdempotente(banco).processar("tenant-pg", "pedido-42", 991));
    }
}
