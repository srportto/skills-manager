package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.srportto.exemplos.ResultadoCobranca.Acao.CONCLUIR;
import static br.com.srportto.exemplos.ResultadoCobranca.Acao.INFORMAR_RECUSA;
import static br.com.srportto.exemplos.ResultadoCobranca.Acao.OFERECER_OUTRO_MEIO;
import static br.com.srportto.exemplos.ResultadoCobranca.Acao.RECONCILIAR;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultadoCobrancaTest {
    @DisplayName("ResultadoCobranca: Cada resultado deve levar a uma acao explicita")
    @Test
    void cadaResultadoDeveLevarAUmaAcaoExplicita() {
        assertEquals(CONCLUIR, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Aprovada("aut-1")));
        assertEquals(OFERECER_OUTRO_MEIO, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Recusada("SALDO_INSUFICIENTE")));
        assertEquals(INFORMAR_RECUSA, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Recusada("CARTAO_BLOQUEADO")));
    }

    @DisplayName("ResultadoCobranca: Resultado desconhecido deve reconciliar e nunca repetir a cobranca")
    @Test
    void resultadoDesconhecidoDeveReconciliarENuncaRepetirACobranca() {
        assertEquals(RECONCILIAR, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Desconhecida("chave-123")));
    }
}
