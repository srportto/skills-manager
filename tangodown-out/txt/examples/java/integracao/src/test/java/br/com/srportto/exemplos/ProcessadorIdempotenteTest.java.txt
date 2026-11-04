package br.com.srportto.exemplos;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessadorIdempotenteTest {
    static DataSource banco() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        return ds;
    }

    @DisplayName("ProcessadorIdempotente: Duplicatas concorrentes e reinicio devem produzir um efeito")
    @Test
    void duplicatasConcorrentesEReinicioDevemProduzirUmEfeito() throws Exception {
        DataSource ds = banco();
        var processador = new ProcessadorIdempotente(ds);
        processador.preparar();
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var respostas = new ArrayList<Future<String>>();
            for (int i = 0; i < 8; i++) {
                respostas.add(executor.submit(() -> {
                    if (!iniciar.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
                    return processador.processar("tenant-a", "pedido-1", 1500);
                }));
            }
            iniciar.countDown();
            var ids = new HashSet<String>();
            for (var resposta : respostas) ids.add(resposta.get(10, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            // "Reinício": nova instância sobre o mesmo banco devolve o resultado anterior.
            assertEquals(ids.iterator().next(), new ProcessadorIdempotente(ds).processar("tenant-a", "pedido-1", 1500));
        }
        assertEquals(1, processador.quantidadePedidos());
    }

    @DisplayName("ProcessadorIdempotente: Mesma chave com payload diferente deve conflitar e escopo por tenant deve isolar")
    @Test
    void mesmaChaveComPayloadDiferenteDeveConflitarEEscopoPorTenantDeveIsolar() throws Exception {
        var processador = new ProcessadorIdempotente(banco());
        processador.preparar();
        processador.processar("tenant-a", "pedido-1", 1500);
        assertThrows(IllegalArgumentException.class, () -> processador.processar("tenant-a", "pedido-1", 2000));
        processador.processar("tenant-b", "pedido-1", 2000);
        assertEquals(2, processador.quantidadePedidos());
    }

    @DisplayName("ProcessadorIdempotente: Entrada invalida nao deve gravar nada")
    @Test
    void entradaInvalidaNaoDeveGravarNada() throws Exception {
        var processador = new ProcessadorIdempotente(banco());
        processador.preparar();
        assertThrows(IllegalArgumentException.class, () -> processador.processar("", "chave", 10));
        assertThrows(IllegalArgumentException.class, () -> processador.processar("t", "chave", 0));
        assertEquals(0, processador.quantidadePedidos());
    }
}
