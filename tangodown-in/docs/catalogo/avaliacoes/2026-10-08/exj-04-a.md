# EXJ eval 4 — execução A

Executor independente: `eval_exj_release_4a`. Avaliador: agente principal. Nota: **2/2**.

## Saída bruta

Skill: `monitoramento-java` + agent: `especialista-monitoramento`; no Codex, aplico a skill diretamente, e o agent só pode ser acionado se estiver instalado no cliente.

Para investigar sem presumir a causa, preciso do intervalo afetado e dos dados desse período: p95/p99 e taxa de requisições por rota, erros, CPU/GC, espera e timeouts do pool de conexões, e fila ou lag, se houver. Envie também um trace lento representativo e mudanças ou deploys recentes.

Vou cruzar latência e erros (RED) com saturação (USE), seguindo a requisição pelos spans para localizar a etapa lenta. Depois disso, posso apontar a causa provável e sugerir alertas ligados ao SLO, com janela e duração que evitem disparos por picos breves. Sem esses dados, ainda não dá para concluir onde está o gargalo.

## Justificativa

Seleciona a skill e o agent de observabilidade, pede evidências pertinentes e não conclui causa sem dados.
