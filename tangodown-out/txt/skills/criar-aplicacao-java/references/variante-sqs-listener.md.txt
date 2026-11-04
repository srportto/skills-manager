# Variantes: sqs-listener e sqs-para-banco

Leia este arquivo quando a aplicação **consome uma fila SQS** — com idempotência em memória (`sqs-listener`,
demonstração de instância única) ou persistente (`sqs-para-banco`, produção). As regras de DLQ, `RedrivePolicy`,
ack e erro central vivem em [mensageria-sqs-kafka](../../mensageria-sqs-kafka/SKILL.md) — aqui está só o que
acrescentar ao `assets/esqueleto`.

## O que adicionar sobre `assets/esqueleto`

**Dependência:** o exemplo executável do catálogo usa o AWS SDK v2 direto (`software.amazon.awssdk:sqs`, ver
`examples/java/integracao/pom.xml`). Se preferir Spring Cloud AWS, confira antes a versão compatível com Spring Boot 4
(não validada neste catálogo). Para `sqs-para-banco`, some `spring-boot-starter-data-jpa` e o driver — ver
[variante-crud-banco](variante-crud-banco.md).

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sqs</artifactId>
    <!-- versão: copie a de examples/java/integracao/pom.xml (a que o catálogo prova) -->
</dependency>
```

**Pacotes novos:** `domain/port/in/Processar<Evento>UseCase`, `application/usecase/*`,
`infrastructure/messaging/{Sqs<Evento>Listener, <Evento>ErrorInterceptor}` e `infrastructure/config/SqsConfig`
(cliente com timeouts explícitos). Em `sqs-para-banco`: `domain/port/out/<Entidade>RepositoryPort` +
`infrastructure/persistence/*` e a tabela de idempotência.

**`application.yml`** (acrescente):

```yaml
app:
  sqs:
    fila-url: ${SQS_FILA_URL}            # fila JÁ criada com DLQ + RedrivePolicy
    max-mensagens-em-voo: 10             # trabalho em voo limitado
    visibility-timeout: 60s              # maior que o p99 do processamento (ou renove durante o trabalho)
    endpoint: ${SQS_ENDPOINT:}           # local: http://localhost:4566 (Floci); vazio em AWS real
```

Fila e DLQ nascem juntas (Terraform/CLI/Floci). Receita e `RedrivePolicy`:
[sqs-dlq-redrive](../../mensageria-sqs-kafka/references/sqs-dlq-redrive.md).

## Componentes (da definição da skill)

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **sqs-listener** | Listener (driving adapter) em `infrastructure/messaging/` com idempotência (em memória só para demonstração de instância única; persistente em produção — ver `sqs-para-banco`), **interceptor central de erro de consumo** (`infrastructure/messaging/*ErrorInterceptor`) e **fila provisionada com DLQ + `RedrivePolicy`** (nunca uma sem a outra). | `mensageria-sqs-kafka` (`sqs-dlq-redrive.md`, `erro-central-interceptor.md`) |
| **sqs-para-banco** | Como acima + idempotência **persistente** (constraint única) + gravação via `port/out` e adapter JPA. | `mensageria-sqs-kafka`, `persistencia-jpa` |

**Toda variante que envolva SQS SHALL nascer com DLQ na fila e com o interceptor central de erro de
consumo** — não é opcional, é parte da definição da variante (ver regra de ouro em
`mensageria-sqs-kafka` seção 2 e o padrão da seção 3).

## Proteções e provas

| Variante | Proteções obrigatórias | Prova mínima gerada |
|---|---|---|
| sqs-listener / sqs-para-banco | Mensagens em voo limitadas, visibility timeout coerente (ou renovação), DLQ + RedrivePolicy, idempotência (persistente em `sqs-para-banco`), delete só após efeito | Duplicata não repete efeito; falha não apaga mensagem |

## Antes / depois: ack só depois do efeito, decisão em um ponto

```java
// ANTES: try/catch decide o ack inline e engole a falha; a mensagem some sem efeito
void tratar(Message msg) {
    try {
        service.processar(msg.body());
    } catch (Exception e) {
        log.error("falhou", e);            // erro engolido
    } finally {
        sqs.deleteMessage(b -> b.queueUrl(fila).receiptHandle(msg.receiptHandle()));   // apaga SEMPRE
    }
}
```

```java
// DEPOIS: um ponto central classifica a falha; delete só em CONFIRMAR (efeito durável ou cópia em quarentena)
void tratar(Message msg) {
    switch (erroInterceptor.executar(msg, () -> useCase.processar(msg.body()))) {
        case CONFIRMAR -> sqs.deleteMessage(b -> b.queueUrl(fila).receiptHandle(msg.receiptHandle()));
        case REENTREGAR -> { /* não apaga: a mensagem reaparece após o visibility timeout */ }
    }
}
```

Esgotadas as tentativas, é a `RedrivePolicy` da fila que move a mensagem para a DLQ — o código não "conta tentativas"
por conta própria. Idempotência: duplicata não repete o efeito (em `sqs-para-banco`, restrição única na mesma transação do efeito).

## Fontes únicas e exemplos executáveis

- DLQ/visibility/mensagens em voo: [sqs-dlq-redrive](../../mensageria-sqs-kafka/references/sqs-dlq-redrive.md); ponto central de erro:
  [erro-central-interceptor](../../mensageria-sqs-kafka/references/erro-central-interceptor.md); idempotência persistida:
  [idempotencia-outbox-replay-java](../../mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md).
- Consumo com mensagens em voo limitadas, renovação de visibilidade e delete só após CONFIRMAR:
  [ConsumidorSqsLimitado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumidorSqsLimitado.java),
  decisão central em [ConsumoControlado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumoControlado.java);
  provas em [ConsumidorSqsLimitadoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoTest.java)
  e [ConsumidorSqsLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java)
  (Floci via Testcontainers; perfil `integracao`, Docker obrigatório).
- Idempotência persistente: [ProcessadorIdempotente](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java).
