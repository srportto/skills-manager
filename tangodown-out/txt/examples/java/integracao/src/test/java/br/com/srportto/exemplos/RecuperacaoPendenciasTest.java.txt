package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.XAutoClaimParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class RecuperacaoPendenciasTest {
    private final UnifiedJedis redis = mock(UnifiedJedis.class);
    private final RecuperacaoPendencias recuperacao =
            new RecuperacaoPendencias(redis, "eventos", "processadores", Duration.ofMillis(500), 10);

    @DisplayName("RecuperacaoPendencias confirma cada mensagem que o efeito processa")
    @Test
    void leituraNovaDeveConfirmarCadaMensagemProcessadaComSucesso() {
        StreamEntry entrada = entrada("1-0", Map.of("id", "a"));
        when(redis.xreadGroup(eq("processadores"), eq("c1"), any(XReadGroupParams.class), anyMap()))
                .thenReturn(List.of(Map.entry("eventos", List.of(entrada))));

        int confirmadas = recuperacao.lerNovas("c1", campos -> {});

        assertEquals(1, confirmadas);
        verify(redis).xack("eventos", "processadores", entrada.getID());
    }

    @DisplayName("RecuperacaoPendencias mantem a entrada pendente quando o efeito falha")
    @Test
    void falhaNoEfeitoDeveManterEntradaPendenteSemConfirmar() {
        StreamEntry entrada = entrada("2-0", Map.of("id", "b"));
        when(redis.xreadGroup(eq("processadores"), eq("c1"), any(XReadGroupParams.class), anyMap()))
                .thenReturn(List.of(Map.entry("eventos", List.of(entrada))));

        assertEquals(0, recuperacao.lerNovas("c1", campos -> { throw new IllegalStateException("falha"); }));

        verify(redis, never()).xack(anyString(), anyString(), any(StreamEntryID.class));
    }

    @DisplayName("RecuperacaoPendencias usa a ociosidade minima configurada ao reivindicar")
    @Test
    void reivindicacaoDeveRespeitarOciosidadeMinimaConfigurada() {
        when(redis.xautoclaim(eq("eventos"), eq("processadores"), eq("c2"), eq(500L),
                any(StreamEntryID.class), any(XAutoClaimParams.class)))
                .thenReturn(Map.entry(new StreamEntryID("0-0"), List.of()));

        assertEquals(0, recuperacao.reivindicar("c2", campos -> {}));

        verify(redis).xautoclaim(eq("eventos"), eq("processadores"), eq("c2"), eq(500L),
                any(StreamEntryID.class), any(XAutoClaimParams.class));
    }

    @DisplayName("RecuperacaoPendencias processa e confirma a mensagem reivindicada")
    @Test
    void mensagemReivindicadaDepoisDaOciosidadeDeveSerProcessadaEConfirmada() {
        StreamEntry entrada = entrada("3-0", Map.of("id", "c"));
        when(redis.xautoclaim(eq("eventos"), eq("processadores"), eq("c2"), eq(500L),
                any(StreamEntryID.class), any(XAutoClaimParams.class)))
                .thenReturn(Map.entry(new StreamEntryID("0-0"), List.of(entrada)));

        assertEquals(1, recuperacao.reivindicar("c2", campos -> assertEquals("c", campos.get("id"))));

        verify(redis).xack("eventos", "processadores", entrada.getID());
    }

    @DisplayName("RecuperacaoPendencias ignora BUSYGROUP ao preparar grupo existente")
    @Test
    void prepararGrupoDuasVezesDeveIgnorarSomenteGrupoJaExistente() {
        doThrow(new JedisDataException("BUSYGROUP Consumer Group name already exists"))
                .when(redis).xgroupCreate(eq("eventos"), eq("processadores"), any(StreamEntryID.class), eq(true));

        assertDoesNotThrow(recuperacao::prepararGrupo);
        assertDoesNotThrow(recuperacao::prepararGrupo);
    }

    @DisplayName("RecuperacaoPendencias registra WARN e mantem pendente a falha recuperavel")
    @Test
    void falhaRecuperavelDeveGerarWarnEManterEntradaPendente(CapturedOutput saida) {
        StreamEntry entrada = entrada("4-0", Map.of("id", "d"));
        when(redis.xreadGroup(eq("processadores"), eq("c1"), any(XReadGroupParams.class), anyMap()))
                .thenReturn(List.of(Map.entry("eventos", List.of(entrada))));

        assertEquals(0, recuperacao.lerNovas("c1", campos -> {
            throw new JedisDataException("coordenador temporariamente indisponivel");
        }));

        org.junit.jupiter.api.Assertions.assertTrue(saida.getOut().contains("WARN"));
        verify(redis, never()).xack(anyString(), anyString(), any(StreamEntryID.class));
    }

    @DisplayName("RecuperacaoPendencias registra ERROR e incrementa a metrica opcional em bug interno")
    @Test
    void bugInternoDeveGerarErrorEIncrementarMetricaOpcional(CapturedOutput saida) {
        StreamEntry entrada = entrada("5-0", Map.of("id", "e"));
        when(redis.xreadGroup(eq("processadores"), eq("c1"), any(XReadGroupParams.class), anyMap()))
                .thenReturn(List.of(Map.entry("eventos", List.of(entrada))));
        var falhas = new java.util.concurrent.atomic.AtomicInteger();
        var observada = new RecuperacaoPendencias(redis, "eventos", "processadores", Duration.ofMillis(500),
                10, falhas::incrementAndGet);

        assertEquals(0, observada.lerNovas("c1", campos -> { throw new NullPointerException("bug"); }));

        org.junit.jupiter.api.Assertions.assertTrue(saida.getOut().contains("ERROR"));
        assertEquals(1, falhas.get());
        verify(redis, never()).xack(anyString(), anyString(), any(StreamEntryID.class));
    }

    private StreamEntry entrada(String id, Map<String, String> campos) {
        StreamEntry entrada = mock(StreamEntry.class);
        when(entrada.getID()).thenReturn(new StreamEntryID(id));
        when(entrada.getFields()).thenReturn(campos);
        return entrada;
    }
}