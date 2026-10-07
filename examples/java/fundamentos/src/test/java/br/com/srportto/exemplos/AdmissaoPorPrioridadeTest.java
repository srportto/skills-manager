package br.com.srportto.exemplos;

import br.com.srportto.exemplos.AdmissaoPorPrioridade.Prioridade;
import br.com.srportto.exemplos.AdmissaoPorPrioridade.Rejeicao;
import br.com.srportto.exemplos.AdmissaoPorPrioridade.Rejeitada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdmissaoPorPrioridadeTest {
    private static final Duration FOLGA = Duration.ofSeconds(1);

    @DisplayName("AdmissaoPorPrioridade: Deve preservar reserva para fluxo critico quando saturado")
    @Test
    void devePreservarReservaParaFluxoCriticoQuandoSaturado() {
        // Capacidade 3, com 1 permissão reservada a CRITICA.
        var admissao = new AdmissaoPorPrioridade(3, 1, Duration.ofMillis(50));
        var normal1 = admissao.admitir(Prioridade.NORMAL, FOLGA);
        var normal2 = admissao.admitir(Prioridade.NORMAL, FOLGA);

        var rejeitada = assertThrows(Rejeitada.class, () -> admissao.admitir(Prioridade.NORMAL, FOLGA));
        assertEquals(Rejeicao.SATURADO, rejeitada.motivo());

        try (var critica = admissao.admitir(Prioridade.CRITICA, FOLGA)) {
            assertEquals(3, admissao.ativas());
            assertThrows(Rejeitada.class, () -> admissao.admitir(Prioridade.CRITICA, FOLGA));
        }
        normal1.close();
        normal2.close();
        assertEquals(0, admissao.ativas());
        assertEquals(2, admissao.rejeicoes(Rejeicao.SATURADO));
    }

    @DisplayName("AdmissaoPorPrioridade: Deve rejeitar cedo quando o deadline restante nao cobre o custo minimo")
    @Test
    void deveRejeitarCedoQuandoODeadlineRestanteNaoCobreOCustoMinimo() {
        var admissao = new AdmissaoPorPrioridade(2, 0, Duration.ofMillis(50));
        var rejeitada = assertThrows(Rejeitada.class,
                () -> admissao.admitir(Prioridade.CRITICA, Duration.ofMillis(10)));
        assertEquals(Rejeicao.DEADLINE_INSUFICIENTE, rejeitada.motivo());
        // A rejeição antecipada não consome capacidade.
        assertEquals(0, admissao.ativas());
        assertEquals(1, admissao.rejeicoes(Rejeicao.DEADLINE_INSUFICIENTE));
    }

    @DisplayName("AdmissaoPorPrioridade: Fechar duas vezes nao deve devolver capacidade extra")
    @Test
    void fecharDuasVezesNaoDeveDevolverCapacidadeExtra() {
        var admissao = new AdmissaoPorPrioridade(1, 0, Duration.ZERO);
        var permissao = admissao.admitir(Prioridade.NORMAL, FOLGA);
        permissao.close();
        permissao.close();
        admissao.admitir(Prioridade.NORMAL, FOLGA);
        assertThrows(Rejeitada.class, () -> admissao.admitir(Prioridade.NORMAL, FOLGA));
    }

    @DisplayName("AdmissaoPorPrioridade: Deve recusar configuracao invalida")
    @Test
    void deveRecusarConfiguracaoInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new AdmissaoPorPrioridade(0, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AdmissaoPorPrioridade(2, 2, Duration.ZERO));
    }
}
