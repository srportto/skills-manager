# SQS: DLQ, RedrivePolicy, visibility timeout e mensagens em voo

Leia este arquivo quando for criar ou revisar uma fila SQS (Terraform, CLI, Floci local), ajustar `maxReceiveCount`, visibility timeout ou o limite de mensagens em voo.

## Regra de ouro: toda fila SQS nasce com sua DLQ

**Nenhuma fila SQS é criada — em Terraform, CLI ou qualquer IaC, nem localmente — sem DLQ e `RedrivePolicy`.**
Sem DLQ, uma mensagem venenosa reentrega para sempre, consome throughput e não deixa rastro.

```hcl
# Terraform - fila principal + DLQ SEMPRE juntas
resource "aws_sqs_queue" "fila_dlq" {
  name                      = "fila-pedidos-dlq"
  message_retention_seconds = 1209600 # 14 dias: tempo para investigar e fazer redrive
}

resource "aws_sqs_queue" "fila" {
  name                       = "fila-pedidos"
  visibility_timeout_seconds = 60 # > tempo máximo de processamento, ou renove durante o processamento
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.fila_dlq.arn
    maxReceiveCount     = 3
  })
}
```

Localmente, use o **Floci** (emulador AWS, porta 4566): `docker run -d -p 4566:4566 floci/floci:2.2.0`. Com
`AWS_ENDPOINT_URL=http://localhost:4566`, `AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test` e
`AWS_DEFAULT_REGION=us-east-1`, AWS CLI e AWS SDK v2 apontam para ele sem mudança de código. A mesma regra: criar
a DLQ, obter o ARN e só então criar a fila com `RedrivePolicy`:

```bash
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name fila-pedidos-dlq
ARN_DLQ=$(aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/fila-pedidos-dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name fila-pedidos \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$ARN_DLQ\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
```

`maxReceiveCount=3` é um default razoável, não universal: conte que **cada recebimento é uma tentativa** (inclusive
os que expiraram por visibility timeout). A retenção da DLQ deve ser **maior** que a da fila principal, senão a
mensagem pode expirar antes da investigação. O `java-revisor` (modo `auditoria`) reprova fila nova sem DLQ.

### Visibility timeout e renovação

Intervalo em que a mensagem entregue fica invisível. Se o processamento passar do timeout, a mensagem reaparece
e **outro consumidor processa a mesma mensagem em paralelo**. Duas opções: timeout acima do tempo máximo de
processamento (incluindo retries internos e deadline), ou **renovar** com `ChangeMessageVisibility` enquanto
processa (ex.: a cada metade do timeout), parando ao terminar. Visibilidade máxima por mensagem: 12 horas.

### Mensagens em voo limitadas

Peça ao SQS só o que cabe: `MaxNumberOfMessages = min(10, capacidade livre)`. Mensagens recebidas e não
processadas ficam invisíveis consumindo o timeout — buscar mais do que se processa gera reentregas e duplicatas.
O backlog deve ficar **no broker**, não em fila em memória. Com `@SqsListener` (Spring Cloud AWS), limite via
`maxConcurrentMessages`/`maxMessagesPerPoll` do container.


## Antes/depois: criação de fila em Java (AWS SDK v2, Floci local)

```java
// ANTES: fila sem DLQ -> mensagem venenosa reentrega para sempre (reprovado: Crítico)
sqs.createQueue(r -> r.queueName("fila-pedidos"));
```

```java
// DEPOIS: DLQ primeiro, ARN obtido, e só então a fila principal com RedrivePolicy
SqsClient sqs = SqsClient.builder()
    .endpointOverride(URI.create("http://localhost:4566")) // Floci (emulador AWS local)
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
    .build();

String urlDlq = sqs.createQueue(r -> r.queueName("fila-pedidos-dlq")
    .attributes(Map.of(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"))).queueUrl(); // 14 dias
String arnDlq = sqs.getQueueAttributes(r -> r.queueUrl(urlDlq)
    .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);

sqs.createQueue(r -> r.queueName("fila-pedidos").attributes(Map.of(
    QueueAttributeName.VISIBILITY_TIMEOUT, "60",
    QueueAttributeName.REDRIVE_POLICY,
        "{\"deadLetterTargetArn\":\"" + arnDlq + "\",\"maxReceiveCount\":\"3\"}")));
```

Prova executável de trabalho em voo limitado e confirmação só após o efeito:
[ConsumidorSqsLimitadoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoTest.java)
e [ConsumidorSqsLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java)
(perfil `integracao`, Docker obrigatório). Política geral de retry/backoff: `resiliencia-controle-fluxo-java`.
