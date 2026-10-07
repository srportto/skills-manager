# Logs estruturados: regras de ouro, JSON e níveis

Leia este arquivo ao adicionar ou revisar logs em Java/Spring Boot: regras de ouro (SLF4J, sem dado sensível), formato JSON estruturado e critério objetivo de nível (ERROR/WARN/INFO/DEBUG). Para correlação por request leia `logs-mdc-correlacao.md`; para o que logar em cada camada, `logs-por-camada.md`.

## 1. Regras de ouro

- **SLF4J sempre, nunca `System.out`/`System.err`** — `System.out.println` não tem nível, não vai para
  o pipeline de log estruturado e não aparece em nenhuma ferramenta de observabilidade.
- **Placeholders `{}`, nunca concatenação** — `log.info("pedido {}", id)`, nunca
  `log.info("pedido " + id)`. Concatenação roda sempre, mesmo com o nível desligado; placeholder só
  monta a string se o nível estiver habilitado.
- **Logger `private static final`** — uma instância por classe, nomeada `log`, criada com
  `LoggerFactory.getLogger(NomeDaClasse.class)`.
- **NUNCA logar (em nenhum nível, nem `debug`):**
  - senha, hash de senha, PIN;
  - token de autenticação, JWT, API key, secret, chave de assinatura;
  - CPF, CNPJ, número de cartão de crédito, CVV;
  - dado pessoal completo que identifique alguém sem necessidade (nome completo + endereço + telefone
    juntos, por exemplo) — prefira um id de negócio (`pedidoId`, `usuarioId`);
  - corpo bruto de payload de terceiro sem mascarar, quando esse payload pode conter qualquer um dos
    itens acima.

```java
// ERRADO - System.out, concatenacao, sem placeholder, senha no log
System.out.println("Login do usuario " + usuario.email() + " com senha " + usuario.senha());

// CORRETO - SLF4J, placeholder, logger private static final, sem dado sensivel
private static final Logger log = LoggerFactory.getLogger(LoginService.class);
// ...
log.info("login realizado usuarioId={}", usuario.id());
```

## 2. JSON estruturado por padrão

Este projeto já sai com log estruturado em JSON habilitado, para todo ambiente (inclusive `local`) —
não é preciso adicionar nenhuma dependência, é suporte nativo do Spring Boot (3.4+; este projeto usa
Boot 4). Toda aplicação nova gerada por `criar-aplicacao-java` deve nascer com este `application.yaml`:

```yaml
logging:
    structured:
        format:
            console: logstash   # logs JSON estruturados (padrao do catalogo, ver logs-estruturados.md)
```

### Por que JSON em vez de texto

```
# Texto - uma ferramenta (ou uma IA) precisa "interpretar" a string
2026-08-01 10:15:30 INFO ProcessarPedidoService - pedido processado id=abc-123 valor=99.90

# JSON - campos acessiveis diretamente, sem parsing ad-hoc
{"@timestamp":"2026-08-01T10:15:30.123-03:00","level":"INFO","logger_name":"br.com.srportto.appbase.application.pedido.ProcessarPedidoService","message":"pedido processado id=abc-123 valor=99.90","traceId":"9f1c3e2a-6b7d-4e11-9a2f-1234567890ab"}
```

| Aspecto | Texto | JSON |
|---|---|---|
| Parsing | Regex / interpretação da string | Acesso direto ao campo (`jq`, query de log) |
| Uso de tokens (análise por IA) | Maior — padrão repetido em texto livre precisa ser reinterpretado a cada linha | Menor — estrutura já separa o que é campo do que é texto |
| Extração de erro | Recortar a stack trace do meio do texto | Campo `stack_trace`/`exception` isolado |
| Filtragem / correlação | `grep` por substring, frágil a mudança de formato | `jq 'select(.traceId == "...")'`, robusto |
| Agregação (contagem de erros, latência) | Precisa de parser customizado | Direto por ferramenta de log (campo `level`, `duration_ms`) |

