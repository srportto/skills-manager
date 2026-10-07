package br.com.exemplo.esqueleto.domain.exception;

/** Falha inesperada da aplicação: o chamador não tem como corrigir (vira 500 na borda web). */
public class ApplicationException extends RuntimeException {

    public ApplicationException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
