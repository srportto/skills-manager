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
