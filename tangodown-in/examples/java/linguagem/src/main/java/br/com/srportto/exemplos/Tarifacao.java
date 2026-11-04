package br.com.srportto.exemplos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/** Switch moderno: padrões de tipo e de record, guarda {@code when}, {@code case null}, {@code _}, rótulos múltiplos e yield. */
public final class Tarifacao {
    private static final BigDecimal TAXA_CARTAO = new BigDecimal("0.0299");
    private static final BigDecimal ACRESCIMO_POR_PARCELA = new BigDecimal("0.0100");
    private static final BigDecimal TARIFA_BOLETO = new BigDecimal("2.50");

    public enum Canal { APP, WEB, LOJA, TELEFONE }

    private Tarifacao() {}

    /** Exaustivo sobre o tipo selado e sem default: um subtipo novo de Pagamento quebra a compilação aqui. */
    public static BigDecimal taxa(Pagamento pagamento) {
        return switch (pagamento) {
            // Sem "case null", um pagamento nulo lançaria NullPointerException.
            case null -> throw new IllegalArgumentException("Pagamento ausente");
            case Pagamento.Pix _ -> BigDecimal.ZERO;
            // A guarda refina o padrão e precisa vir antes do caso sem guarda do mesmo tipo (dominância).
            case Pagamento.Cartao(_, int parcelas, BigDecimal valor) when parcelas > 1 ->
                    percentual(valor, TAXA_CARTAO.add(ACRESCIMO_POR_PARCELA.multiply(BigDecimal.valueOf(parcelas - 1))));
            case Pagamento.Cartao(_, _, BigDecimal valor) -> percentual(valor, TAXA_CARTAO);
            case Pagamento.Boleto _ -> TARIFA_BOLETO;
        };
    }

    /** Switch sobre enum sem default: exaustivo; adicionar um Canal sem tratá-lo não compila. */
    public static Duration prazoEstorno(Canal canal) {
        return switch (canal) {
            case APP, WEB -> Duration.ofDays(1);
            case LOJA -> Duration.ofDays(3);
            case TELEFONE -> {
                // Ramo com mais de uma instrução devolve o valor com yield.
                Duration analiseManual = Duration.ofDays(2);
                yield analiseManual.plus(Duration.ofDays(3));
            }
        };
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal taxa) {
        return valor.multiply(taxa).setScale(2, RoundingMode.HALF_EVEN);
    }
}
