# Pontuação comportamental — rodada 2

Pontuação independente das saídas da rodada 2, pela rubrica de `docs/catalogo/avaliacoes-agents.md`. As citações abaixo são trechos das respostas avaliadas; as notas de A05 foram preservadas da rodada 1 conforme o ruling do protocolo.

## Notas por execução

| Caso | Execução 1 | Razão sucinta e evidência | Execução 2 | Razão sucinta e evidência |
|---|---:|---|---:|---|
| A01 | 1 | Identifica déficit de 900/s, estima backlog e armazenamento e define admissão limitada, mas não calcula quanto tempo levaria para drenar um backlog acumulado. A resposta assume “taxa sustentada de entrada” e dá “3,24 GB/hora ou 77,76 GB/dia”; falta a prova de drenagem exigida. [Saída](A01-1.md) | 1 | Dimensiona crescimento e bytes e afirma que a capacidade precisa “superar 1.000/s” para recuperar backlog, mas não estima o tempo de drenagem em função do backlog e da capacidade excedente. [Saída](A01-2.md) |
| A02 | 2 | Explica que threads virtuais “não aumentam as conexões disponíveis”, limita concorrência a 10, rejeita excedente sem fila ilimitada e explicita ajuste para orçamento somado das réplicas; propõe prova concorrente. [Saída](A02-1.md) | 2 | Limita acesso ao banco antes da transação, define espera de 200 ms e 503, inclui timeout do pool e condiciona o tamanho por instância ao orçamento global; descreve validação de concorrência e liberação após falha. [Saída](A02-2.md) |
| A03 | 2 | Trata timeout como resultado desconhecido: consulta pela mesma chave, só tenta novamente se ausência for conclusiva e mantém estado pendente quando a consulta é inconclusiva; lista provas para cada caminho. [Saída](A03-1.md) | 2 | Persiste chave, payload e estado antes da chamada; bloqueia nova cobrança enquanto desconhecida, limita retries/deadline e pede verificação do contrato do provedor. [Saída](A03-2.md) |
| A04 | 2 | Mantém `poll()` ativo durante a pausa, limita trabalho em voo e comita apenas offsets concluídos em sequência depois do efeito durável. [Saída](A04-1.md) | 2 | Repete a política segura e cobre rebalance: “o restante poderá ser reprocessado” com efeito idempotente, sem confirmar trabalho pendente. [Saída](A04-2.md) |
| A05 | 2 | Nota mantida da rodada 1, sem reavaliar as saídas nesta rodada. A pontuação anterior registra single-flight, limite ao banco, rejeição/degradação e provas. [Pontuação da rodada 1](../2026-10-07/pontuacao.md) | 2 | Nota mantida da rodada 1, sem reavaliar as saídas nesta rodada. A pontuação anterior registra single-flight, limite ao banco, rejeição/degradação e provas. [Pontuação da rodada 1](../2026-10-07/pontuacao.md) |
| A06 | 2 | Rejeita liveness dependente de serviço externo, separa diagnóstico e define comportamento degradado por rota, com respostas e estados esperados verificáveis. [Saída](A06-1.md) | 2 | Mantém liveness e readiness separadas da dependência compartilhada, prevê exceção para dependência local e descreve 503 apenas nas rotas que precisam dela. [Saída](A06-2.md) |
| A07 | 2 | Dá veredicto “PENDENTE”, distingue build de prova comportamental e exige evidências de concorrência, falha e recuperação com comandos e `Skipped: 0`. [Saída](A07-1.md) | 2 | Também mantém PENDENTE, explicita a falta de contagem/execução e lista comandos e evidência requeridos para as três famílias. [Saída](A07-2.md) |
| A08 | 2 | Usa identidade autenticada e coordenador compartilhado com decisão/expiração atômicas; define 429 e fallback local conservador com o pior caso agregado explícito. [Saída](A08-1.md) | 2 | Rejeita identidade por header livre e `INCR`/`EXPIRE` separado; descreve atomicidade, 429 e escolhas fail-closed ou limite local com limite agregado conhecido. [Saída](A08-2.md) |
| A09 | 1 | Não executa refatoração nem testes antes/depois, mas evita alegar preservação sem cobertura e enumera ordem, commit, idempotência e cancelamento/revogação a caracterizar. [Saída](A09-1.md) | 1 | Propõe um Extract Method delimitado e detalha invariantes e testes faltantes, mas deixa a mudança e a prova antes/depois pendentes. [Saída](A09-2.md) |
| A10 | 2 | Segue agenda de 45 minutos, estima capacidade, apresenta exemplos Java, limites por conexão, recuperação por sequência e ensaios de falha. Trata números como hipóteses a validar. [Saída](A10-1.md) | 2 | Organiza explicitamente os blocos 0–45 min, traz capacidade, trechos Java, reconexão com resync, filas limitadas, trade-offs e provas operacionais. [Saída](A10-2.md) |
| A11 | 2 | Recomenda MVC + banco para baixo tráfego e rejeita broker/WebFlux/Redis sem motivo; define paginação, teto de pool/timeout e validação de escala. [Saída](A11-1.md) | 2 | Mantém solução simples, com paginação, payload de 1 MB, timeout, credenciais fora do repositório e orçamento do pool por instâncias. [Saída](A11-2.md) |
| A12 | 2 | Recusa `traceId`/ID do pedido como tags, separa métricas agregadas de logs/traces e lista validações do scrape, correlação e séries. [Saída](A12-1.md) | 2 | Define labels de conjunto fechado, exemplars para correlação sem cardinalidade, consultas PromQL e validação de labels e traces. [Saída](A12-2.md) |

