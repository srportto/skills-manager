package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TarifacaoTest {
    private static final BigDecimal CEM = new BigDecimal("100.00");

    @DisplayName("Tarifacao: Taxa deve depender do tipo e dos componentes do pagamento")
    @Test
    void taxaDeveDependerDoTipoEDosComponentesDoPagamento() {
        assertEquals(BigDecimal.ZERO, Tarifacao.taxa(new Pagamento.Pix("chave", CEM)));
        assertEquals(new BigDecimal("2.99"), Tarifacao.taxa(new Pagamento.Cartao(Pagamento.Bandeira.VISA, 1, CEM)));
        // Guarda "when parcelas > 1": 2,99% + 1% por parcela adicional.
        assertEquals(new BigDecimal("4.99"), Tarifacao.taxa(new Pagamento.Cartao(Pagamento.Bandeira.VISA, 3, CEM)));
        assertEquals(new BigDecimal("2.50"), Tarifacao.taxa(new Pagamento.Boleto("linha", CEM)));
    }

    @DisplayName("Tarifacao: Pagamento nulo deve ser tratado pelo case null")
    @Test
    void pagamentoNuloDeveSerTratadoPeloCaseNull() {
        var erro = assertThrows(IllegalArgumentException.class, () -> Tarifacao.taxa(null));
        assertEquals("Pagamento ausente", erro.getMessage());
    }

    @DisplayName("Tarifacao: Prazo de estorno deve usar rotulos multiplos e yield")
    @Test
    void prazoDeEstornoDeveUsarRotulosMultiplosEYield() {
        assertEquals(Duration.ofDays(1), Tarifacao.prazoEstorno(Tarifacao.Canal.APP));
        assertEquals(Duration.ofDays(1), Tarifacao.prazoEstorno(Tarifacao.Canal.WEB));
        assertEquals(Duration.ofDays(3), Tarifacao.prazoEstorno(Tarifacao.Canal.LOJA));
        assertEquals(Duration.ofDays(5), Tarifacao.prazoEstorno(Tarifacao.Canal.TELEFONE));
    }
}
