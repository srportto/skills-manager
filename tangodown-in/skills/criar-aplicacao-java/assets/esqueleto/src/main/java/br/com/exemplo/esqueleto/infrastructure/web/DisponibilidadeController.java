package br.com.exemplo.esqueleto.infrastructure.web;

import br.com.exemplo.esqueleto.domain.model.Disponibilidade;
import br.com.exemplo.esqueleto.domain.port.in.ConsultarDisponibilidadeUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adapter de entrada: smoke test funcional. NÃO é probe — as probes são /actuator/health/liveness|readiness. */
@RestController
public class DisponibilidadeController {

    private final ConsultarDisponibilidadeUseCase useCase;

    public DisponibilidadeController(ConsultarDisponibilidadeUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/disponibilidade")
    public Disponibilidade disponibilidade() {
        return useCase.consultar();
    }
}
