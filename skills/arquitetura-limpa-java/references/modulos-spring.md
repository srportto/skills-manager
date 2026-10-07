Leia este arquivo ao escolher módulos Spring (Web vs WebFlux, JPA, Cache, Segurança, Observabilidade), configurar o resource server JWT ou decidir onde mora cada proteção contra sobrecarga numa aplicação Spring em camadas.

# Escolha de stack Spring

| Necessidade | Módulo Spring | Observação |
|---|---|---|
| REST síncrono | `spring-boot-starter-webmvc` (Tomcat) ou `-webflux` (Netty) | Default do Boot 4 é MVC; com virtual threads atende alta concorrência de I/O. Limites de concorrência e filas **não exigem WebFlux** (use admissão/bulkhead). WebFlux quando toda a cadeia é não bloqueante e há streaming com demanda — ver `resiliencia-controle-fluxo-java` |
| Persistência JPA | `spring-boot-starter-data-jpa` | Padrão para PostgreSQL/MySQL. Para queries reativas: `spring-boot-starter-data-r2dbc` |
| Cache | `spring-boot-starter-cache` + provider (Caffeine, Redis) | `@Cacheable` em método de leitura. Cuidado com cache de entidade JPA (lazy) |
| Mensageria | `spring-boot-starter-kafka` | Produtor e consumer. Veja `mensageria-sqs-kafka` |
| Segurança | `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | JWT. Veja `seguranca-aplicacao-java` |
| Validação | `spring-boot-starter-validation` | Bean Validation 3.0 (Jakarta) |
| Observabilidade | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | Veja `monitoramento-java` |

---

# Spring Security — JWT resource server

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/disponibilidade", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

Veja `seguranca-aplicacao-java` para JWT details, CORS, headers, mass assignment.

---

# Features modernas do Java

Veja a skill dedicada `java-moderno` para records, sealed classes, pattern matching, switch
expressions, text blocks, virtual threads, `var`. Pontos mais relevantes em arquitetura:

- **Records** para DTOs e value objects — imutabilidade nativa.
- **Sealed types** para hierarquia de domínio finita (ex.: `Pagamento` com `Pix`/`Cartao`/`Boleto`).
- **Virtual threads** (`spring.threads.virtual.enabled: true`) para cargas I/O-bound com muita
  concorrência. Elas barateiam a espera, mas não aumentam conexões, CPU ou quotas: mantenha pool de
  conexões dentro do orçamento somado das réplicas, timeout de aquisição e admissão limitada.

# Proteção contra sobrecarga na arquitetura interna

Onde cada proteção mora numa aplicação Spring em camadas (detalhes em `resiliencia-controle-fluxo-java`):

| Proteção | Onde | Observação |
|---|---|---|
| Limite de payload e paginação | borda HTTP (controller/config do servidor) | Rejeitar antes de alocar |
| Admissão/load shedding | filtro ou interceptor de entrada | Rejeita com 503 cedo, por requisição lógica |
| Quota por cliente/tenant | filtro de entrada, após autenticação | Identidade confiável; 429 + `Retry-After` |
| Deadline, timeout, retry, breaker, bulkhead | adapter/cliente da dependência | Uma camada dona do retry; timeout no cliente HTTP/JDBC |
| Idempotência de efeito | caso de uso + persistência na mesma transação | Restrição única arbitra corridas |
| Fallback | caso de uso | Semântica de negócio decide se existe alternativa válida |
