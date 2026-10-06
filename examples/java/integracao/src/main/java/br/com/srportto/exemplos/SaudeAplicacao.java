package br.com.srportto.exemplos;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contribuidores de saúde com semântica explícita (os grupos são definidos em configuração):
 * <ul>
 *   <li>liveness = só o estado do processo; nenhum serviço externo.</li>
 *   <li>readiness = estado + dependências <b>necessárias</b> para atender (ex.: banco).</li>
 *   <li>operacional = sinais de saturação (backlog) para alerta; não tira réplicas da rotação, porque
 *       remover réplicas saturadas concentraria a carga nas restantes.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SaudeAplicacao {
    static final double LIMIAR_OCUPACAO = 0.9;

    /** Exposto como contribuidor "backlog" (o sufixo HealthIndicator é removido do nome). */
    @Bean
    HealthIndicator backlogHealthIndicator(ObjectProvider<FilaLimitada<?>> filas) {
        return () -> {
            FilaLimitada<?> fila = filas.getIfUnique();
            if (fila == null) return Health.up().withDetail("fila", "não configurada").build();
            int itens = fila.tamanho();
            int capacidade = fila.capacidade();
            var saude = (double) itens / capacidade >= LIMIAR_OCUPACAO ? Health.down() : Health.up();
            return saude.withDetail("itens", itens).withDetail("capacidade", capacidade).build();
        };
    }
}
