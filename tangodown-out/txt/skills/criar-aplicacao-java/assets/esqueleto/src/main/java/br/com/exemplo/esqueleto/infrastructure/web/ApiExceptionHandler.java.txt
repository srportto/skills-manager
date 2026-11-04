package br.com.exemplo.esqueleto.infrastructure.web;

import br.com.exemplo.esqueleto.domain.exception.ApplicationException;
import br.com.exemplo.esqueleto.domain.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Ponto central de erro HTTP (RFC 9457 Problem Details). Estender {@link ResponseEntityExceptionHandler} já
 * cobre validação de bean (400) e demais erros do Spring MVC no mesmo formato.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail negocio(BusinessException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, erro.getMessage());
    }

    @ExceptionHandler(CorpoExcedidoException.class)
    public ProblemDetail corpoExcedido(CorpoExcedidoException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, erro.getMessage());
    }

    /**
     * O limite pode estourar no meio de um valor JSON; o Jackson embrulha a exceção da leitura e o Spring a entrega
     * como corpo ilegível (400). Se a causa for o limite do corpo, a resposta correta é 413.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException erro,
            HttpHeaders headers, HttpStatusCode status, WebRequest requisicao) {
        for (Throwable causa = erro.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof CorpoExcedidoException excedido) {
                return handleExceptionInternal(erro, corpoExcedido(excedido), headers, HttpStatus.CONTENT_TOO_LARGE,
                        requisicao);
            }
        }
        return super.handleHttpMessageNotReadable(erro, headers, status, requisicao);
    }

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail aplicacao(ApplicationException erro) {
        // detalhe genérico de propósito: a causa vai para o log, nunca para o cliente
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");
    }
}
