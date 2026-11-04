package br.com.srportto.exemplos;

import br.com.srportto.exemplos.ConsumoControlado.Decisao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumoControladoTest {
    static final class MensagemInvalida extends RuntimeException {
        MensagemInvalida(String mensagem) { super(mensagem); }
    }

    private final List<String> quarentenadas = new ArrayList<>();

    private ConsumoControlado<String> controle(ConsumoControlado.Efeito<String> efeito, boolean quarentenaDisponivel) {
        return new ConsumoControlado<>(efeito, erro -> erro instanceof MensagemInvalida, (mensagem, causa) -> {
            if (!quarentenaDisponivel) throw new IllegalStateException("DLQ indisponível");
            quarentenadas.add(mensagem);
        });
    }

    @DisplayName("ConsumoControlado: Efeito concluido deve ser confirmado")
    @Test
    void efeitoConcluidoDeveSerConfirmado() {
        var resultado = controle(mensagem -> {}, true).consumir("m1");
        assertEquals(Decisao.CONFIRMAR, resultado.decisao());
    }

    @DisplayName("ConsumoControlado: Falha transitoria deve voltar para nova tentativa sem quarentena")
    @Test
    void falhaTransitoriaDeveVoltarParaNovaTentativaSemQuarentena() {
        var resultado = controle(mensagem -> { throw new IllegalStateException("banco lento"); }, true).consumir("m1");
        assertEquals(Decisao.REENTREGAR, resultado.decisao());
        assertInstanceOf(IllegalStateException.class, resultado.erro());
        assertTrue(quarentenadas.isEmpty());
    }

    @DisplayName("ConsumoControlado: Falha permanente deve ir para quarentena duravel antes de confirmar")
    @Test
    void falhaPermanenteDeveIrParaQuarentenaDuravelAntesDeConfirmar() {
        var resultado = controle(mensagem -> { throw new MensagemInvalida("schema"); }, true).consumir("m1");
        assertEquals(Decisao.CONFIRMAR, resultado.decisao());
        assertEquals(List.of("m1"), quarentenadas);
    }

    @DisplayName("ConsumoControlado: Quarentena indisponivel nao deve confirmar mensagem")
    @Test
    void quarentenaIndisponivelNaoDeveConfirmarMensagem() {
        var resultado = controle(mensagem -> { throw new MensagemInvalida("schema"); }, false).consumir("m1");
        // Sem cópia durável, confirmar seria perder a mensagem.
        assertEquals(Decisao.REENTREGAR, resultado.decisao());
        assertEquals(1, resultado.erro().getSuppressed().length);
        assertInstanceOf(MensagemInvalida.class, resultado.erro().getSuppressed()[0]);
    }

    @DisplayName("ConsumoControlado: Tentativas esgotadas devem ser quarentenadas explicitamente")
    @Test
    void tentativasEsgotadasDevemSerQuarentenadasExplicitamente() {
        var controle = controle(mensagem -> { throw new IllegalStateException(); }, true);
        assertEquals(Decisao.CONFIRMAR, controle.quarentenar("m1", new IllegalStateException("3 falhas")).decisao());
        assertEquals(List.of("m1"), quarentenadas);
        assertEquals(Decisao.REENTREGAR, controle(mensagem -> {}, false).quarentenar("m2", new IllegalStateException()).decisao());
    }

    @DisplayName("ConsumoControlado: Interrupcao deve ser preservada e nao confirmar")
    @Test
    void interrupcaoDeveSerPreservadaENaoConfirmar() {
        try {
            var resultado = controle(mensagem -> { throw new InterruptedException(); }, true).consumir("m1");
            assertEquals(Decisao.REENTREGAR, resultado.decisao());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
