package br.com.srportto.exemplos;

import br.com.srportto.exemplos.FallbackDegradado.Origem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FallbackDegradadoTest {
    private final AtomicLong tempo = new AtomicLong();
    private final AtomicBoolean primariaFora = new AtomicBoolean();

    private FallbackDegradado<String> fallback(int maximoEntradas) {
        return new FallbackDegradado<>(chave -> {
            if (primariaFora.get()) throw new IllegalStateException("Recomendador indisponível");
            return "recomendacoes-" + chave;
        }, Duration.ofSeconds(30), maximoEntradas, tempo::get);
    }

    @DisplayName("FallbackDegradado: Deve usar primaria quando disponivel")
    @Test
    void deveUsarPrimariaQuandoDisponivel() throws Exception {
        var resposta = fallback(10).obter("vitrine");
        assertEquals("recomendacoes-vitrine", resposta.valor());
        assertEquals(Origem.PRIMARIA, resposta.origem());
        assertEquals(Duration.ZERO, resposta.idade());
    }

    @DisplayName("FallbackDegradado: Deve devolver valor anterior com idade visivel dentro do limite de frescor")
    @Test
    void deveDevolverValorAnteriorComIdadeVisivelDentroDoLimiteDeFrescor() throws Exception {
        var fallback = fallback(10);
        fallback.obter("vitrine");
        primariaFora.set(true);
        tempo.set(Duration.ofSeconds(20).toNanos());

        var resposta = fallback.obter("vitrine");
        assertEquals("recomendacoes-vitrine", resposta.valor());
        assertEquals(Origem.ULTIMO_VALOR_CONHECIDO, resposta.origem());
        assertEquals(Duration.ofSeconds(20), resposta.idade());
    }

    @DisplayName("FallbackDegradado: Nao deve inventar valor nem servir dado alem do frescor maximo")
    @Test
    void naoDeveInventarValorNemServirDadoAlemDoFrescorMaximo() throws Exception {
        var fallback = fallback(10);
        fallback.obter("vitrine");
        primariaFora.set(true);

        assertThrows(FallbackDegradado.Indisponivel.class, () -> fallback.obter("chave-nunca-vista"));
        tempo.set(Duration.ofSeconds(31).toNanos());
        assertThrows(FallbackDegradado.Indisponivel.class, () -> fallback.obter("vitrine"));
    }

    @DisplayName("FallbackDegradado: Memoria do fallback deve ser limitada")
    @Test
    void memoriaDoFallbackDeveSerLimitada() throws Exception {
        var fallback = fallback(2);
        fallback.obter("a");
        fallback.obter("b");
        fallback.obter("c");
        assertEquals(2, fallback.entradas());
        primariaFora.set(true);
        // A entrada mais antiga foi descartada para respeitar o limite.
        assertThrows(FallbackDegradado.Indisponivel.class, () -> fallback.obter("a"));
        assertEquals("recomendacoes-c", fallback.obter("c").valor());
    }
}
