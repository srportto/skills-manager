# Streams com consumer group

Leia este arquivo quando for usar Redis/Valkey Streams como fila de trabalho: leitura por grupo, `XACK`, recuperação de pendências com `XAUTOCLAIM` e retenção.

## Stream com consumer group (ex.: agendador de expirações)

```java
// XADD — adiciona mensagem ao stream
redisTemplate.opsForStream().add(chaveStream, Map.of("id_autorizacao", autorizacaoId.toString()));

// XGROUP CREATE — cria o consumer group (lança BUSYGROUP se já existir: capture e ignore na inicialização)
redisTemplate.opsForStream().createGroup(chaveStream, ReadOffset.from("0"), grupoConsumidor);

// XREADGROUP — lê mensagens pendentes para este consumidor
List<MapRecord<String, Object, Object>> mensagens = redisTemplate.opsForStream().read(
    Consumer.from(grupoConsumidor, nomeConsumidor),
    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
    StreamOffset.create(chaveStream, ReadOffset.lastConsumed())
);

// XACK — confirma DEPOIS do efeito (idempotente); sem XACK a entrada fica pendente
redisTemplate.opsForStream().acknowledge(chaveStream, grupoConsumidor, recordId);

// XPENDING — só INSPECIONA pendências (monitoramento); não recupera nada
PendingMessagesSummary resumo = redisTemplate.opsForStream().pending(chaveStream, grupoConsumidor);
```

Três operações diferentes, frequentemente confundidas:

| Comando | O que faz | Quando |
|---|---|---|
| `XPENDING` | Lista/conta entradas entregues e não confirmadas | Métrica e alerta (pendências e ociosidade) |
| `XAUTOCLAIM` (Redis/Valkey ≥ 6.2) | **Transfere a posse** de pendências ociosas há mais de N ms para outro consumidor e as devolve | Recuperar trabalho de consumidor morto/travado |
| `XACK` | Remove a entrada da lista de pendências | Depois do efeito durável |

`XAUTOCLAIM` não tem atalho dedicado em todas as versões do `StreamOperations`; use o cliente nativo (Lettuce/
Jedis) ou `execute`. Escolha a ociosidade mínima **maior** que o tempo máximo de processamento — senão você
rouba trabalho de um consumidor vivo e processa em dobro. Entradas reivindicadas muitas vezes (contador de
entregas do `XPENDING`) vão para uma quarentena em vez de ciclar para sempre. Defina retenção do stream
(`XADD ... MAXLEN ~ N` ou `XTRIM MINID`) para ele não crescer sem limite.

Exemplo executável (consumidor morre sem `XACK`; outro reivindica após a ociosidade mínima, processa uma vez e
confirma; nada é roubado antes do prazo):
[RecuperacaoPendencias](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/RecuperacaoPendencias.java)
com [RecuperacaoPendenciasExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasExternoIT.java).


## Antes/depois: ack e recuperação

```java
// ANTES: XACK antes do efeito e sem recuperação; consumidor que morre perde o item
for (var registro : mensagens) {
    redisTemplate.opsForStream().acknowledge(chaveStream, grupoConsumidor, registro.getId()); // cedo demais
    processar(registro);
}
```

```java
// DEPOIS: XACK só depois do efeito durável; falha mantém a entrada pendente para XAUTOCLAIM
for (var registro : mensagens) {
    try {
        processar(registro); // idempotente
        redisTemplate.opsForStream().acknowledge(chaveStream, grupoConsumidor, registro.getId());
    } catch (RuntimeException e) {
        log.warn("falha ao processar registro {}", registro.getId(), e); // permanece pendente
    }
}
```

Regra de ack e de DLQ/quarentena para mensageria em geral: `mensageria-sqs-kafka`
([ponto central de decisão](../../mensageria-sqs-kafka/references/erro-central-interceptor.md)).
Prova: [RecuperacaoPendenciasExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasExternoIT.java)
e [RecuperacaoPendenciasTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasTest.java).
