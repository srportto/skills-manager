package br.com.exemplo.esqueleto.infrastructure.config;

import br.com.exemplo.esqueleto.application.usecase.ConsultarDisponibilidadeService;
import br.com.exemplo.esqueleto.domain.port.in.ConsultarDisponibilidadeUseCase;
import br.com.exemplo.esqueleto.infrastructure.web.LimiteCorpoRequisicaoFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** Composição: liga os casos de uso (application) às portas; é a camada que conhece o Spring. */
@Configuration
public class BeansConfig {

    @Bean
    ConsultarDisponibilidadeUseCase consultarDisponibilidadeUseCase(
            @Value("${spring.application.name}") String nomeAplicacao) {
        return new ConsultarDisponibilidadeService(nomeAplicacao);
    }

    /** Limite do corpo de toda requisição (JSON incluso): acima dele, 413 antes do controller. */
    @Bean
    LimiteCorpoRequisicaoFilter limiteCorpoRequisicaoFilter(@Value("${app.http.limite-corpo}") DataSize limite) {
        return new LimiteCorpoRequisicaoFilter(limite);
    }
}
