# Concorrência e resiliência

Use CountDownLatch/CyclicBarrier e timeout de segurança para prender trabalhos ativos antes de lançar excedentes. Meça máximo ativo com AtomicInteger; não infira limite só pelo tamanho de Semaphore. Garanta liberação de latches/executors em finally, inclusive quando a asserção falhar.

Para Reactor, StepVerifier começa com demanda zero, thenRequest em lotes e thenCancel. Use tempo virtual para temporizadores; construir publisher temporizado dentro do supplier do teste virtual. Teste fonte não regulável e política de overflow separadamente.

Retry: relógio monotônico e sleeper injetados; valores literais esperados para tentativas/esperas. Testar interrupção, falha não elegível, orçamento vazio e deadline. Não verificar apenas que um mock foi chamado.

Idempotência: duas conexões/consumidores disputam a mesma chave; reinício usa o mesmo datastore; mesmo payload retorna mesmo resultado, diferente payload conflita. Efeito e registro devem participar da mesma transação quando possível. Falha depois do commit antes do ack precisa preservar efeito único.

Cancelamento: não liberar capacidade de trabalho ainda ativo porque o chamador deixou de esperar. Concluir ou interromper efetivamente a operação e só então devolver recursos.

Veja [testes fundamentos](../../../examples/java/fundamentos/pom.xml) e [integração](../../../examples/java/integracao/pom.xml).


## Provas de referência

| Comportamento | Teste |
|---|---|
| Máximo ativo com virtual threads; liberação após falha; cancelamento não libera trabalho ativo | [ControleConcorrenciaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java) |
| Backoff exponencial com teto e jitter, deadline, quota agregada, interrupção | [PoliticaRetryTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/PoliticaRetryTest.java) |
| Demanda zero, lotes, cancelamento; buffer limitado com tempo virtual | [FluxoSobDemandaTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/FluxoSobDemandaTest.java) |
| Reserva crítica e rejeição por deadline | [AdmissaoPorPrioridadeTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/AdmissaoPorPrioridadeTest.java) |
| Drenagem no encerramento | [EncerramentoControladoTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/EncerramentoControladoTest.java) |

**Mutação como prova do teste:** quando o teste for escrito depois do código (código herdado), quebre a
linha que implementa o invariante (remova o `release()`, o teto do backoff, o limite de bytes) e confirme
que o teste falha antes de confiar nele.
