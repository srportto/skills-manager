package br.com.srportto.exemplos;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Meios de pagamento como hierarquia selada. Os records aninhados são os únicos subtipos ({@code permits}
 * implícito por estarem no mesmo arquivo) e, sendo {@code final}, fecham a hierarquia aqui.
 */
public sealed interface Pagamento {
    BigDecimal valor();

    record Pix(String chave, BigDecimal valor) implements Pagamento {
        public Pix {
            Objects.requireNonNull(chave);
            exigirPositivo(valor);
        }
    }

    record Cartao(Bandeira bandeira, int parcelas, BigDecimal valor) implements Pagamento {
        public Cartao {
            Objects.requireNonNull(bandeira);
            if (parcelas < 1 || parcelas > 12) throw new IllegalArgumentException("Parcelas fora de 1..12: " + parcelas);
            exigirPositivo(valor);
        }
    }

    record Boleto(String linhaDigitavel, BigDecimal valor) implements Pagamento {
        public Boleto {
            Objects.requireNonNull(linhaDigitavel);
            exigirPositivo(valor);
        }
    }

    enum Bandeira { VISA, MASTERCARD, ELO }

    private static void exigirPositivo(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Valor deve ser positivo");
    }
}
