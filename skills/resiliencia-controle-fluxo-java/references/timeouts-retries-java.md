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

## Orçamento de deadline por salto

Cada salto recebe só o que **resta** do orçamento do cliente, menos uma margem; tentativas de retry contam
dentro do mesmo orçamento. Exemplo: cliente com 2 s.

| Salto | Orçamento restante na entrada | Timeout deste salto | Tentativas | Observação |
|---|---|---|---|---|
| Cliente → gateway | 2.000 ms | 2.000 ms (total) | 1 (sem retry no cliente) | Dono do deadline |
| Gateway → serviço | ~1.900 ms | 1.800 ms | até 2, só se sobrar orçamento | Margem de 100 ms para a resposta |
| Serviço → banco | ~1.500 ms | 800 ms por consulta | 1 (efeito não repetido às cegas) | Timeout < restante; libera conexão antes do backoff |
| Serviço → dependência externa | ~1.500 ms | 600 ms | até 2 (backoff 50 ms → 100 ms, teto 200 ms, full jitter) | Soma de timeouts + esperas ≤ restante |

Regras: (1) timeout de cada salto **menor** que o restante da entrada; (2) tentativas e esperas de backoff
descontam do mesmo orçamento — se a espera consumir o restante, encerre antes de dormir; (3) backoff exponencial
com **teto** e **jitter**; (4) uma só camada é dona do retry (se o gateway já repete, o serviço não repete);
(5) repasse o deadline restante ao salto seguinte (prazo restante, não relógio de parede).

```java
// ANTES: sem timeout e retry em cada camada: pior caso 2 s × 3 × 3 = 18 s
var resposta = http.send(requisicao, BodyHandlers.ofString()); // timeout padrão: infinito
```

```java
// DEPOIS: orçamento único; cada salto deriva o timeout do restante e a política conta tentativas dentro dele
var orcamento = new OrcamentoTempo(Duration.ofSeconds(2), System::nanoTime);
var retry = new PoliticaRetry(3, Duration.ofMillis(50), Duration.ofMillis(200),
        ThreadLocalRandom.current()::nextDouble, d -> Thread.sleep(d), () -> quotaRetry.tryAcquire());
String corpo = retry.executar(
        () -> chamadaComDeadline.obter(URI.create("http://servico/itens"), orcamento),
        e -> e instanceof IOException,   // só falha transitória elegível
        orcamento);
```

Provas: [PoliticaRetryTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/PoliticaRetryTest.java) (backoff
exponencial com jitter, teto respeitado, não dorme se a espera consome o deadline, sem retry de erro permanente,
interrupção não vira retry), [OrcamentoTempoTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/OrcamentoTempoTest.java)
e [ChamadaComDeadlineTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ChamadaComDeadlineTest.java) (dependência
lenta não retém o chamador além do prazo; não chama com orçamento esgotado).
