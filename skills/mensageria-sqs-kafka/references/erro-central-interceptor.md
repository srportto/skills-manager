# Ponto central de decisão de erro (listener e interceptor)

Leia este arquivo quando for decidir entre confirmar, reentregar ou quarentenar uma mensagem, ou configurar o `DefaultErrorHandler`/DLT central.

## Ponto central de decisão de erro

Assim como o `@ControllerAdvice` classifica exceções HTTP num só lugar, **toda falha de consumo passa por um ponto
único** que decide entre **confirmar**, **reentregar** ou **quarentenar**:

| Situação | Decisão | Por quê |
|---|---|---|
| Efeito concluído (ou já aplicado antes — idempotência) | Confirmar | Trabalho feito |
| Falha transitória (timeout, dependência fora, lock) | Reentregar | Nova tentativa pode funcionar; tentativas limitadas |
| Falha permanente (schema inválido, regra violada) | Copiar para quarentena (DLQ/DLT) **e então** confirmar | Não adianta repetir; o dado de negócio não pode sumir |
| Quarentena indisponível | **Não confirmar** (reentregar) | Confirmar sem cópia durável é perda silenciosa |
| Efeito com resultado desconhecido (timeout após enviar) | Reentregar + idempotência/reconciliação | Repetir às cegas pode duplicar o efeito |
| Falha desconhecida | Tratar como transitória | Repetir é mais seguro que descartar |

Implementação de referência, testada:
[ConsumoControlado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumoControlado.java).

**Listener manual (SDK)** — o listener só delega:

```java
// infrastructure/messaging — o listener não decide nada sozinho
var resultado = decisao.consumir(mensagem);           // ConsumoControlado<Message>
if (resultado.decisao() == ConsumoControlado.Decisao.CONFIRMAR) {
    sqs.deleteMessage(r -> r.queueUrl(fila).receiptHandle(mensagem.receiptHandle()));
}
// REENTREGAR: não apaga; a mensagem reaparece após o timeout/backoff e a RedrivePolicy limita as tentativas.
```

**`@KafkaListener` (spring-kafka)** — o ponto central é um `DefaultErrorHandler` configurado uma vez:

```java
@Bean
DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    var recuperador = new DeadLetterPublishingRecoverer(kafkaTemplate);
    // 1 tentativa inicial + 3 retentativas, com backoff exponencial (1 s, 2 s, 4 s, teto 10 s).
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(1_000L);
    backoff.setMultiplier(2.0);
    backoff.setMaxInterval(10_000L);
    var handler = new DefaultErrorHandler(recuperador, backoff);
    // Exceção permanente vai direto para o DLT, sem gastar retentativas.
    handler.addNotRetryableExceptions(PedidoInvalidoException.class);
    return handler;
}
```

Notas: `FixedBackOff(1000L, 3)` significa **3 retentativas** (4 execuções no total). O retry do
`DefaultErrorHandler` acontece **bloqueando a partição** (seek para o registro que falhou): retries longos atrasam
toda a partição e contam para `max.poll.interval.ms`. Se a publicação no DLT falhar, o handler não commita o
offset — o registro é reprocessado (configure `DeadLetterPublishingRecoverer` para **falhar** quando o envio falhar,
nunca para ignorar). Com `@SqsListener` gerenciado, o equivalente é um `ErrorHandler`/`AcknowledgementResultCallback`
central.

**Reprova em revisão:** classificação duplicada; `try/catch` no listener decidindo ack inline; descarte genérico
(`return true` / ack) de mensagem com dado de negócio sem quarentena durável; ack antes do efeito.


## Antes/depois: classificação espalhada × ponto central

```java
// ILUSTRATIVO - ANTES: try/catch no listener decide o ack por conta própria; descarta dado sem rastro
@SqsListener("fila-pedidos")
void ouvir(Message<String> msg) {
    try {
        service.processar(msg.getPayload());
    } catch (Exception e) {
        log.warn("ignorando mensagem", e); // ack implícito: a mensagem some
    }
}
```

```java
// ILUSTRATIVO - DEPOIS: o listener só delega; falha vira decisão central (confirmar / reentregar / quarentena)
@SqsListener("fila-pedidos")
void ouvir(Message<String> msg) {
    var resultado = decisao.consumir(msg); // ConsumoControlado: transitória -> relança; permanente -> DLQ e então confirma
    if (resultado.decisao() == ConsumoControlado.Decisao.REENTREGAR) {
        throw new FalhaTransitoriaException("reentregar " + msg.getHeaders().getId());
    }
}
```

Provas: [ConsumoControladoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumoControladoTest.java)
(falha de envio à quarentena não confirma; falha desconhecida reentrega). Para a política de retry/backoff e o
orçamento de tentativas, veja `resiliencia-controle-fluxo-java` ([deadline e retry](../../resiliencia-controle-fluxo-java/references/timeouts-retries-java.md)).
