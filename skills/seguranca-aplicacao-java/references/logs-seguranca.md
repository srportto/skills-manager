# A09 — Security Logging and Monitoring Failures

Leia este arquivo quando decidir quais eventos de segurança registrar, o que nunca pode aparecer em log (segredo, token, PII) e quando alertar. Formato JSON e correlação são regras de outra skill (ver abaixo).

## Fonte única para formato e correlação

Esta reference trata só do que é **específico de segurança**. Formato estruturado, níveis,
campos e MDC vivem em `monitoramento-java` — não duplique aqui:

- Formato JSON, níveis e "Regras de ouro" (inclui segredo em log): [`logs-estruturados.md`](../../monitoramento-java/references/logs-estruturados.md).
- `traceId`/MDC e propagação entre threads: [`logs-mdc-correlacao.md`](../../monitoramento-java/references/logs-mdc-correlacao.md).

## Regras de A09

- **Logar** falhas de autenticação, tentativas de privilege escalation, falhas de autorização,
  rate limit triggers — **sem** logar o segredo que falhou (ver `monitoramento-java/references/logs-estruturados.md`).
- **Alertar** quando há pico de falhas de login (possível credential stuffing).

## O que nunca logar

| Nunca registrar | Registrar no lugar |
|---|---|
| Senha (certa ou errada), token (Bearer, refresh, reset), chave de API, segredo | Resultado (`falha`), motivo categórico (`credencial_invalida`), `userId`/hash do e-mail |
| Número de cartão, CPF completo, e-mail completo em evento de erro | Valor mascarado (`***.***.***-12`, últimos 4 dígitos) |
| Corpo inteiro da request/response em endpoint de auth | Método, rota, status, `traceId` |

## Exemplo antes/depois — evento de falha de login

```java
// ANTES - loga a senha tentada e o e-mail completo; sem estrutura para alertar
log.warn("Falha de login para " + email + " com senha " + senha);

// DEPOIS - evento de auditoria estruturado: sem segredo, PII mascarado, campos fixos para alerta
// (traceId entra automaticamente via MDC; ver logs-mdc-correlacao.md)
log.atWarn()
   .addKeyValue("evento", "auth.login.falha")
   .addKeyValue("motivo", "credencial_invalida")
   .addKeyValue("usuario", mascararEmail(email))      // j***@exemplo.com
   .addKeyValue("origem", ipConfiavel)                // ver abuso-recursos-quotas.md
   .log("Falha de autenticacao");

static String mascararEmail(String email) {
    int arroba = email.indexOf('@');
    if (arroba <= 1) return "***";
    return email.charAt(0) + "***" + email.substring(arroba);
}
```

Eventos mínimos de auditoria: login sucesso/falha, logout, troca/reset de senha, mudança de
role/permissão, acesso negado (403), rate limit disparado (429), uso de refresh token revogado.
Cada um com `evento`, `resultado`, identidade (sem segredo) e `traceId`.

Alerta sugerido: taxa de `auth.login.falha` por origem e no total acima do baseline numa janela
curta (regras de alerta: `monitoramento-java/references/alertas-dashboards-probes.md`).
