# Decisões de execução — evolução de skills e agents Java (2026-10-06)

Registro versionado das decisões tomadas durante a execução do
[plano](2026-10-06-evolucao-skills-agents-java.md). Cada decisão traz o motivo e, quando relevante, o custo se
estiver errada. Versões e resultados de execução estão em
[compatibilidade](../../catalogo/compatibilidade.md).

## Estado encontrado ao retomar

Uma execução anterior havia avançado parcialmente em T01–T08, mas o registro de progresso indicava apenas
"T01 em andamento". Builds verdes (17 testes). Problemas encontrados:

- As 5 referências existentes de `design-system-architecture` tinham sido **enxugadas** (−520 linhas) em vez de
  ampliadas; ADR e system design perderam o template preenchível.
- T02 só tinha `CatalogoEstruturaTest`; T05 concentrava testes em `FundamentosTest`; faltavam exemplos de
  throttling, load shedding, fallback e timeout HTTP; T09–T13 não iniciadas.
- Links quebrados (`examples/java/README.md`, `controle-consumo-java.md`, `CheckoutApplication.java`).

## Decisões gerais

| Decisão | Motivo | Custo se errada |
|---|---|---|
| Nenhum commit durante a execução | Branch `main` com alterações preexistentes do usuário; commit exige consentimento | Usuário agrupa e commita o lote manualmente |
| Revisão final feita pelo próprio executor | O ambiente só permite subagents com pedido explícito | Revisão mais fraca que um revisor independente |
| Testes de T02 executados antes de T01 | Serviram de RED para as correções de T01 | Nenhum (só ordem) |
| Testes locais determinísticos separados dos testes com serviços reais | Ausência de Docker não pode virar aprovação | — |
| A IDE compila em `target/` concorrentemente | Comandos de verificação usam `clean` | — |

## Por tarefa

### T01–T02 — descoberta, rastreabilidade e validação

- Criadas `padroes-de-projeto-java/references/{criacionais,estruturais,comportamentais}.md` (links quebrados
  preexistentes); Mediator reclassificado como comportamental. *Custo: conteúdo resumido em relação ao original perdido.*
- `related-skills` que apontavam o agent `refatorador-java` trocados por `refactoring-remove-parameter`.
- Blocos Python/TypeScript/Groovy em referências importadas registrados como baseline (`EXCECOES_LEGADO` em
  `ExemplosJavaTest`); o teste obriga a remover a exceção quando o arquivo for reescrito.
- Links para alvos de tarefas futuras ficaram em crase até o alvo existir (restaurados em T06/T12).

### T03 — capacidade e orçamento

- `design-system-architecture/SKILL.md` reescrita já com o escopo de T07 (consistência, rede, protocolos,
  correção da ADR sobre MongoDB) para não editar o arquivo duas vezes.
- `refinamento-de-historias` generalizado nesta tarefa (também item de T10): pressupostos do monorepo de origem
  movidos para `references/perfil-exemplo-autorizacoes.md`; novo eixo de carga/limites/recuperação e DoR.

### T04–T05 — proteções e exemplos Java

- T04 e T05 validadas juntas: a tabela de mecanismos da T04 aponta para exemplos criados na T05.
- `java-moderno` corrigido para Java 25 (JEP 491: `synchronized`/`Object.wait` não fazem pinning; resta
  nativo/FFM) e "virtual threads não aumentam capacidade".
- Código herdado mantido; `FundamentosTest` dividido em `<Classe>Test`; provas validadas por **mutação** (7/7
  mutantes mortos no herdado, 7/7 nas classes novas).
- Acrescentadas `LeakyBucket`, `AdmissaoPorPrioridade`, `ChamadaComDeadline`, `FallbackDegradado`,
  `EncerramentoControlado` — linhas da seção 6 do plano sem exemplo executável. *Custo: mais 5 classes a manter.*

### T06 — mensageria

- `ConsumoControlado` passou a devolver decisão (CONFIRMAR/REENTREGAR) e expor `quarentenar()`.
- Criados `ConsumidorKafkaLimitado` e `ConsumidorSqsLimitado`: os critérios de aceite (dois consumidores,
  reinício, DLT) exigiam consumidores reais.
- **Bug herdado corrigido:** a outbox ordenava por UUID (ordem aleatória) → coluna `seq` IDENTITY.
- `localstack/localstack:latest` (2026.7) exige licença ("License activation failed") → imagem fixa `4.4`.
  *Custo: versão antiga do emulador.*
- Frontmatter com `---` duplicado em 15 skills da trilha corrigido, com teste dedicado (ferramentas auxiliares
  excluídas para não sofrerem alteração incidental).
- Mutação: Kafka 4/4, `ConsumoControlado` 2/2, SQS 4/4. Um sobrevivente inicial levou a fila SQS falsa a
  validar `MaxNumberOfMessages` 1..10 como o serviço real.

### T07 — system design e protocolos

- Blocos Python/TypeScript de `cloud-architect` (`cost.md`, `multi-cloud.md`) convertidos para Java; exemplo de
  interrupção spot migrado de IMDSv1 para IMDSv2.
- `cloud-architect` deixou de exigir "99,9%+" e multi-região como regra universal.
- `api-rest-design` mapeia 413/429/502/503/504, quotas e `Idempotency-Key`.

