# Protocolos e comunicação

| Estilo | Quando ajuda | Custo/risco | Contrato e prova |
|---|---|---|---|
| HTTP/REST | Recursos, interoperabilidade, cache | Fan-out, round trips, retry de escrita | Status, idempotência, deadline e paginação |
| gRPC | Contrato tipado, chamadas internas, streaming | Compatibilidade protobuf, intermediários HTTP/2 | Deadline, cancelamento, evolução de campos |
| GraphQL | Cliente escolhe projeção/composição | N+1, consulta cara e autorização por campo | Limite de profundidade/custo, paginação e batching |
| WebSocket | Chat/presença/duplex contínuo | Conexões longas, slow clients, reconexão | Buffer por conexão, sequência, resync e heartbeat |
| Eventos | Desacoplamento temporal e múltiplos consumidores | Duplicatas, ordem parcial, schema e replay | Chave/eventId, evolução e efeito idempotente |

Síncrono preserva resposta imediata, mas acopla disponibilidade e deadline. Assíncrono exige estados pendentes e lag aceitável; não é automaticamente mais consistente ou mais barato.

RabbitMQ: fila/roteamento e ack; prefetch ajuda a limitar entregas em voo. SQS: serviço de fila com visibility timeout, reentrega e DLQ; FIFO tem semântica própria, não elimina idempotência de efeitos. Kafka: log retido e grupos independentes, ordem por partição, replay e gerenciamento de offsets. Escolha por requisitos e custo operacional.

At-most-once pode perder ao confirmar antes; at-least-once exige deduplicação; exactly-once deve declarar a fronteira (por exemplo, transações Kafka não tornam HTTP externo exatamente uma vez).

## Deadline e cancelamento por protocolo

| Protocolo | Como propagar o prazo | Como cancelar |
|---|---|---|
| HTTP/REST | `HttpRequest.timeout(restante)`; header de deadline se o contrato prever | Fechar a conexão não cancela o trabalho remoto |
| gRPC | Deadline nativo propagado no contexto | Cancelamento propaga ao servidor, que deve checar `Context.isCancelled()` |
| Eventos | Campo de expiração no evento; consumidor descarta trabalho vencido | Não há cancelamento: use estado/compensação |
| WebSocket | Heartbeat e tempo máximo por operação de aplicação | Fechar a conexão; buffer por conexão limitado (cliente lento é desconectado) |

```java
// gRPC: o deadline viaja com a chamada; o servidor sabe quanto tempo resta e pode desistir.
var resposta = estoqueStub
        .withDeadlineAfter(orcamento.restante().toMillis(), TimeUnit.MILLISECONDS)
        .reservar(ReservarRequest.newBuilder().setPedidoId(pedidoId).setQuantidade(quantidade).build());
```

Todos os clientes e trechos de implementação desta trilha são Java. Para garantias concretas: [mensageria](../../mensageria-sqs-kafka/SKILL.md); para limites: [resiliência](../../resiliencia-controle-fluxo-java/SKILL.md). Não iniciar SDKs/brokers quando o pedido requer apenas comparar alternativas.

