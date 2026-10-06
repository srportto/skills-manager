# Perfil de projeto — exemplo: monorepo de autorizações de pagamento recorrente

Exemplo **ilustrativo** de perfil de projeto, preservado do monorepo em que esta skill nasceu. Os serviços,
convenções e arquivos abaixo **não existem neste repositório**. Use como modelo do nível de detalhe que um
perfil precisa ter; para um projeto real, monte o perfil a partir do contexto recebido (`CLAUDE.md`,
`AGENTS.md`, docs de arquitetura, código).

## Serviços e pergunta obrigatória

| Serviço | O que ele impõe à história | Pergunta obrigatória |
|---|---|---|
| **contratocommand** (8080) — escrita | Convenção 422, grafo de transições, evento SNS pós-commit, partição de expurgo, lock otimista | Qual transição de status (de → para)? Qual `motivo_status`? Publica evento — com qual `tipoEvento`? O que acontece se a chamada chegar duas vezes? |
| **contratoquery** (8081) — leitura | Somente leitura (`DB_READ_ONLY=true`), cascata de partições, `status` exposto como `String`, custo linear em consulta sem poda de partição | A leitura precisa encontrar autorização já expurgada? O campo novo foi refletido nos **três** pontos (entidade JPA + `domain/model` + mapper)? Qual o volume esperado? |
| **autorizacaostatus-producer** (8082) — ponte SQS→Kafka | Sem banco. Converte JSON → Avro | O payload JSON mudou? O `.avsc` mudou? Os espelhos foram replicados? Precisa de DLQ/retry novo? |
| **eventos-consumer** (8083) — consome Avro | Sem banco. Retry é do spring-kafka (`DefaultErrorHandler`). Deriva o tipo do evento do **corpo** | O consumer precisa do campo novo? O `.avsc` local foi replicado? O campo é nullable? |
| **temporiza-autorizacao** (8084) | Agenda/expira via Valkey; usa só id + data de inclusão; chamador at-least-once do `/decisao` | O prazo muda? Depende de campo além de id/data? O efeito é seguro se o disparo repetir? |
| **expurgo-particao** (função fora da trilha Java) | Fórmula de partição espelhada; `TRUNCATE` só sobre dado do ciclo anterior | A fórmula ou a retenção mudam? O espelho foi replicado? |

## Convenção HTTP do projeto

Entrada inválida do cliente → **422**, tanto falha de formato (`@Valid`) quanto regra de negócio; a distinção
vem do **shape do corpo** (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`). Concorrência →
**409**. Erro técnico → **500**. Rotas de escrita **não têm 404** (inexistente é 422); `GET /{id}` de leitura
tem 404 — os dois convivem por design. Campo numérico novo precisa de faixa (`@Max`) para evitar narrowing
silencioso para `short`.

## Máquina de estados

`RECEBIDA` → {`PENDENTE_ACEITE`, `EM_PROCESSO_ATIVACAO`, `REJEITADA`}; `ATIVA` → {`CANCELADA`, `FINALIZADA`,
`REJEITADA`}; terminais não saem. **Alcançabilidade no grafo não é idempotência**: `ATIVA → REJEITADA` existe
para outro fluxo, então a expiração exige `statusAtual == RECEBIDA` explicitamente.

## Idempotência e concorrência

SNS/SQS são at-least-once; o temporizador chama `/decisao` sem conhecer o estado. A segunda chamada devolve
**422 identificando o status atual** (o chamador distingue "já resolvida" de falha). `@Version` →
`ObjectOptimisticLockingFailureException` → 409; movimentação entre partições gera
`CannotAcquireLockException`, também 409.

## Persistência

Coluna nova no `contratoquery` exige três edições (entidade, modelo, mapper) e nenhuma quebra a compilação.
Tabela particionada (faixa 900–999 do expurgo): `CREATE INDEX CONCURRENTLY` não funciona na tabela-pai
particionada. `@Column(nullable = false)` não impõe nada com `ddl-auto: none`. Consulta sem poda por chave
de partição custa linear no número de partições.

## Espelhos manuais

| Se a história muda... | Replique em |
|---|---|
| `AutorizacaoEventoPayload` (JSON) | `contratocommand` **e** `autorizacaostatus-producer`; conferir o subconjunto do temporizador |
| `EventoAutorizacao.avsc` (Avro) | `autorizacaostatus-producer` **e** `eventos-consumer` |
| enums `StatusAutorizacao` / `TipoEventoAutorizacao` | `contratocommand` **e** `eventos-consumer` |
| Fórmula de partição de expurgo | serviço Java **e** a função de expurgo |
| Coluna de `autorizacoes` | `contratocommand` **e** `contratoquery` (três pontos) |

## Critérios observáveis no dialeto do projeto

| Vago | Observável |
|---|---|
| "a autorização é cancelada" | "a linha persistida tem `status` = `CANCELADA` (código 5) **e** `motivo_status` = `<valor>`" |
| "retorna erro" | "responde 422 com `LayoutErrosApiResponse` identificando o status atual, **e** nada é persistido" |
| "o evento é publicado" | "**exatamente um** evento em `sns-estados-autorizacao` com `tipoEvento` = `ATIVACAO`" |

## Anti-padrão real: silêncio sobre a chamada repetida

```
Quando o prazo da jornada 1 expira, a autorização é rejeitada.
```

Quem dispara é um chamador at-least-once que não conhece o estado. Sem dizer o que acontece se a expiração
chegar **depois** da aprovação, o dev escolhe na hora. Versão refinada:

```
Cenário: expiração chega depois da aprovação
  Dado que a autorização já está em ATIVA
  Quando a expiração é processada
  Então a resposta é 422 identificando o status atual
  E a linha permanece em ATIVA
  E nenhum evento é publicado
```

## Ponte para OpenSpec neste projeto

Histórias viravam changes em `openspec/changes/<change>/`: cada regra vira `### Requirement:` com `SHALL` e
cada cenário um `#### Scenario:` com `WHEN`/`THEN`/`AND`.
