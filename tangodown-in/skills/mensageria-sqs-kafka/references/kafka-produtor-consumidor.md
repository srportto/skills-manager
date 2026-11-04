# Kafka: produtor e consumidor

Leia este arquivo quando for configurar produtor (chave, `acks`, idempotência), consumer group, commit de offset, `max.poll.interval.ms`, pause/resume ou rebalance.

## Kafka produtor

- **Chave define partição e ordem**: mesma chave → mesma partição → ordem garantida entre elas. Use id de negócio
  estável (id do pedido/agregado). Chave muito concentrada vira **hot partition**.
- **`acks=all`** aguarda as réplicas **em sincronia (ISR)**, não "todas as réplicas". A durabilidade real depende de
  `min.insync.replicas` no tópico/broker: com `replication.factor=3` e `min.insync.replicas=2`, a escrita só é
  confirmada com ao menos 2 cópias; com `min.insync.replicas=1`, `acks=all` ainda pode perder dado se o líder cair.
- **Produtor idempotente** (`enable.idempotence=true`, default nos clientes atuais com `acks=all`) evita duplicatas
  causadas pelos **retries internos do próprio produtor**; não evita duplicatas de reenvio pela aplicação.
- **Retries do produtor** são automáticos até `delivery.timeout.ms`; não acrescente retry de aplicação em cima sem
  orçamento — e, se acrescentar, o consumidor precisa deduplicar.
- **Dependência:** `spring-boot-starter-kafka` (autoconfigura `KafkaTemplate`); sem o starter falta o bean.

```yaml
spring:
  kafka:
    producer:
      acks: all
      properties:
        enable.idempotence: true
        delivery.timeout.ms: 120000
```

Publicar **depois** do commit do banco perde o evento se o processo cair entre os dois; publicar **antes** cria
evento de algo que pode sofrer rollback. Use **outbox** (ver `references/idempotencia-outbox-replay-java.md`).

## Kafka consumidor

- **Consumer group:** cada partição tem no máximo um consumidor ativo do grupo; paralelismo máximo = número de
  partições. Mais instâncias que partições ficam ociosas.
- **`auto.offset.reset`** só vale quando **não há offset commitado válido** para o grupo (grupo novo ou offset
  expirado/fora da retenção): `earliest` lê desde o início disponível, `latest` só o que chegar. Não é "configuração
  de leitura" do dia a dia.
- **Commit do concluído:** commite o offset `último processado + 1` **depois** do efeito durável. Com
  `@KafkaListener` e processamento síncrono, o `AckMode` padrão (`BATCH`) commita após o retorno do listener —
  correto. Se você entregar o registro a outra thread e retornar, o commit automático confirma trabalho **não
  feito**: use ack manual.
- **Manter o poll vivo:** o tempo entre `poll()` não pode passar de `max.poll.interval.ms` (default 5 min), senão o
  membro sai do grupo e as partições são reatribuídas (com reprocessamento). Processamento lento: reduza
  `max.poll.records`, ou processe de forma assíncrona **pausando** as partições ocupadas (`pause`/`resume`) e
  continuando a chamar `poll()`. **Nunca "pare o poll" até o trabalho acabar.**
- **`KafkaConsumer` não é thread-safe:** só a thread do laço chama `poll`, `commit`, `pause`; workers devolvem
  resultados por fila.
- **Ordem e paralelismo:** paralelizar **dentro** de uma partição quebra a ordem por chave. Paralelize entre
  partições, ou por chave com cuidado explícito.
- **Rebalance:** em `onPartitionsRevoked`, espere (limitado) o trabalho em andamento e commite o concluído; o resto
  será reprocessado pelo novo dono — por isso o efeito precisa ser idempotente.

Detalhes, números e o exemplo completo:
[controle de consumo](controle-consumo-java.md).


## Antes/depois: processamento assíncrono com commit automático

```java
// ANTES: entrega o registro a outra thread e retorna; o AckMode padrão commita trabalho ainda não feito
@KafkaListener(topics = "pedidos")
void ouvir(ConsumerRecord<String, String> registro) {
    executor.submit(() -> service.processar(registro.value())); // se a JVM cair, o offset já avançou
}
```

```java
// DEPOIS: ack manual só depois do efeito durável; poll segue vivo (pause/resume cuida do ritmo)
// spring.kafka.listener.ack-mode: manual
@KafkaListener(topics = "pedidos")
void ouvir(ConsumerRecord<String, String> registro, Acknowledgment ack) {
    service.processar(registro.value()); // idempotente; síncrono dentro de max.poll.interval.ms
    ack.acknowledge();                   // commita o offset seguinte ao processado
}
```

Para trabalho realmente assíncrono ou lento, o padrão pausa-por-partição está em
[controle de consumo](controle-consumo-java.md). Provas:
[ConsumidorKafkaLimitadoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoTest.java)
e [ConsumidorKafkaLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoExternoIT.java).
