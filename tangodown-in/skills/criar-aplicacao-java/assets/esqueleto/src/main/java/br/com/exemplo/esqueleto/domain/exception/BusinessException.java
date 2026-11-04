package br.com.exemplo.esqueleto.domain.exception;

/** Violação de regra de negócio: o chamador pode corrigir (vira 422 na borda web). */
public class BusinessException extends RuntimeException {

    public BusinessException(String mensagem) {
        super(mensagem);
    }
}
