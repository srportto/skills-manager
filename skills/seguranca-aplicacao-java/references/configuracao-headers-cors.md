# A05 — Security Misconfiguration (headers, CORS, error handling)

Leia este arquivo quando configurar headers de segurança (CSP, HSTS, X-Frame-Options), CORS, a `SecurityFilterChain` ou o tratamento de erro exposto ao cliente.

## Headers de segurança

```yaml
# application.yaml — headers minimos via Spring Security
# ATENCAO: o Spring Boot NAO tem essas propriedades `spring.security.headers.*`; o bloco abaixo
# nao produz efeito. Os headers sao configurados na SecurityFilterChain (exemplo "DEPOIS").
spring:
  security:
    headers:
      content-security-policy: "default-src 'self'"
      x-content-type-options: nosniff
      x-frame-options: DENY
      referrer-policy: strict-origin-when-cross-origin
      strict-transport-security: max-age=31536000 ; includeSubDomains
```

```java
// DEPOIS - Spring Security 7 (Boot 4), DSL com lambda; X-Content-Type-Options: nosniff ja e padrao
@Bean
SecurityFilterChain filtros(HttpSecurity http) throws Exception {
    http
        .headers(h -> h
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
            .frameOptions(f -> f.deny())
            .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31_536_000)))   // HSTS so e emitido em requisicao HTTPS
        .cors(Customizer.withDefaults())          // usa o bean CorsConfigurationSource abaixo
        .authorizeHttpRequests(a -> a
            .requestMatchers("/disponibilidade", "/actuator/health/**").permitAll()
            .anyRequest().authenticated());       // negado por padrao
    return http.build();
}
```

Validação: `curl -I https://seu-host/api/...` deve mostrar `Content-Security-Policy`,
`Strict-Transport-Security`, `X-Frame-Options` e `X-Content-Type-Options`.

## CORS

**CORS — não abra `allowedOrigins("*")` com `allowCredentials(true)`:**

```java
// ERRADO - qualquer origem pode fazer requisicao autenticada (CSRF total)
CorsConfiguration cfg = new CorsConfiguration();
cfg.addAllowedOriginPattern("*");
cfg.setAllowCredentials(true);

// CORRETO - allowlist explicita, sem wildcard quando ha credenciais
@Bean
CorsConfigurationSource corsConfig() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("https://app.exemplo.com"));
    cfg.setAllowCredentials(true);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cfg);
    return source;
}
```

## Error handling

**Error handling — não exponha stack trace em produção:**

```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
    include-binding-errors: never
```

Para o corpo de erro padronizado (RFC 9457) sem vazar detalhe interno, ver `api-rest-design`.

```java
// ANTES - devolve a mensagem da excecao (pode conter SQL, caminho de arquivo, nome de tabela)
@ExceptionHandler(Exception.class)
ResponseEntity<String> erro(Exception e) {
    return ResponseEntity.status(500).body(e.toString());
}

// DEPOIS - Problem Details generico; o detalhe fica so no log (com traceId para correlacao)
@ExceptionHandler(Exception.class)
ProblemDetail erro(Exception e) {
    log.error("Erro nao tratado", e);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");
}
```
