---

name: seguranca-aplicacao-java
description: "Guia de segurança de aplicação focado em código Java/Spring Boot (não infra de nuvem nem compliance) — OWASP Top 10 aplicado a Java, hashing de senha, validação de entrada, queries parametrizadas, JWT, headers de segurança, CORS, varredura de dependências vulneráveis. Use ao implementar auth/authz, prevenir OWASP, configurar CORS/CSP, emitir/validar JWT ou auditar dependências. Uso: agents `engenheiro-seguranca`/`java-revisor`/`engenheiro-devops` ou `/seguranca-aplicacao-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.2.0"
  domain: security
  triggers: segurança, OWASP, JWT, bcrypt, SQL injection, XSS, headers de segurança, segredo hardcoded, CVE, CORS
  role: specialist
  scope: application-security
  output-format: code
  related-skills: monitoramento-java, arquitetura-limpa-java, revisao-de-codigo-java
---

# Segurança de Aplicação Java

## Quando usar

Guia de segurança focado em **código de aplicação Java/Spring Boot** (não infraestrutura de nuvem,
redes ou compliance corporativo): OWASP Top 10 aplicado a Java, hashing de senha, validação de
entrada, queries parametrizadas, JWT, headers de segurança, CORS e varredura de dependências.

## Quando NÃO usar

Infraestrutura de nuvem profunda (redes, IAM, KMS) ou compliance corporativo
(SOC2, ISO27001) — use o agent `engenheiro-seguranca`. Para segredo
em log, `monitoramento-java` (`references/logs-estruturados.md`, seção "Regras de ouro") é a fonte.

## Entradas

- Código ou diff a implementar/auditar (controller, service, repository, `SecurityFilterChain`).
- Superfície exposta: endpoints públicos x autenticados, integrações que chamam URL externa.
- Stack: Spring Boot 4 / Spring Security 7 / Java 25.

## Decisão: qual item OWASP → qual reference (guia de references)

| Item OWASP | Reference | Quando ler |
|---|---|---|
| A01 Broken Access Control (+ mass assignment) | `references/controle-acesso.md` | Ownership, IDOR, `@PreAuthorize`, DTO que expõe `role`/`id` |
| A02 Cryptographic Failures (+ segredos) | `references/criptografia-senhas.md` | Hash de senha, criptografia em repouso, onde guardar segredo |
| A03 Injection (+ validação de entrada) | `references/injecao.md` | Query JPQL/SQL/JDBC, `ProcessBuilder`, Bean Validation |
| A04 Insecure Design | `references/design-inseguro.md` | "Esqueci senha", login, enumeração de usuário, IDOR por design |
| A05 Security Misconfiguration | `references/configuracao-headers-cors.md` | Headers (CSP/HSTS), CORS, stack trace em erro |
| API4 Unrestricted Resource Consumption | `references/abuso-recursos-quotas.md` | Limite de payload/página/consulta, quota por cliente, identidade do limitador |
| A07 Authentication Failures (+ JWT) | `references/autenticacao-jwt.md` | Login, brute force, sessão/logout, emitir/validar JWT, resource server |
| A08 Integrity Failures (+ CVEs) | `references/integridade-dependencias.md` | Desserialização, Flyway/`ddl-auto`, Dependency-Check, Dependabot |
| A09 Logging and Monitoring Failures | `references/logs-seguranca.md` | Eventos de auditoria, o que nunca logar, alertas de login |
| A10 SSRF | `references/ssrf.md` | Backend que chama URL informada pelo cliente |

## Passo a passo (workflow de implementação segura)

**Threat model** (superfície de ataque e ameaças: autenticação, validação, exposição de dados,
dependências) → **projete controles** (hash de senha, JWT, validação, headers) → **implemente com
defense in depth** (várias camadas, cada uma com propósito claro) → **valide** (checkpoints
abaixo) → **documente** as decisões de segurança.

- [ ] Mapear a superfície e escolher os itens OWASP aplicáveis (tabela acima).
- [ ] Ler apenas as references dos itens escolhidos e aplicar o padrão "CORRETO".
- [ ] Rodar o checklist por feature (abaixo) e os checkpoints de validação.
- [ ] Rodar a varredura de dependências e registrar as decisões de segurança.

