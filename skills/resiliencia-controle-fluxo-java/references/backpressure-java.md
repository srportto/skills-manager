# Backpressure em Java

Backpressure é realimentação: o consumidor controla demanda ou sinaliza redução ao produtor. Fila limitada sozinha é um limite de armazenamento; rejeição é admissão/load shedding. A proteção ponta a ponta depende de todos os adaptadores cooperarem.

## Escolha da execução

- MVC/JDBC: use admissão por recurso, conexões limitadas e timeout. Virtual threads permitem esperar I/O com menor custo de thread, sem criar CPU ou conexões extras.
- Reactor/Flow: demanda inicial, request(n), cancelamento e prefetch devem ser conhecidos. Operadores podem solicitar itens antecipadamente; limite também concorrência de flatMap e buffers.
- Origem não regulável: escolha overflow explícito (erro, persistência ou descarte aceitável). Nunca esconda pedidos perdidos como amostragem de métricas.
- Mensageria: broker armazena backlog segundo retenção; poll do consumidor não limita automaticamente o produtor. Consulte [controle de consumo](../../mensageria-sqs-kafka/references/controle-consumo-java.md). Três regras que não mudam com a forma de execução (virtual threads inclusive):
  1. **Paralelismo só entre partições/chaves.** Registros da mesma partição são processados um de cada vez, em ordem; mandar cada registro do `poll` para uma thread quebra a ordem por chave.
  2. **Nunca confirme offset de registro não concluído.** O commit é `offset + 1` do último registro concluído **em sequência** na partição; falha ou registro pendente antes dele segura o commit daquela partição.
  3. **Saturação no consumidor = `pause` + `seek`, não exceção.** Sem capacidade, pause a partição e volte a posição (`seek`) para o primeiro registro não concluído; lançar `RejectedExecutionException` e seguir para o próximo `poll`/commit perde o registro.

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
// DEPOIS: paralelo só ENTRE partições; dentro da partição, em ordem; commit só do prefixo concluído
ConsumerRecords<String, Evento> lote = consumidor.poll(Duration.ofMillis(500));
Map<TopicPartition, Future<Long>> porParticao = new HashMap<>();
for (TopicPartition particao : lote.partitions()) {
    List<ConsumerRecord<String, Evento>> registros = lote.records(particao);
    porParticao.put(particao, workers.submit(() -> processarEmOrdem(registros)));
}
Map<TopicPartition, OffsetAndMetadata> aCommitar = new HashMap<>();
for (var entrada : porParticao.entrySet()) {
    TopicPartition particao = entrada.getKey();
    long proximo = entrada.getValue().get();      // tempo do lote ≪ max.poll.interval.ms
    aCommitar.put(particao, new OffsetAndMetadata(proximo));
    if (proximo <= lote.records(particao).getLast().offset()) { // parou antes do fim
        consumidor.seek(particao, proximo);       // o não concluído volta no próximo poll
        consumidor.pause(List.of(particao));      // resume() quando o downstream voltar ou após o backoff
    }
}
consumidor.commitSync(aCommitar);                 // nunca além do último registro concluído

/** Processa em ordem e devolve o próximo offset a ler: para no primeiro registro não concluído. */
private long processarEmOrdem(List<ConsumerRecord<String, Evento>> registros) {
    for (ConsumerRecord<String, Evento> registro : registros) {
        try {
            servico.processar(registro.value());  // idempotente: reentrega após falha é esperada
        } catch (DownstreamSaturadoException | FalhaTransitoriaException naoConcluido) {
            return registro.offset();             // este e os seguintes não avançam
        }
    }
    return registros.getLast().offset() + 1;
}
```

Falha permanente (esgotou tentativas) vai para a quarentena/DLT antes do commit; se a quarentena falhar, a
partição não avança. Versão completa — limite de trabalho em voo, retomada, rebalance —, provada com
`MockConsumer` e Kafka real: [controle de consumo](../../mensageria-sqs-kafka/references/controle-consumo-java.md#kafka-padrão-pausa-por-partição).

Provas que executam essas regras:

- [FilaLimitadaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/FilaLimitadaTest.java): rejeita o excedente e
  aceita de novo após liberar capacidade; limita por itens e por bytes.
- [FluxoSobDemandaTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/FluxoSobDemandaTest.java): não emite além da
  demanda, propaga cancelamento, usa buffer limitado com descarte contado e expõe overflow de fonte não regulável.
- [ProtecoesTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/ProtecoesTest.java): isolamento por bulkhead.
