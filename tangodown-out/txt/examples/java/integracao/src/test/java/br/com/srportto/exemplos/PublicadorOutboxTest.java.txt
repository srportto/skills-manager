package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicadorOutboxTest {
    private final DataSource ds = ProcessadorIdempotenteTest.banco();

    @DisplayName("PublicadorOutbox: Falha na publicacao deve preservar evento para nova tentativa")
    @Test
    void falhaNaPublicacaoDevePreservarEventoParaNovaTentativa() throws Exception {
        var processador = new ProcessadorIdempotente(ds);
        processador.preparar();
        String id = processador.processar("tenant", "chave", 100);
        var outbox = new PublicadorOutbox(ds);

        assertThrows(IllegalStateException.class, () -> outbox.publicar(10, () -> true, evento -> {
            throw new IllegalStateException("Destino indisponível");
        }));
        var eventos = new ArrayList<String>();
        // Sem quota de publicação, nada sai (o relay respeita a capacidade do destino).
        assertEquals(0, outbox.publicar(10, () -> false, eventos::add));
        assertEquals(1, outbox.publicar(10, () -> true, eventos::add));
        assertEquals(List.of(id), eventos);
        assertEquals(0, outbox.publicar(10, () -> true, eventos::add));
    }

    @DisplayName("PublicadorOutbox: Eventos devem sair na ordem de criacao")
    @Test
    void eventosDevemSairNaOrdemDeCriacao() throws Exception {
        var processador = new ProcessadorIdempotente(ds);
        processador.preparar();
        var criados = new ArrayList<String>();
        for (int i = 0; i < 20; i++) criados.add(processador.processar("tenant", "chave-" + i, 100 + i));

        var publicados = new ArrayList<String>();
        new PublicadorOutbox(ds).publicar(100, () -> true, publicados::add);
        assertEquals(criados, publicados);
    }

    @DisplayName("PublicadorOutbox: Falha no meio do lote nao deve perder nem reenviar os ja confirmados")
    @Test
    void falhaNoMeioDoLoteNaoDevePerderNemReenviarOsJaConfirmados() throws Exception {
        var processador = new ProcessadorIdempotente(ds);
        processador.preparar();
        String primeiro = processador.processar("tenant", "a", 1);
        String segundo = processador.processar("tenant", "b", 2);
        var outbox = new PublicadorOutbox(ds);
        var enviados = new ArrayList<String>();

        assertThrows(IllegalStateException.class, () -> outbox.publicar(10, () -> true, evento -> {
            if (evento.equals(segundo)) throw new IllegalStateException("broker caiu");
            enviados.add(evento);
        }));
        assertEquals(1, outbox.publicar(10, () -> true, enviados::add));
        assertEquals(List.of(primeiro, segundo), enviados);
    }

    @DisplayName("PublicadorOutbox: Lote deve ser limitado")
    @Test
    void loteDeveSerLimitado() {
        var outbox = new PublicadorOutbox(ds);
        assertThrows(IllegalArgumentException.class, () -> outbox.publicar(0, () -> true, evento -> {}));
        assertThrows(IllegalArgumentException.class, () -> outbox.publicar(1001, () -> true, evento -> {}));
    }

    @DisplayName("PublicadorOutbox para no limite da quota e preserva os eventos anteriores")
    @Test
    void quotaEsgotadaDevePararSemDesfazerPublicacoesAnteriores() throws Exception {
        var processador = new ProcessadorIdempotente(ds);
        processador.preparar();
        String primeiro = processador.processar("tenant", "quota-a", 1);
        processador.processar("tenant", "quota-b", 2);
        var chamadas = new java.util.concurrent.atomic.AtomicInteger();
        var publicados = new ArrayList<String>();

        assertEquals(1, new PublicadorOutbox(ds).publicar(10,
                () -> chamadas.incrementAndGet() == 1, publicados::add));
        assertEquals(List.of(primeiro), publicados);
    }
}