## Saída

Código com os controles aplicados, testes de segurança (tokens de roles distintas, payloads
inválidos rejeitados) e o checklist por feature marcado, citando o item OWASP de cada controle.

## Validação

### Checkpoints de validação

- **Autenticação:** brute-force protection (lockout/rate limit), resistência a session fixation,
  expiração de token, mensagens de credencial inválida (não devem vazar existência de usuário).
- **Autorização:** horizontal e vertical privilege escalation bloqueadas; teste com tokens de
  roles/users diferentes.
- **Validação de entrada:** payloads de SQL injection (`' OR 1=1--`) rejeitados; payloads de XSS
  (`<script>alert(1)</script>`) escapados ou rejeitados.
- **Headers/CORS:** valide com scanner (`curl -I`, Mozilla Observatory) que headers estão
  presentes e que a allowlist de origem CORS está correta.

### Checklist de segurança por feature (use ao implementar)

- [ ] Entrada validada no DTO (`@Valid` + Bean Validation)
- [ ] Query parametrizada (nunca concatenação)
- [ ] Autorização por ownership/role verificada (não só autenticação)
- [ ] Senha hasheada com bcrypt(≥10) ou argon2id
- [ ] JWT com `alg` allowlist, `iss`/`aud` validados, expiração curta
- [ ] Segredo em env/secret manager (nunca em código)
- [ ] Headers de segurança (CSP, HSTS, X-Frame-Options) ativos
- [ ] CORS com allowlist explícita (sem `*` com credenciais)
- [ ] Error response sem stack trace em produção
- [ ] Logs de evento de segurança (sem logar o dado sensível)
- [ ] Rate limit em `/login` e endpoints sensíveis, com identidade confiável (não header livre)
- [ ] Limites de payload, página e custo de consulta; quotas por cliente/tenant onde houver abuso possível
- [ ] Dependências sem CVE crítico/alto
- [ ] Migrations validadas (Flyway/Liquibase), sem `ddl-auto: update` em prod

## Gotchas

- Propriedade `spring.security.headers.*` **não existe** no Spring Boot: headers se configuram na
  `SecurityFilterChain` (`configuracao-headers-cors.md`).
- `csrf.disable()` só vale para API stateless com Bearer token; com cookie de sessão, mantenha CSRF.
- Validação de entrada não substitui query parametrizada nem escape na saída.
- Rate limit de aplicação não substitui proteção volumétrica de borda (WAF/CDN).
- Limites de tráfego, formato de log e MDC têm fonte única em outras skills
  (`resiliencia-controle-fluxo-java`, `monitoramento-java`); as references apontam, não copiam.

# Constraints

Regras inegociáveis — cada uma reforça um item do checklist acima; violá-las é bloqueante em
qualquer revisão de segurança.

## MUST DO
- Hash de senha com bcrypt/argon2id; queries sempre parametrizadas; valide/sanitize toda entrada.
- Rate limiting em endpoints de autenticação; security headers (CSP, HSTS, X-Frame-Options).
- Logue eventos de segurança (failed auth, privilege escalation) sem logar o segredo.
- Segredos em env vars/secret manager; HTTPS obrigatório em produção (HSTS).

## MUST NOT DO
- Senha em plaintext ou encriptada reversivelmente; algoritmo fraco (MD5, SHA-1, DES, ECB).
- Confiar em entrada do usuário sem validação; expor dado sensível em log ou error response.
- Hardcode de segredo/credencial em código; `allowedOrigins("*")` com `allowCredentials(true)`;
  stack trace em error response de produção.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Implementar feature com segurança (auth, validação) | sessão principal | esta skill |
| Auditar segurança completa de um serviço (pré-produção) | agent `engenheiro-seguranca` | esta skill + `monitoramento-java` |
| Configurar Spring Security (filter chain, JWT, CORS) | sessão principal | esta skill |
| Escanear dependências em CI | sessão principal | esta skill |
| Revisão arquitetural completa | agent `java-revisor` (modo `auditoria`) | `revisao-de-codigo-java` + esta skill |
