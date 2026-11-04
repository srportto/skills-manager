package br.com.exemplo.esqueleto.domain.model;

/** Modelo puro do domínio: sem Spring, sem Jackson, sem JPA. */
public record Disponibilidade(String aplicacao, String status) {

    public static Disponibilidade disponivel(String aplicacao) {
        return new Disponibilidade(aplicacao, "DISPONIVEL");
    }
}
