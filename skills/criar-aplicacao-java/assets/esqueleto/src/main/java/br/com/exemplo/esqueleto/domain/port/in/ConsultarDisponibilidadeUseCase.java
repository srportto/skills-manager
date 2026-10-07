package br.com.exemplo.esqueleto.domain.port.in;

import br.com.exemplo.esqueleto.domain.model.Disponibilidade;

/** Porta de entrada (driving port): o adapter web chama isto, nunca o service concreto. */
public interface ConsultarDisponibilidadeUseCase {

    Disponibilidade consultar();
}
