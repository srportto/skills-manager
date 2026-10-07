# Escolha de broker: SQS × Kafka × RabbitMQ

Leia este arquivo quando precisar decidir qual broker usar para um fluxo novo.

## 7. Decisão SQS × Kafka × RabbitMQ

| Aspecto | SQS | Kafka | RabbitMQ |
|---|---|---|---|
| Modelo | Fila gerenciada, consumo destrutivo | Log retido, consumo por offset | Broker com exchanges/filas, consumo destrutivo |
| Múltiplos consumidores independentes | Via SNS fan-out | Nativo (um grupo por consumidor) | Via exchange fan-out |
| Ordem | FIFO por grupo | Por partição | Por fila (com 1 consumidor ou single-active) |
| Replay | DLQ redrive | Nativo dentro da retenção | Não nativo (streams à parte) |
| Controle de fluxo do consumidor | Quantas mensagens pedir | `max.poll.records`, pause/resume | `prefetch` (QoS) |
| Operação | Gerenciado (AWS) | Cluster/serviço gerenciado; partições e retenção a dimensionar | Cluster a operar |
| Quando usar | Trabalho a executar por um processador | Histórico/eventos para vários consumidores, replay | Roteamento flexível, RPC assíncrono, filas de trabalho |


## Antes/depois: escolha guiada pelo requisito

```text
ANTES: "usamos Kafka porque é o padrão" para um fluxo de jobs de e-mail, 50 msg/s, sem replay,
       com um único processador -> cluster, partições e retenção a operar sem necessidade.

DEPOIS: requisito "trabalho a executar por um processador, retry com DLQ, sem replay" -> SQS standard
        com DLQ + RedrivePolicy. Requisito "vários consumidores independentes + replay de 7 dias" -> Kafka.
```

```yaml
# Exemplo: o mesmo serviço expõe o requisito como configuração explícita (Kafka: retenção e réplicas)
# kafka-topics: retention.ms=604800000 (7 dias), replication.factor=3, min.insync.replicas=2
```

Regras comuns a qualquer broker (ack só após efeito, quarentena durável, idempotência persistida) estão em
[sqs-dlq-redrive.md](sqs-dlq-redrive.md), [erro-central-interceptor.md](erro-central-interceptor.md) e
[idempotencia-outbox-replay-java.md](idempotencia-outbox-replay-java.md). Provas: [ProcessadorIdempotenteTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteTest.java).
