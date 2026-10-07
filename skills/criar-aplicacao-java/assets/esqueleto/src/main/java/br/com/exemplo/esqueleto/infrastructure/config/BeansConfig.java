package br.com.exemplo.esqueleto.infrastructure.config;

import br.com.exemplo.esqueleto.application.usecase.ConsultarDisponibilidadeService;
import br.com.exemplo.esqueleto.domain.port.in.ConsultarDisponibilidadeUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composição: liga os casos de uso (application) às portas; é a camada que conhece o Spring. */
@Configuration
public class BeansConfig {

    @Bean
    ConsultarDisponibilidadeUseCase consultarDisponibilidadeUseCase(
            @Value("${spring.application.name}") String nomeAplicacao) {
        return new ConsultarDisponibilidadeService(nomeAplicacao);
    }
}
