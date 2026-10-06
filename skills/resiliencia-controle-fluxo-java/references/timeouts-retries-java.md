# Deadline, retry e recuperação de efeito desconhecido

Estabeleça deadline ponta a ponta. Divida o restante entre espera por conexão, conexão de rede, leitura/resposta, processamento e tentativas. Use System.nanoTime para tempo decorrido dentro do processo; deadlines propagados entre máquinas precisam de contrato que considere relógios distintos.

Timeout da resposta não equivale a rollback remoto. Para cobrança, registre chave idempotente, payload e resultado, e consulte/reconcilie a operação antes de repetir efeito desconhecido. Erro de negócio, payload inválido, cancelamento e interrupção não devem ser repetidos indiscriminadamente.

## Política

maxAttempts inclui a chamada inicial. Para tentativa de índice n começando em zero, teto da espera = min(maxDelay, baseDelay × 2^n), com cálculo protegido contra overflow. Full jitter sorteia entre zero e esse teto. Limite número e duração; se a espera consome o deadline, encerre antes de dormir. Honre Retry-After quando couber no orçamento.

Uma camada é responsável pelo retry lógico. Identifique retentativas automáticas de SDK, cliente HTTP, gateway e broker: três camadas com três tentativas podem gerar 27 chamadas. Use orçamento agregado (por exemplo, token bucket de retries por dependência) para evitar amplificação mesmo quando cada chamada respeita seu limite.

## Composição

Admissão por operação lógica; tentativa elegível passa por breaker/bulkhead; timeout limita o I/O; recurso é liberado antes do backoff. A ordem exata depende do que deve contar como falha e de quando medir a latência. Não trate RejectedExecutionException local como evidência automática de falha remota.

[PoliticaRetry.java](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/PoliticaRetry.java) injeta tempo, espera e jitter. [OrcamentoTempo.java](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/OrcamentoTempo.java) limita o tempo decorrido. A política não interrompe Callable arbitrário: configure timeout/cancelamento no cliente utilizado.

Teste transitório seguido de sucesso; falha permanente uma vez; interrupção; deadline esgotado; orçamento agregado vazio; ausência de retry após sucesso. Fonte: [retries nos SDKs AWS](https://docs.aws.amazon.com/sdkref/latest/guide/feature-retry-behavior.html).


## Cliente HTTP com deadline

[ChamadaComDeadline](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ChamadaComDeadline.java)
deriva o timeout da requisição do orçamento restante e não inicia I/O sem tempo disponível. O teste prova
que o chamador é liberado no prazo **e** que o servidor continua processando — por isso efeitos remotos
exigem idempotência e reconciliação.
