# Backpressure em Java

Backpressure é realimentação: o consumidor controla demanda ou sinaliza redução ao produtor. Fila limitada sozinha é um limite de armazenamento; rejeição é admissão/load shedding. A proteção ponta a ponta depende de todos os adaptadores cooperarem.

## Escolha da execução

- MVC/JDBC: use admissão por recurso, conexões limitadas e timeout. Virtual threads permitem esperar I/O com menor custo de thread, sem criar CPU ou conexões extras.
- Reactor/Flow: demanda inicial, request(n), cancelamento e prefetch devem ser conhecidos. Operadores podem solicitar itens antecipadamente; limite também concorrência de flatMap e buffers.
- Origem não regulável: escolha overflow explícito (erro, persistência ou descarte aceitável). Nunca esconda pedidos perdidos como amostragem de métricas.
- Mensageria: broker armazena backlog segundo retenção; poll do consumidor não limita automaticamente o produtor. Consulte [controle de consumo](../../mensageria-sqs-kafka/references/controle-consumo-java.md).

Não execute JDBC ou sleep em event loop. Mover para executor não resolve sobrecarga se a fila desse executor for ilimitada. Não use onBackpressureBuffer sem capacidade, política de overflow e observação. limitRate regula pedidos upstream, não taxa temporal global.

## Exemplos e prova

[FluxoSobDemanda.java](../../../examples/java/reativo/src/main/java/br/com/srportto/exemplos/FluxoSobDemanda.java) e seu teste exercitam demanda inicial zero, lotes e cancelamento. [FilaLimitada.java](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FilaLimitada.java) fornece rejeição imediata. [ControleConcorrencia.java](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ControleConcorrencia.java) limita operações síncronas.

Ao retornar Future/CompletionStage, a liberação deve ocorrer na conclusão real, inclusive erro/cancelamento; não em finally do método que apenas criou o future. Timeout externo pode deixar trabalho remoto ativo: use idempotência/reconciliação para efeitos desconhecidos.

Prova: número emitido não excede demanda; cancelamento chega à origem; overflow é observável; máximo ativo respeita limite; falha libera permissão; backoff não retém recurso escasso.

Fonte: [Reactive Streams](https://www.reactive-streams.org/) e [testes Reactor](https://projectreactor.io/docs/core/release/reference/testing.html).

