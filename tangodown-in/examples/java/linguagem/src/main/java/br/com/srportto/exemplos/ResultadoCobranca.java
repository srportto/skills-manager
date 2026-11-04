package br.com.srportto.exemplos;

/** Resultado de uma cobrança remota; o tipo selado obriga quem consome a decidir o caso "desconhecido". */
public sealed interface ResultadoCobranca {
    record Aprovada(String autorizacao) implements ResultadoCobranca {}

    record Recusada(String motivo) implements ResultadoCobranca {}

    /** Timeout ou queda sem resposta: a cobrança pode ter acontecido no provedor. */
    record Desconhecida(String chaveIdempotencia) implements ResultadoCobranca {}

    enum Acao { CONCLUIR, OFERECER_OUTRO_MEIO, INFORMAR_RECUSA, RECONCILIAR }

    static Acao proximaAcao(ResultadoCobranca resultado) {
        return switch (resultado) {
            case Aprovada _ -> Acao.CONCLUIR;
            case Recusada(String motivo) when "SALDO_INSUFICIENTE".equals(motivo) -> Acao.OFERECER_OUTRO_MEIO;
            case Recusada _ -> Acao.INFORMAR_RECUSA;
            // Nunca repetir às cegas: consultar o provedor pela chave idempotente antes de qualquer nova tentativa.
            case Desconhecida _ -> Acao.RECONCILIAR;
        };
    }
}