## Totais e critérios da meta

| Critério | Resultado | Situação |
|---|---:|---|
| Total original por execução, antes do reteste de A01 | 22/24 em ambas | Registro histórico preservado; A01 recebeu 1/1 |
| Total original combinado, antes do reteste de A01 | 44/48 | Registro histórico preservado |
| Total final por execução, substituindo A01 pela respectiva nota do reteste | 23/24 em ambas | Atinge o mínimo de 22/24 |
| Total final combinado, substituindo A01 pelas notas do reteste | 46/48 | Atinge o mínimo de 44/48 |
| Notas zero | 0 | Atinge a meta de nenhum zero |
| Notas 2 em A01–A08 nas duas execuções, considerando o reteste de A01 | 8 de 8 casos; reteste A01 2/2 | **Atinge**; a nota original de A01 (1/1) permanece registrada acima |

## Diagnóstico

As respostas demonstram boas decisões e limites em fluxo de dados, concorrência, idempotência, Kafka, probes, revisão de resiliência, quota distribuída e métricas. A pontuação original de A01 (1/1) foi mantida como histórico; no reteste, ambas quantificam a drenagem e atendem ao resultado obrigatório, levando A01 a 2/2 no cálculo final atualizado. Com essa substituição explícita, a meta específica A01–A08 é atingida. A09 permanece em 1 nas duas execuções porque reconhece corretamente os invariantes e a ausência de provas, mas não entrega a refatoração nem testes antes/depois solicitados.

## Resumo de A05

| Caso | Rodada 1, execução 1 | Rodada 1, execução 2 | Rodada 2 |
|---|---:|---:|---|
| A05 | 2 | 2 | Mantidas em 2/2, conforme ruling de que A05 não foi afetado; respostas não reavaliadas nesta rodada. |

## Reteste de A01

| Saída | Nota | Justificativa |
|---|---:|---|
| [Reteste execução 1](A01-1-reteste.md) | 2 | Assume explicitamente pico de 60 s, calcula déficit de 900/s e backlog de 54.000, dimensiona bytes a partir do payload medido e calcula drenagem de 18 min com entrada pós-pico de 50/s. Também explicita que com entrada de 100/s não há drenagem líquida, limita a fila por itens/bytes/idade, define pausa ou rejeição e propõe ensaios para validar capacidade e hipóteses. |
| [Reteste execução 2](A01-2-reteste.md) | 2 | Assume explicitamente pico de 10 s, calcula backlog de 9.000 e mostra drenagem de 90 s com entrada zero ou 180 s com entrada de 50/s, além de ausência de progresso com entrada de 100/s. Limita o buffer/retention, define pausa, rejeição e escala condicionada à capacidade medida, e lista métricas e ensaios verificáveis. |

As notas originais de A01 na rodada 2 permanecem registradas como 1/1 na tabela acima. Para o resultado final da amostra, o reteste independente substitui essas notas no cálculo de A01; não altera as demais notas.
