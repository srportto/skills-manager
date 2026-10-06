package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilaLimitadaTest {
    @Test
    void deveRejeitarExcedenteEAceitarDepoisDeLiberarCapacidade() {
        var fila = new FilaLimitada<String>(1);

        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("pedido-1"));
        assertEquals(FilaLimitada.Admissao.REJEITADO_POR_CAPACIDADE, fila.oferecer("pedido-2"));
        assertEquals("pedido-1", fila.retirar().orElseThrow());
        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("pedido-2"));
    }

    @Test
    void deveLimitarPorItensEPorBytes() {
        var fila = new FilaLimitada<String>(2, 5, String::length);
        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("abc"));
        // Cabe em itens, mas não em bytes (3 + 3 > 5).
        assertEquals(FilaLimitada.Admissao.REJEITADO_POR_CAPACIDADE, fila.oferecer("def"));
        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("de"));
        assertEquals(5, fila.bytes());
        assertEquals("abc", fila.retirar().orElseThrow());
        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("xyz"));
        // Cabe em bytes? 2 + 3 = 5, mas a fila já tem 2 itens.
        assertEquals(FilaLimitada.Admissao.REJEITADO_POR_CAPACIDADE, fila.oferecer("z"));
    }

    @Test
    void deveRecusarCapacidadeInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new FilaLimitada<>(0));
        assertThrows(IllegalArgumentException.class, () -> new FilaLimitada<String>(1, 0, String::length));
    }

    @Test
    void deveRecusarItemComTamanhoInvalido() {
        var fila = new FilaLimitada<String>(2, 10, String::length);
        assertThrows(IllegalArgumentException.class, () -> fila.oferecer(""));
        assertThrows(NullPointerException.class, () -> fila.oferecer(null));
    }
}
