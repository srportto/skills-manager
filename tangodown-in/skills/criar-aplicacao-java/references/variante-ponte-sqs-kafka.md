# Variantes: sqs-para-kafka e rest-para-kafka (publicação em Kafka)

Leia este arquivo quando a aplicação **publica no Kafka**: seja como **ponte** (consome SQS e republica no Kafka)
ou a partir de um **endpoint REST** (`POST /eventos`). Em ambas, o produtor é um adapter de saída atrás de uma
`port/out`. Consumo SQS: [variante-sqs-listener](variante-sqs-listener.md); produtor/consumidor Kafka:
[mensageria-sqs-kafka](../../mensageria-sqs-kafka/SKILL.md).

## O que adicionar sobre `assets/esqueleto`

**Dependências:** as de [variante-sqs-listener](variante-sqs-listener.md) (só na ponte) e o starter Kafka
(`spring-boot-starter-kafka` — confirme o nome no BOM, ver [variante-kafka-consumer](variante-kafka-consumer.md)).

**Pacotes novos:** `domain/port/out/Publicar<Evento>Port`, `infrastructure/messaging/Kafka<Evento>Publisher`
(implementa a porta), `infrastructure/config/KafkaProducerConfig`. Na ponte, o listener SQS chama um `port/in`
cujo caso de uso chama a `port/out` — o listener **nunca** chama o producer direto. Em `rest-para-kafka`, o
controller (`POST /eventos`) chama o `port/in`.

**`application.yml`** (acrescente):

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP}
    producer:
      acks: all                          # confirmação de todas as réplicas in-sync
      properties:
        enable.idempotence: true         # sem duplicata por retry interno do producer
        delivery.timeout.ms: 5000        # deadline total da publicação
        request.timeout.ms: 2000
```

## Componentes (da definição da skill)

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **sqs-para-kafka** | Ponte: consome SQS (interceptor + DLQ, como acima) e republica no Kafka através de uma `port/out` implementada por um producer em `infrastructure/messaging/`. | `mensageria-sqs-kafka` |
| **rest-para-kafka** | Endpoint REST (`POST /eventos`) que chama um use case, o qual publica pela `port/out` implementada em `infrastructure/messaging/`. | `mensageria-sqs-kafka` (`kafka-produtor-consumidor.md`) |

## Proteções e provas

| Variante | Proteções obrigatórias | Prova mínima gerada |
|---|---|---|
| sqs-para-kafka | Tudo de SQS + producer `acks=all`/idempotente com timeout; delete SQS só após confirmação do Kafka | Falha do Kafka não apaga a mensagem SQS |
| rest-para-kafka | Deadline na publicação; 503 quando o broker não confirma; outbox se houver escrita em banco no mesmo fluxo | Broker fora → 503 sem evento fantasma |

## Antes / depois: confirmar o Kafka antes de apagar a mensagem SQS

```java
// ANTES: publica sem esperar a confirmação e apaga a mensagem SQS; broker fora = evento perdido
void ponte(Message msg) {
    kafka.send("eventos", msg.body());                 // future ignorado
    sqs.deleteMessage(b -> b.queueUrl(fila).receiptHandle(msg.receiptHandle()));
}
```

```java
// DEPOIS: o caso de uso publica pela porta e só a confirmação (com deadline) libera o delete
// application/usecase
public void encaminhar(Evento evento) {
    publicarPort.publicar(evento);                     // lança ApplicationException se o broker não confirmar
}

// infrastructure/messaging
@Override
public void publicar(Evento evento) {
    try {
        kafka.send("eventos", evento.chave(), evento.payload()).get(3, TimeUnit.SECONDS);   // espera o ack
    } catch (Exception e) {
        throw new ApplicationException("Kafka não confirmou a publicação", e);              // delete SQS NÃO acontece
    }
}
```

Em `rest-para-kafka`, a mesma exceção vira **503** na borda (`ApiExceptionHandler`), sem evento fantasma. Se o
mesmo fluxo grava no banco **e** publica, use outbox (publicar direto dos dois lados não é atômico).

## Fontes únicas e exemplos executáveis

- Outbox, idempotência e replay: [idempotencia-outbox-replay-java](../../mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md);
  produtor (`acks`, idempotência): [kafka-produtor-consumidor](../../mensageria-sqs-kafka/references/kafka-produtor-consumidor.md).
- Relay de outbox com quota (at-least-once): [PublicadorOutbox](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/PublicadorOutbox.java),
  provado por [PublicadorOutboxTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/PublicadorOutboxTest.java).
- Lado SQS da ponte (delete só após CONFIRMAR): [ConsumidorSqsLimitado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumidorSqsLimitado.java);
  503 com `Retry-After` na borda REST: [CheckoutApplication](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java).
