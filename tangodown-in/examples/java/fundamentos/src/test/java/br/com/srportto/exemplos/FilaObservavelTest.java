package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilaObservavelTest {
    @DisplayName("FilaObservavel expõe itens, bytes e capacidade da fila quando há mensagens")
    @Test
    void deveExporMedidasDaFilaLimitada() {
        var implementação = new FilaLimitada<>(10, 1_000, String::length);
        FilaObservavel fila = implementação;
        implementação.oferecer("abcd");
        implementação.oferecer("ef");

        assertEquals(2, fila.tamanho());
        assertEquals(6, fila.bytes());
        assertEquals(10, fila.capacidade());
    }
}
