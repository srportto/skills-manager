# A07 — Identification and Authentication Failures e JWT

Leia este arquivo quando implementar login, proteção contra brute force, sessão/logout, emissão e validação de JWT ou configurar a `SecurityFilterChain` como resource server.

## A07 — Falhas de autenticação

- **Brute force:** rate limit em `/login` por **conta** (ex.: 10 tentativas / 15 min) **e** por origem — só por
  IP falha contra ataques distribuídos e pune usuários atrás de NAT; só por conta permite travar a conta de
  terceiros (aplique atraso progressivo/captcha em vez de bloqueio permanente).
- **Sessão:** JWT de curta duração (15 min access token + refresh token) ou session cookie
  `httpOnly; secure; sameSite=strict`.
- **Logout:** invalidar refresh token em servidor (não só no client) para impedir reuso de token
  roubado.

## JWT — emissão e validação

```java
// Emissao
String token = Jwts.builder()
        .subject(user.getId().toString())
        .claim("role", user.getRole())
        .issuer("sua-app")
        .audience().add("sua-app").and()
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000))   // 15 min
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
        .compact();

// Validacao (em filtro do Spring Security)
Jws<Claims> parsed = Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .requireIssuer("sua-app")
        .requireAudience("sua-app")
        .build()
        .parseSignedClaims(token);
```

Para HS256 o segredo deve ter **≥ 256 bits (32 bytes)**; o jjwt rejeita chave menor. Prefira chave assimétrica (RS256/ES256) quando outros serviços só validam.

**Checklist JWT:**
- `algorithm` allowlist explícito (rejeitar `none` e algoritmos fracos).
- `issuer` e `audience` validados.
- `expiration` curta (≤ 15 min para access token).
- Refresh token: persistido em servidor (banco) com revogação.
- Secret em variável de ambiente / vault, **nunca** em código-fonte ou `application.yml` versionado.

## Validação sem filtro próprio: resource server do Spring Security 7

Em vez de escrever o filtro de validação à mão, deixe o Spring Security validar assinatura,
`exp`, `iss` e `aud`. Chaves assimétricas (RS256/ES256 via JWKS) evitam distribuir o segredo de
assinatura para quem só precisa **validar**.

```java
// ANTES - filtro manual: facil esquecer iss/aud, aceitar alg errado ou engolir excecao
public class JwtFiltro extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = req.getHeader("Authorization").substring(7);   // NPE se header ausente
        Claims c = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
        // ...sem requireIssuer/requireAudience
        chain.doFilter(req, res);
    }
}

// DEPOIS - resource server: DSL com lambda, API stateless, decoder com algoritmo fixo + iss + aud
@Configuration
@EnableMethodSecurity
class SegurancaConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // API stateless com Bearer token (sem cookie de sessao)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/disponibilidade", "/actuator/health/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${seguranca.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)      // allowlist: so RS256, nunca "none"
                .build();

        OAuth2TokenValidator<Jwt> audiencia = jwt -> jwt.getAudience().contains("sua-app")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "audience invalida", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://auth.exemplo.com"),   // exp, nbf, iss
                audiencia));
        return decoder;
    }
}
```

Por que `csrf.disable()` aqui: CSRF explora credenciais enviadas **automaticamente** pelo
navegador (cookie). Com `Authorization: Bearer` enviado explicitamente pelo client, o vetor não
existe — mas se a API usar cookie de sessão, **mantenha** o CSRF ligado.

Para headers de segurança e CORS na mesma chain, ver `configuracao-headers-cors.md`.
