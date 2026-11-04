# Variante: kafka-consumer

Leia este arquivo quando a aplicação **consome tópicos Kafka**. Chave/ordenação, commit, retry/DLT e rebalance
têm fonte única em [mensageria-sqs-kafka](../../mensageria-sqs-kafka/SKILL.md); aqui está o que acrescentar ao
`assets/esqueleto`.

## O que adicionar sobre `assets/esqueleto`

**Dependência:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

> Spring Boot 4 tem starter dedicado ao Kafka; confirme o nome no BOM da versão em uso (`mvn dependency:tree`)
> antes de fixar. Serialização JSON usa **Jackson 3** (`tools.jackson`); desserialize para um `record` do próprio
> adapter, não para o modelo de domínio.

**Pacotes novos:** `domain/port/in/Processar<Evento>UseCase`, `application/usecase/*`,
`infrastructure/messaging/<Evento>KafkaListener` e `infrastructure/config/KafkaConsumerConfig` (o
`DefaultErrorHandler` central mora aqui).

**`application.yml`** (acrescente):

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP}
    consumer:
      group-id: ${spring.application.name}
      enable-auto-commit: false          # commit após o efeito, nunca antes
      max-poll-records: 50               # dimensionado: max-poll-records x tempo por registro < max.poll.interval.ms
      properties:
        max.poll.interval.ms: 300000
    listener:
      ack-mode: record                   # commit por registro concluído
```

## Componentes (da definição da skill)

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **kafka-consumer** | `@KafkaListener` em `infrastructure/messaging/` + `DefaultErrorHandler`/`DeadLetterPublishingRecoverer` central (o ponto único de erro é o próprio `DefaultErrorHandler`, configurado em `infrastructure/config/`). | `mensageria-sqs-kafka` (`erro-central-interceptor.md`, `kafka-produtor-consumidor.md`) |

## Proteções e provas

| Variante | Proteções obrigatórias | Prova mínima gerada |
|---|---|---|
| kafka-consumer | `max.poll.records` dimensionado, commit após efeito, `DefaultErrorHandler` com tentativas limitadas + DLT, idempotência | Reentrega não duplica efeito; DLT indisponível não commita |

## Antes / depois: erro tratado em um ponto, DLT, tentativas limitadas

```java
// ANTES: catch dentro do listener engole a falha e o offset avança (mensagem perdida)
@KafkaListener(topics = "pedidos")
void consumir(String payload) {
    try {
        useCase.processar(payload);
    } catch (Exception e) {
        log.error("erro", e);              // sem retry, sem DLT: dado perdido em silêncio
    }
}
```

```java
// DEPOIS: o listener só chama o caso de uso; a decisão de erro é do DefaultErrorHandler central
@KafkaListener(topics = "pedidos")
void consumir(PedidoEvento evento) {
    useCase.processar(evento.paraComando());   // falha propaga ao handler central
}

// infrastructure/config/KafkaConsumerConfig
@Bean
DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);                 // vai para <topico>.DLT
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3)); // 3 retentativas, depois DLT
    handler.addNotRetryableExceptions(BusinessException.class);                  // falha permanente: DLT direto
    return handler;
}
```

Se a publicação na DLT falhar, o offset **não** é commitado (o registro volta); reentrega não pode duplicar o efeito
(idempotência por chave de negócio). O `CommonErrorHandler` é registrado automaticamente no container se houver um
único bean do tipo.

## Fontes únicas e exemplos executáveis

- [kafka-produtor-consumidor](../../mensageria-sqs-kafka/references/kafka-produtor-consumidor.md) (chave, commit, poll, rebalance),
  [erro-central-interceptor](../../mensageria-sqs-kafka/references/erro-central-interceptor.md) (`DefaultErrorHandler` e DLT),
  [controle-consumo-java](../../mensageria-sqs-kafka/references/controle-consumo-java.md) (dimensionamento).
- Trabalho em voo limitado, ordem por partição, commit só do concluído, quarentena que falha não commita:
  [ConsumidorKafkaLimitado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumidorKafkaLimitado.java);
  provas em [ConsumidorKafkaLimitadoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoTest.java)
  e [ConsumidorKafkaLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoExternoIT.java)
  (Kafka real via Testcontainers; perfil `integracao`, Docker obrigatório).