**Importante — o que realmente vira campo JSON:** os placeholders `{}` viram parte do texto do campo
`message`, não campos JSON separados (isso exigiria `logstash-logback-encoder` com
`StructuredArguments.kv()`, que este projeto não usa). Quem vira campo JSON de verdade,
automaticamente, é **tudo que está no MDC** (seção 4) — por isso um id que você precisa
filtrar/agrupar entre linhas de log (`traceId`) deve ir para o MDC, não só para o texto da mensagem.

Para um campo estruturado pontual sem passar pelo MDC, o SLF4J 2.x (já usado pelo Spring Boot 4) tem
uma API fluente sem dependência extra — use só quando o campo separado importa de verdade
(dashboard/query); no caso comum, `log.info("... id={} valor={}", id, valor)` já é suficiente:

```java
// campo extra como chave/valor real no JSON, sem precisar de biblioteca adicional
log.atInfo().setMessage("pedido processado")
        .addKeyValue("pedidoId", pedido.id())
        .addKeyValue("valor", pedido.valor())
        .log();
```

### Como ler os logs como humano em dev

O dia a dia mais simples é filtrar o JSON com `jq`, sem mexer em nenhuma configuração:

```bash
mvn spring-boot:run | jq .
# ou, olhando so os erros de um traceId especifico
tail -f app.log | jq 'select(.traceId == "9f1c3e2a-6b7d-4e11-9a2f-1234567890ab")'
```

A propriedade `logging.structured.format.console` hoje **não tem** forma documentada de ser
desligada por profile (limitação conhecida e em aberto do Spring Boot — issue
[#45407](https://github.com/spring-projects/spring-boot/issues/45407)). Se precisar mesmo de texto
legível por profile, a forma suportada é um `logback-spring.xml` próprio com `<springProfile
name="log-humano">` trocando o appender `CONSOLE` para um `ConsoleAppender` com `${CONSOLE_LOG_PATTERN}`,
e `<springProfile name="!log-humano">` incluindo `structured-console-appender.xml` (o JSON padrão) —
ativado com `mvn spring-boot:run -Dspring-boot.run.profiles=local,log-humano`.

## 3. Níveis

| Nível | Critério objetivo | Exemplo | Config por ambiente |
|---|---|---|---|
| `ERROR` | Algo quebrou e exige ação/investigação humana; a operação não foi concluída | `log.error("Falha ao salvar pedido {}", pedido.id(), e)` no handler central | Sempre habilitado, em todo ambiente |
| `WARN` | Situação anormal, mas recuperável — não interrompe o fluxo | `log.warn("Retry {} de {} ao chamar integracao", tentativa, maxTentativas)` | Sempre habilitado, em todo ambiente |
| `INFO` | Evento de negócio relevante, marco do fluxo — o que aconteceu, não como | `log.info("pedido processado id={} valor={}", pedido.id(), pedido.valor())` (`ProcessarPedidoService`) | `root: INFO` em todo ambiente |
| `DEBUG` | Detalhe técnico útil só durante investigação — verboso, granular | `log.debug("payload bruto recebido: {}", mensagemJson)` | Pacote da aplicação (`br.com.srportto.appbase`) em `DEBUG` **só** no profile `local`; nunca em produção |

Config de exemplo (documento YAML separado por `---`, seguindo a convenção de profile do
`application.yaml` do app-base):

```yaml
logging:
    level:
        root: INFO
---
spring:
    config:
        activate:
            on-profile: local
logging:
    level:
        br.com.srportto.appbase: DEBUG   # verboso so em local; nunca habilitar em hml/prod
```


## Logs numa stack de observabilidade

Em uma stack de observabilidade o que muda é **para onde os logs vão**:
`logging.structured.format.console: logstash` (ou `ecs` para Elastic
Common Schema) já produz 1 linha JSON por evento com MDC `traceId` automático:
`{"timestamp":"...","level":"INFO","logger":"...","message":"Order created","traceId":"trace-xyz",
"orderId":12345,"duration_ms":45}`.

**Não confunda "campo no JSON" com "placeholder no log".** O que vira campo JSON de verdade é o
que está no MDC (ver `logs-mdc-correlacao.md`) — placeholders `{}` viram texto do campo
`message`, não campos separados.
