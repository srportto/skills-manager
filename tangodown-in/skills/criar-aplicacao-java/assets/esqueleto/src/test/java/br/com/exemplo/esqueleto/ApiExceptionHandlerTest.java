package br.com.exemplo.esqueleto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.com.exemplo.esqueleto.domain.exception.ApplicationException;
import br.com.exemplo.esqueleto.domain.exception.BusinessException;
import br.com.exemplo.esqueleto.infrastructure.web.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contrato de erro: BusinessException vira 422; ApplicationException vira 500 sem vazar a causa. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("BusinessException mapeia para 422 com a mensagem de negócio")
    void negocio() {
        var problema = handler.negocio(new BusinessException("saldo insuficiente"));
        assertEquals(422, problema.getStatus());
        assertEquals("saldo insuficiente", problema.getDetail());
    }

    @Test
    @DisplayName("ApplicationException mapeia para 500 sem expor a causa")
    void aplicacao() {
        var problema = handler
                .aplicacao(new ApplicationException("falha ao gravar", new IllegalStateException("segredo")));
        assertEquals(500, problema.getStatus());
        assertFalse(problema.getDetail().contains("segredo"));
    }
}
