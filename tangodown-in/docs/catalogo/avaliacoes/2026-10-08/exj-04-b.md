# EXJ eval 4 — execução B

Executor independente: `eval_exj_release_4b`. Avaliador: agente principal. Nota: **2/2**.

## Saída bruta

Skill: `monitoramento-java` + agent: `especialista-monitoramento`. No Codex, aplico a skill; o agent só pode ser acionado se estiver instalado no cliente.

Para investigar latência e saturação, correlacione métricas RED com traces e métricas USE:

- **Latência e erros:** acompanhe taxa de requisições, proporção de erros e p95/p99 por rota. Agregue histogramas entre instâncias antes de calcular percentis; não faça média dos p99.
- **Saturação:** observe conexões pendentes e tempo de aquisição do pool, filas e idade do item mais antigo, tarefas ativas versus limite, rejeições, lag de consumidores, heap após GC e pausas.
- **Traces:** propague W3C `traceparent`; compare spans lentos para localizar a etapa que concentra o tempo. Use exemplars para ligar amostras de histogramas a traces. Não use `traceId`, IDs ou caminhos dinâmicos como labels de métricas.
- **Alertas:** use SLO e burn rate para erros e latência; alerte também para sinais de saturação, como conexões pendentes persistentes, fila acima da capacidade ou lag além do SLO. Inclua `for`, severidade e runbook. Limiares precisam refletir o SLO e a capacidade reais do serviço.

Para apontar a causa concreta, preciso de um intervalo de tempo afetado, nome das operações críticas, SLOs e consultas ou gráficos disponíveis de Prometheus e alguns traces representativos, com dados sensíveis removidos. Não preciso de credenciais. Sem esses dados, ainda não dá para determinar se o gargalo está na aplicação, numa dependência ou na infraestrutura.

## Justificativa

Seleciona o encaminhamento esperado e fornece sinais e limites de observabilidade úteis, sem inventar limiares nem pedir acesso/credenciais indisponíveis.
