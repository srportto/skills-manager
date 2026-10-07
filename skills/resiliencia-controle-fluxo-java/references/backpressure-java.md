# Backpressure em Java

Backpressure é realimentação: o consumidor controla demanda ou sinaliza redução ao produtor. Fila limitada sozinha é um limite de armazenamento; rejeição é admissão/load shedding. A proteção ponta a ponta depende de todos os adaptadores cooperarem.

## Escolha da execução

- MVC/JDBC: use admissão por recurso, conexões limitadas e timeout. Virtual threads permitem esperar I/O com menor custo de thread, sem criar CPU ou conexões extras.
- Reactor/Flow: demanda inicial, request(n), cancelamento e prefetch devem ser conhecidos. Operadores podem solicitar itens antecipadamente; limite também concorrência de flatMap e buffers.
- Origem não regulável: escolha overflow explícito (erro, persistência ou descarte aceitável). Nunca esconda pedidos perdidos como amostragem de métricas.
- Mensageria: broker armazena backlog segundo retenção; poll do consumidor não limita automaticamente o produtor. Consulte [controle de consumo](../../mensageria-sqs-kafka/references/controle-consumo-java.md). Três regras que não mudam com a forma de execução (virtual threads inclusive):
  1. **Paralelismo só entre partições/chaves.** Registros da mesma partição são processados um de cada vez, em ordem; mandar cada registro do `poll` para uma thread quebra a ordem por chave.
  2. **Nunca confirme offset de registro não concluído.** O commit é `offset + 1` do último registro concluído **em sequência** na partição; falha ou registro pendente antes dele segura o commit daquela partição.
  3. **Saturação no consumidor = `pause`, não exceção.** Sem capacidade, pause a partição e mantenha o registro não concluído pendente. Se descartar o que já foi lido, faça `seek` para o primeiro não concluído. Lançar `RejectedExecutionException` e seguir para o próximo `poll`/commit perde o registro. Durante a pausa, o `poll()` continua.

Não execute JDBC ou sleep em event loop. Mover para executor não resolve sobrecarga se a fila desse executor for ilimitada. Não use onBackpressureBuffer sem capacidade, política de overflow e observação. limitRate regula pedidos upstream, não taxa temporal global.

## Exemplos e prova

[FluxoSobDemanda.java](../../../examples/java/reativo/src/main/java/br/com/srportto/exemplos/FluxoSobDemanda.java) e seu teste exercitam demanda inicial zero, lotes e cancelamento. [FilaLimitada.java](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FilaLimitada.java) fornece rejeição imediata. [ControleConcorrencia.java](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ControleConcorrencia.java) limita operações síncronas.

Ao retornar Future/CompletionStage, a liberação deve ocorrer na conclusão real, inclusive erro/cancelamento; não em finally do método que apenas criou o future. Timeout externo pode deixar trabalho remoto ativo: use idempotência/reconciliação para efeitos desconhecidos.

Prova: número emitido não excede demanda; cancelamento chega à origem; overflow é observável; máximo ativo respeita limite; falha libera permissão; backoff não retém recurso escasso.

Fonte: [Reactive Streams](https://www.reactive-streams.org/) e [testes Reactor](https://projectreactor.io/docs/core/release/reference/testing.html).


## Antes/depois (Java 25)

```java
// ANTES: fila ilimitada e executor sem teto; produtor rápido esgota o heap e ninguém é avisado
var executor = Executors.newFixedThreadPool(4);          // LinkedBlockingQueue sem limite por trás
pedidos.forEach(p -> executor.submit(() -> processar(p)));
Flux.interval(Duration.ofMillis(1)).onBackpressureBuffer().subscribe(this::processar); // buffer sem capacidade
```

```java
// DEPOIS: fila limitada com rejeição explícita e demanda controlada
var fila = new FilaLimitada<Pedido>(500);
switch (fila.oferecer(pedido)) {
    case ACEITO -> metricas.aceito();
    case REJEITADO_POR_CAPACIDADE -> throw new SobrecargaException(Duration.ofSeconds(1)); // 429/503 + Retry-After
}

Flux.range(1, 1_000).limitRate(32).subscribe(this::processar);          // pede em lotes, não ilimitado
Flux.interval(Duration.ofMillis(1))
    .onBackpressureBuffer(100, descartado -> metricas.descartado(), BufferOverflowStrategy.DROP_LATEST);
```

Laço de consumo Kafka (regras de mensageria acima):

```java
// ANTES: cada registro do poll numa virtual thread e commit do lote inteiro
for (ConsumerRecord<String, Evento> registro : consumidor.poll(Duration.ofMillis(500))) {
    workers.submit(() -> processar(registro));   // mesma chave em paralelo: ordem perdida; erro fica no Future
}
consumidor.commitSync();                          // confirma também o que falhou ou ainda nem terminou
```

```java
// DEPOIS: sem laço próprio. Use o padrão pausa-por-partição da fonte única (ConsumidorKafkaLimitado)
var controle = new ConsumoControlado<ConsumerRecord<String, Evento>>(
        registro -> servico.processar(registro.value()),     // efeito idempotente: reentrega é esperada
        erro -> erro instanceof EventoInvalidoException,      // falha permanente → quarentena
        (registro, causa) -> dlt.publicar(registro, causa));   // DLT durável antes do commit
try (var consumo = new ConsumidorKafkaLimitado<>(kafkaConsumer, controle,
        20, 3, Duration.ofMillis(200), System::nanoTime)) {  // 20 em voo no total; 3 tentativas; 200 ms entre elas
    consumo.assinar("eventos");
    while (ativo) {
        consumo.ciclo(Duration.ofMillis(100));                // a thread do poll() nunca espera o processamento
    }
}
```

O que cada `ciclo` faz, sempre na thread do `poll()`:

1. Aplica os resultados que os workers devolveram por fila. Concluído → `offset + 1` a commitar. Falha (qualquer
   exceção, classificada no `ConsumoControlado`) → a mesma mensagem é repetida e a partição não avança.
2. Commita só o concluído.
3. Pausa as partições com trabalho em voo.
4. Chama `poll()`, mantendo o membro vivo no grupo.
5. Despacha no máximo um registro por partição, respeitando o limite total em voo.

Não existe `Future.get()` no laço: esperar o lote dentro dele para de chamar `poll()`, passa de
`max.poll.interval.ms` e provoca rebalance. Também não existe `catch` seletivo que deixe uma exceção
inesperada avançar a posição. Esgotadas as tentativas, a mensagem vai para a quarentena; se a quarentena falhar,
nada é commitado. Regras completas, rebalance e provas (`MockConsumer` e Kafka real):
[controle de consumo](../../mensageria-sqs-kafka/references/controle-consumo-java.md#kafka-padrão-pausa-por-partição).

Provas que executam essas regras:

- [FilaLimitadaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/FilaLimitadaTest.java): rejeita o excedente e
  aceita de novo após liberar capacidade; limita por itens e por bytes.
- [FluxoSobDemandaTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/FluxoSobDemandaTest.java): não emite além da
  demanda, propaga cancelamento, usa buffer limitado com descarte contado e expõe overflow de fonte não regulável.
- [ProtecoesTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/ProtecoesTest.java): isolamento por bulkhead.
