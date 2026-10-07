# Isolamento, degradação e recuperação

Circuit breaker observa falhas/lentidão de dependência, abre, recusa rapidamente e permite sondas limitadas em half-open. Amostra mínima evita decisão instável com pouco tráfego. Exclua erros de negócio e rejeições locais quando não representam falha remota.

Bulkhead limita concorrência/recursos por dependência ou domínio. Breaker não faz isso sozinho. SemaphoreBulkhead evita um pool extra em execução síncrona; ThreadPoolBulkhead exige fila e tamanho definidos. Use uma instância compartilhada por recurso; múltiplas réplicas somam capacidade.

## Fallback

Escolha alternativa semanticamente válida: recomendação estática, leitura stale com frescor visível, resposta pendente ou indisponibilidade explícita. Não aprovar pagamento, estoque ou autorização sem confirmação. Fallback também tem capacidade e timeout. Cache down → banco irrestrito é cascata, não resiliência.

## Retorno

Limite sondas de breaker, aqueça cache gradualmente, libere produtores e replay em rampa. Autoscaling leva tempo e pode piorar saturação do banco. Durante shutdown, recuse trabalho novo, drene com deadline e preserve mensagem não confirmada.

Teste relatórios saturados com checkout disponível; breaker abre após amostra, não chama remoto aberto e fecha após sonda saudável; fallback preserva semântica; recuperação não gera nova tempestade.

O [teste de proteções](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/ProtecoesTest.java) usa a API Java do Resilience4j sem presumir um starter Spring Boot compatível. Fonte: [CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker) e [Bulkhead](https://resilience4j.readme.io/docs/bulkhead).


## Exemplos executáveis

- [FallbackDegradado](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FallbackDegradado.java):
  último valor conhecido com idade visível, frescor máximo, memória limitada e nenhuma resposta inventada.
- [EncerramentoControlado](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/EncerramentoControlado.java):
  recusa trabalho novo, drena o que está em andamento até um prazo e relata pendências (para não
  confirmar mensagens não processadas).

## Bulkhead com Semaphore × virtual threads

Virtual threads tornam barata a **espera**, mas não limitam a concorrência contra o downstream: com um pool
de conexões de 20 e 10.000 virtual threads, as 9.980 restantes só enfileiram (sem limite) no pool. É o
`Semaphore` (bulkhead) que define quantas chamadas simultâneas a dependência suporta.

```java
// ANTES: virtual thread por requisição, sem teto; relatório lento consome todas as conexões do banco
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    requisicoes.forEach(r -> executor.submit(() -> consultarBanco(r))); // concorrência = nº de requisições
}
```

```java
// DEPOIS: um bulkhead por dependência/criticidade; espera limitada e liberação em finally
private final Semaphore bulkheadRelatorios = new Semaphore(5);   // reserva própria; checkout tem outro
private final Semaphore bulkheadCheckout = new Semaphore(15);

<T> T comBulkhead(Semaphore bulkhead, Duration espera, Callable<T> operacao) throws Exception {
    if (!bulkhead.tryAcquire(espera.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new RejectedExecutionException("bulkhead cheio");   // rejeita cedo, com métrica
    }
    try { return operacao.call(); } finally { bulkhead.release(); }
}

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    requisicoes.forEach(r -> executor.submit(() ->
        comBulkhead(bulkheadRelatorios, Duration.ofMillis(100), () -> consultarBanco(r))));
}
```

Use uma instância por recurso na JVM e some as réplicas ao dimensionar (ex.: 4 réplicas × 5 = 20 permissões
globais). A soma das permissões nunca deve exceder o orçamento de conexões do banco.

### Antes/depois: fallback

```java
// ANTES: fallback inventa sucesso
try { return gateway.autorizar(pedido); } catch (Exception e) { return Autorizacao.aprovada(); }
```

```java
// DEPOIS: indisponibilidade explícita; para leitura, último valor conhecido com idade visível
try { return gateway.autorizar(pedido); }
catch (Exception e) { return Autorizacao.pendente("gateway indisponível"); } // reconciliar depois, nunca aprovar
```

## Provas

- [ControleConcorrenciaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java), em
  `deveLimitarTrabalhoRealComVirtualThreads`: o máximo ativo respeita o limite mesmo com virtual threads;
  também prova liberação após falha e que cancelar a espera não libera operação ainda ativa.
- [ProtecoesTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/ProtecoesTest.java): relatórios saturados
  (`BulkheadFullException`) não consomem a reserva do checkout; breaker abre, recusa e fecha com sonda saudável.
- [FallbackDegradadoTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/FallbackDegradadoTest.java) e
  [EncerramentoControladoTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/EncerramentoControladoTest.java).
