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


## Exemplo aplicado: checkout

Caso de estudo: [checkout](estudos-de-caso-java.md#4-e-commerce-em-alta-escala-checkout--caso-executável). Escolha de protocolo por salto, com a perda aceita:

| Salto | Protocolo | Por quê | Contrato que o torna seguro |
|---|---|---|---|
| Cliente para checkout | HTTP/REST | Recurso `pedidos`, interoperável com web e mobile | `Idempotency-Key` obrigatório (400 se ausente, 422 se o valor for inválido); 409 se a chave voltar com outro payload; 503 + `Retry-After` sob saturação |
| Checkout para provedor de pagamento | HTTP síncrono com deadline | Resposta imediata necessária para confirmar o pedido | Deadline restante propagado; chave de idempotência enviada ao provedor; bulkhead |
| Checkout para notificação/faturamento | Evento (Kafka) via outbox | Desacopla disponibilidade; vários consumidores | `eventId` para deduplicar; at-least-once; ordem por chave `pedidoId` |

Antes/depois do contrato HTTP de criação:

```http
# Antes: sem chave de idempotência — retry do cliente cria pedido duplicado
POST /pedidos
Content-Type: application/json

{"centavos": 15990}

# Depois: chave obrigatória; repetição devolve o mesmo pedido, e saturação é explícita
POST /pedidos
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7
X-Tenant: loja-1
Content-Type: application/json

{"centavos": 15990}

# 201 Created -> {"id":"..."}              (primeira vez e repetições idênticas)
# 409 Conflict -> Problem Details          (mesma chave, payload diferente)
# 503 Service Unavailable + Retry-After    (admissão esgotada; cliente espera e repete com a MESMA chave)
```

O trecho de deadline em gRPC acima aplica-se ao salto de estoque, se existir; para o provedor HTTP, o equivalente é `HttpRequest.timeout(orcamento.restante())`. Se o tempo restante for insuficiente para a 1ª tentativa, não chame: falhe rápido e deixe o pedido `PENDENTE` para reconciliação.

Provas executáveis: [CheckoutApplicationTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java) cobre os três status do contrato HTTP (201 repetido, 409, 503 com `Retry-After`); [CheckoutSobCargaSimulationCargaIT](../../../examples/java/carga/src/test/java/br/com/srportto/exemplos/CheckoutSobCargaSimulationCargaIT.java) mostra o comportamento sob pico. Detalhes de contrato HTTP: `api-rest-design`; de ack/DLQ: [mensageria](../../mensageria-sqs-kafka/SKILL.md).