### T08 — cache, banco e consistência

- `LimiteDistribuido`: script Lua INCR+PEXPIRE com `PTTL < 0` (corrige chave órfã sem TTL) e limite local
  conservador quando o coordenador falha — nem fail-open, nem fail-closed.
- `RecuperacaoPendencias` com Jedis e `XAUTOCLAIM` nativo (nem todas as versões do `StreamOperations` expõem o comando).
- Jedis na versão gerenciada pelo BOM do Boot (7.0.0); havia duas versões no reactor.
- **Erros de documentação corrigidos:** `effective_cache_size` default é 4 GB (não 4 MB);
  `innodb_log_file_size` → `innodb_redo_log_capacity` (8.0.30+); `work_mem` fixo → fórmula por conexão/operação;
  catch de `OptimisticLockingFailureException` dentro do `@Transactional` não pega o conflito do flush;
  conflito de concorrência 422 → 409; `existsBy` + `save` era check-then-act.

### T09 — observabilidade e recuperação

- Semântica única de probes: liveness = estado do processo; readiness = estado + dependências necessárias;
  saturação em grupo operacional (alerta), **fora** da readiness — provado em `SaudeAplicacaoTest`.
  `/disponibilidade` preservado como smoke test.
- `monitoramento-java`: `traceId` fora de labels (exemplars), sampling como decisão, nomes Micrometer com
  ponto, alerta por burn rate.
- `padrao-de-logs-java` prioriza Micrometer Tracing/W3C e valida `X-Trace-Id` recebido.
- `experiment-design.md` reescrito em Java, com experimento executável (Toxiproxy + Valkey). As outras 4
  referências de chaos permanecem no baseline (~900 linhas Python importadas).

### T10 — fluxo de engenharia

- Exemplos longos movidos sem reescrita: `qualidade-codigo-java` 979→397 linhas, `revisao-de-codigo-java`
  819→532; heurísticas de estilo tratadas como Menor, não bug.
- Revisão ganhou tabela de resiliência/efeitos por severidade, bloco de evidência e veredicto PENDENTE.
- `criar-aplicacao-java` pergunta só nome e variante; demais parâmetros com default declarado; tabela de
  proteções e provas por variante; `-DskipTests` não é prova.
- `seguranca-aplicacao-java` ganhou seção de abuso de recursos (OWASP API4:2023).

### T11 — agents

- 11 agents reescritos com o contrato (resolução das skills, entradas, fluxo, entregas e evidências,
  fronteiras); modelo, ferramentas e permissões preservados.
- `java-revisor` com veredicto PENDENTE e sem `-DskipTests` na auditoria; `projetista-api` valida contrato com
  testes Java (linters externos opcionais).
- **Avaliação comportamental A01–A12 não executada** — exige invocar os agents em sessões independentes.
  *Custo: não há medida da qualidade das respostas, só da estrutura.*

### T12 — caso integrado e carga

- `CheckoutApplication` como único caso executável; pagamento simulado com latência configurável só para
  laboratório; tenant em header só no exemplo.
- `ProcessadorIdempotente.ConflitoDeChave` para distinguir 409 de 422.
- `HttpStatus.UNPROCESSABLE_ENTITY` (deprecado no Spring 7) → `UNPROCESSABLE_CONTENT`.
- Gerador de carga Java próprio (malha aberta, latência desde o instante planejado) em vez de Gatling.
- Depuração: a primeira execução do ensaio não rejeitou nada porque `setDefaultProperties` tem precedência
  menor que o `application.yaml`; corrigido com argumentos de linha de comando.

### T13 — consolidação

- Workflow `validar-catalogo.yml`: catálogo → exemplos → integração (obrigatória quando a mudança toca
  broker/persistência/cache) → carga (só agendada/manual).
- Removido do README dos exemplos o comando `spring-boot:run` (não verificado).
- Caixas do plano marcadas; ficam abertas a avaliação A01–A12 e o critério de trechos parciais com fonte
  executável (Pulumi/gRPC/Pub-Sub ilustrativos).

## Revisão final

- **Corrigido:** em `ConsumidorKafkaLimitado`, uma conclusão atrasada após revogação e reatribuição da partição
  ao mesmo membro "consumia" o registro reentregue sem processá-lo (perda de mensagem). Teste
  `conclusaoAtrasadaDepoisDaRevogacaoNaoDevePularMensagemQuandoAParticaoVolta` falhou antes e passa depois;
  prazo de revogação passou a ser configurável.

**Melhorias menores adiadas:**

- `ConsumidorSqsLimitado` adiciona a conclusão antes de cancelar a renovação de visibilidade (a renovação pode
  disparar após o delete), e uma exceção na renovação interrompe as seguintes sem log.
- `ChamadaComDeadline` lê o orçamento duas vezes; se zerar entre a checagem e o timeout, lança
  `IllegalArgumentException` em vez de `TimeoutException`.
- `CacheProtegido` rejeita chaves diferentes que colidem na mesma faixa de lock durante recomputação.
- `MetricasProtecao` registra o timer a cada requisição.

## Pendências

- Avaliação comportamental A01–A12 dos agents.
- Conversão para Java dos scripts importados em 4 referências de `chaos-engineer`.
- Primeira execução do workflow no GitHub Actions.
