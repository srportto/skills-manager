package br.com.exemplo.esqueleto.infrastructure.web;

/** Corpo sem Content-Length passou do limite durante a leitura; vira 413 no {@link ApiExceptionHandler}. */
public class CorpoExcedidoException extends RuntimeException {

    public CorpoExcedidoException(long limiteBytes) {
        super("Corpo da requisição acima de " + limiteBytes + " bytes");
    }
}
