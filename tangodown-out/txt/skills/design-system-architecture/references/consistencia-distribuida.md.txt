# Consistência e dados distribuídos

Comece pela operação: o usuário precisa ler sua escrita, evitar cobrança duplicada, tolerar inventário stale ou manter ordem por agregado? A resposta vale mais que escolher uma tecnologia por categoria.

CAP descreve, durante partição de rede, tensão entre consistência linearizável e disponibilidade de respostas em todos os nós não falhos. Não é um menu irrestrito de dois atributos. PACELC acrescenta a troca entre latência e consistência quando não há partição. ACID descreve propriedades transacionais; o C de ACID não é sinônimo do C de CAP. BASE é uma abordagem ampla, não um nível de isolamento.

## Replicação e particionamento

Leader/follower facilita ordem de escrita, mas réplica atrasada pode violar read-your-writes. Multi-leader exige política de conflito e impacto de relógio/ordem. Quorum depende de interseção, protocolo, falhas e leitura/escrita; fórmula isolada não comprova linearizabilidade.

Exemplo de quorum (sistemas estilo Dynamo/Cassandra): N=3 réplicas, escrita confirmada com W=2 e leitura com
R=2 → R + W > N garante que leitura e escrita se cruzam em pelo menos uma réplica. Isso **não** basta, sozinho,
para linearizabilidade (relógios, read repair, hinted handoff e falhas parciais importam); com W=1 e R=1 a
latência cai e a leitura pode voltar dado antigo.

**Por operação, não por banco:** saldo e cobrança exigem consistência forte e idempotência; catálogo de
produtos tolera minutos de atraso; contador de curtidas tolera aproximação. Escreva a tolerância (ex.: "leitura
de catálogo pode estar até 60 s desatualizada") e como ela é observada.

Sharding reduz conjunto por nó, mas adiciona roteamento, transações cruzadas e rebalance. Escolha chave com distribuição e localidade; detecte hot keys/tenants. Plano de resharding inclui convivência de rotas, cópia, validação, corte e rollback.

## Efeitos distribuídos

Banco + broker não viram transação única por publicar depois do commit. Outbox grava efeito e intenção juntos, relay publica ao menos uma vez, consumidor deduplica com inbox/efeito transacional. Saga compensa ações; compensação pode falhar e não é rollback ACID global. Documente estados pendentes, reconciliação e operação humana.

Exemplo executável Java: [ProcessadorIdempotente](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java). O exemplo usa mesmo banco para idempotência/efeito/outbox; pagamento remoto exige chave no provedor e reconciliação adicional.

Teste perda de resposta após commit, duplicata concorrente, conflito de payload, leitura em réplica atrasada, falha do relay e replay. Migração usa expand/contract: adicionar campo compatível, migrar dados, conviver leitores, mudar produtor e remover contrato antigo só após janela acordada.


## Exemplo aplicado: checkout

Caso de estudo: [checkout](estudos-de-caso-java.md#4-e-commerce-em-alta-escala-checkout--caso-executável). Aplique a regra "consistência por operação, não por banco" ao fluxo de criação de pedido:

| Operação | Consistência exigida | Tolerância declarada | Como é observada |
|---|---|---|---|
| Criar pedido / cobrança | Forte (uma única decisão por chave de idempotência) | Nenhuma: cobrança duplicada é inaceitável | Teste de duplicata concorrente; métrica de conflitos 409 |
| Consultar status do pedido | Read-your-writes para quem criou | Outros usuários: segundos de atraso | Leitura do primário logo após criar; réplica para listagens |
| Catálogo de produtos | Eventual | Até 60 s desatualizado | Idade do cache exposta em métrica |
| Evento "pedido criado" para o broker | At-least-once | Duplicata aceita; perda não | Lag da outbox e deduplicação no consumidor |

Antes/depois do efeito distribuído (publicar no broker depois do commit). Os tipos `repositorio`, `kafka` e `outbox` abaixo são ilustrativos; a implementação executável está no `ProcessadorIdempotente`:

```java
// Antes: commit no banco e publicação no Kafka são dois efeitos independentes.
// Se o processo cai entre os dois, o pedido existe e o evento nunca sai.
@Transactional
public PedidoResponse criar(PedidoRequest req) {
    var pedido = repositorio.save(Pedido.novo(req));
    kafka.send("pedidos", pedido.id(), pedido.paraEvento()); // fora da transação do banco
    return PedidoResponse.de(pedido);
}

// Depois: pedido e intenção de publicar entram na MESMA transação (outbox);
// um relay publica ao menos uma vez e o consumidor deduplica por eventId.
@Transactional
public PedidoResponse criar(PedidoRequest req) {
    var pedido = repositorio.save(Pedido.novo(req));
    outbox.save(EventoOutbox.de("pedidos", pedido.id(), pedido.paraEvento()));
    return PedidoResponse.de(pedido);
}
```

Prova executável: [CheckoutApplication](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java) usa [ProcessadorIdempotente](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java) para gravar chave, efeito e outbox juntos; [CheckoutApplicationTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java) cobre repetição com a mesma chave (mesma resposta, sem efeito duplicado) e conflito 409 quando a chave volta com outro payload.

Perguntas de revisão para este caso: o que o cliente vê se o relay estiver parado por 10 min? (pedido `CRIADO`, evento pendente, lag alertado); quem reconcilia o pagamento remoto se a resposta se perder após o commit? (job de reconciliação com a chave enviada ao provedor).
