# Controle de consumo em Java (Kafka e SQS)

Objetivo: consumir no ritmo da capacidade real, sem acumular backlog na memória, sem perder o grupo/visibilidade e
sem confirmar trabalho não concluído. O backlog fica **no broker** (retenção/fila), que foi feito para isso.

## Por que o poll não é backpressure

O consumidor Kafka "puxa" registros, então não sofre push do broker. Mas isso **não reduz a produção**: se o
produtor escreve 1.000/s e o grupo processa 100/s, o lag cresce 900/s até a retenção. Puxar no próprio ritmo
protege a JVM do consumidor, não o sistema. Controle de fluxo ponta a ponta exige agir no produtor (429/503,
quotas, pausa) ou aumentar a capacidade do consumo (partições + instâncias, processamento mais barato).

## Kafka: dimensionamento

| Configuração | O que controla | Como escolher |
|---|---|---|
| `max.poll.records` | Registros por `poll()` | Tempo de processamento do lote ≪ `max.poll.interval.ms` |
| `max.poll.interval.ms` | Tempo máximo entre `poll()` | Pior caso do lote + margem; não aumente para esconder lentidão |
| `fetch.max.bytes` / `max.partition.fetch.bytes` | Bytes por fetch | Limita memória do consumidor |
| Partições do tópico | Paralelismo máximo do grupo | Taxa alvo ÷ taxa por consumidor, com folga para crescimento |
| Trabalho em voo (aplicação) | Registros sendo processados ao mesmo tempo | Capacidade do downstream (conexões, quotas) |

Exemplo: cada registro leva 50 ms em média (p99 200 ms). Com `max.poll.records=100` processado sequencialmente, o
lote leva ~5 s (pior caso ~20 s) — bem abaixo dos 5 min. Se o downstream aceita 20 conexões, o trabalho em voo
somado das instâncias não deve passar de 20.

## Kafka: padrão pausa-por-partição

1. A thread do laço chama `poll()` continuamente (mantém o membro vivo).
2. Registros vão para uma fila **por partição**; a partição com trabalho pendente é **pausada** — o próximo `poll()`
   não traz mais nada dela, então o buffer por partição é limitado a um lote.
3. Workers (virtual threads) processam **uma mensagem por partição por vez** (ordem por chave preservada) e no máximo
   `limiteEmVoo` no total.
4. Resultados voltam por fila à thread do laço, que commita `offset + 1` do concluído e retoma partições vazias.
5. Falha transitória: a mesma mensagem é repetida (a partição não avança) com pausa entre tentativas; esgotadas as
   tentativas, vai para a quarentena (DLT). Se a quarentena falhar, **nada é commitado**.
6. `onPartitionsRevoked`: espera limitada pelo trabalho em andamento, commit do concluído; o restante será
   reprocessado pelo novo dono (idempotência obrigatória). `onPartitionsLost`: não commita.

Implementação: [ConsumidorKafkaLimitado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumidorKafkaLimitado.java).
Provas:
[ConsumidorKafkaLimitadoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoTest.java)
(`MockConsumer`: limite em voo, pausa sem parar o poll, commit só do concluído, ordem, DLT indisponível) e
[ConsumidorKafkaLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoExternoIT.java)
(Kafka + PostgreSQL reais: dois consumidores, rebalance, falha depois do efeito e antes do commit, reinício — nenhum
efeito duplicado, nada perdido).

Com spring-kafka, o equivalente declarativo: `concurrency` ≤ partições, `max.poll.records` ajustado, processamento
síncrono no listener (ack após retorno) ou `AckMode.MANUAL` quando houver handoff assíncrono, e
`DefaultErrorHandler` com tentativas limitadas e DLT. `pause`/`resume` do container
(`KafkaListenerEndpointRegistry`) serve para pausar o consumo quando o downstream sinaliza saturação.

## SQS: dimensionamento

| Parâmetro | O que controla | Como escolher |
|---|---|---|
| `MaxNumberOfMessages` (1–10) | Mensagens por `ReceiveMessage` | `min(10, capacidade livre)` |
| `WaitTimeSeconds` (até 20) | Long polling | 10–20 s reduz chamadas vazias e custo |
| `VisibilityTimeout` | Tempo invisível após receber | > tempo típico; renovar para casos longos |
| Mensagens em voo | Recebidas e não apagadas | Capacidade do downstream; o resto fica na fila |
| `maxReceiveCount` (RedrivePolicy) | Tentativas antes da DLQ | Cada recebimento conta, inclusive expirados |

Reentrega com atraso: em vez de esperar o timeout inteiro, `ChangeMessageVisibility` com um atraso crescente
(ex.: por `ApproximateReceiveCount`) devolve a mensagem para nova tentativa sem martelar a dependência.

Implementação: [ConsumidorSqsLimitado](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumidorSqsLimitado.java).
Provas:
[ConsumidorSqsLimitadoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoTest.java)
(nunca pede além da capacidade, apaga só após o efeito, renova visibilidade e evita entrega duplicada) e
[ConsumidorSqsLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java)
(LocalStack: mensagem que sempre falha chega à DLQ após 3 recebimentos; as demais são apagadas).

## Métricas mínimas

Lag por partição (Kafka) e idade da mensagem mais antiga (`ApproximateAgeOfOldestMessage` no SQS) — **idade** diz
mais que contagem para SLO. Trabalho em voo, tempo de processamento, tentativas, mensagens em quarentena e taxa
de commit/delete. Alerta por idade do backlog e por crescimento contínuo da DLQ (ver `monitoramento-java`).

## Autoscaling por backlog

Escalar consumidores pelo lag/idade ajuda até o limite de partições (Kafka) e da **capacidade do downstream**:
dobrar consumidores dobra a pressão no banco. Combine escala com o orçamento de conexões e mantenha o limite de
trabalho em voo por instância.
