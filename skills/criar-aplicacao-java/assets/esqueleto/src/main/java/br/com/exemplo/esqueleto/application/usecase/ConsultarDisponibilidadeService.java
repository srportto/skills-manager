package br.com.exemplo.esqueleto.application.usecase;

import br.com.exemplo.esqueleto.domain.model.Disponibilidade;
import br.com.exemplo.esqueleto.domain.port.in.ConsultarDisponibilidadeUseCase;

/** Caso de uso sem anotação de framework; o bean é declarado em {@code infrastructure/config}. */
public class ConsultarDisponibilidadeService implements ConsultarDisponibilidadeUseCase {

    private final String nomeAplicacao;

    public ConsultarDisponibilidadeService(String nomeAplicacao) {
        this.nomeAplicacao = nomeAplicacao;
    }

    @Override
    public Disponibilidade consultar() {
        return Disponibilidade.disponivel(nomeAplicacao);
    }
}
