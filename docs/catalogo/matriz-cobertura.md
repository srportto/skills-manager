# Matriz de cobertura do catálogo

Rastreia cada requisito das fontes de entrada até a skill responsável, o agent dono e a prova. As fontes
originais ficam em `.docs/` (ignorada pelo Git); a síntese versionada abaixo é suficiente para manter o
catálogo em outro checkout.

## Síntese versionada das fontes

**Ementa "Descomplicando o System Design" (módulos M1–M10):** M1 fundamentos (RF/RNF, latência, throughput,
SLA/SLO/SLI, noves, estimativa de tráfego/armazenamento/banda/memória); M2 escala vertical/horizontal,
monólito/microsserviços/serverless, stateless/stateful, redundância e SPOF; M3 DNS (geo/failover),
balanceadores L4/L7 e algoritmos (round-robin, least connections, hashing), gateway/reverse proxy, CDN e edge;
M4 SQL/NoSQL (chave-valor, documento, colunar, grafo), CAP/PACELC, consistência forte/eventual, ACID/BASE,
replicação (leader/follower, multi-leader), particionamento/sharding, índices; M5 cache local/distribuído,
Redis/Memcached, cache-aside/read-through/write-through/write-behind, TTL e evicção (LRU/LFU/FIFO); M6
síncrono/assíncrono, REST/gRPC/GraphQL/WebSocket, EDA com RabbitMQ/SQS/Kafka, garantias at-most/at-least/
exactly-once; M7 rate limiting/throttling (token/leaky bucket), circuit breaker, retry com backoff exponencial
e jitter, bulkhead, fallback, OAuth2/JWT/TLS/mTLS; M8 logs estruturados, métricas, tracing,
Prometheus/Grafana/OpenTelemetry/Jaeger, alertas, health checks e probes; M9 estudos de caso (encurtador,
chat, feed, streaming/e-commerce); M10 roteiro de entrevista em 45 min, trade-offs e armadilhas.

**Documento de backpressure (B1–B2):** B1 — backpressure como controle de fluxo dirigido por demanda
(`request(n)`), estratégias de controlar produtor, buffering, descarte/load shedding e throttling/sampling;
exemplos TCP, poll do Kafka e pool de conexões; padrões complementares rate limiting, load shedding (503),
circuit breaker, bulkhead, fallback/degradação e retry com backoff/jitter. B2 — DLQ, replay e idempotência
para reprocessar sem duplicar efeitos. **Correções incorporadas** (ver plano, seção 6): buffer não resolve
déficit sustentado; `request(n)` só controla a origem se a cadeia coopera; poll do Kafka não reduz a
produção; backoff é exponencial com teto + jitter; virtual threads não aumentam o banco; descarte/sampling
não se aplica a pedidos/pagamentos; cancelar resposta não cancela o efeito remoto; transação no broker não
torna efeitos externos exatamente uma vez.

**Engenharia transversal (ENG):** coesão/acoplamento, SOLID com contexto, DDD, clean code, testes,
contratos, compatibilidade, migração e entrega.

## Matriz

Status: **Coberto** = conteúdo escrito e referenciado; **Provado** = prova executável que passou (ver
[compatibilidade](compatibilidade.md) para a última execução). Links verificados por `validation/java`.

| ID | Requisito | Destino principal | Responsável | Evidência | Status |
|---|---|---|---|---|---|
| M1 | RF/RNF, SLA/SLI/SLO, noves, capacidade | [capacidade-slos](../../skills/design-system-architecture/references/capacidade-slos.md), [nfr-checklist](../../skills/design-system-architecture/references/nfr-checklist.md), [refinamento](../../skills/refinamento-de-historias/SKILL.md) | `arquiteto-sistemas` | Exercício 1.000→800 itens/s (backlog 2.000, 3,91 MiB, drenagem) | Coberto |
| M2 | Escala, monólito modular/microsserviços/serverless, estado, redundância/SPOF | [architecture-patterns](../../skills/design-system-architecture/references/architecture-patterns.md), [cloud-architect](../../skills/cloud-architect/SKILL.md) | `arquiteto-sistemas`, `arquiteto-cloud` | ADR com alternativas e gatilhos ([template](../../skills/design-system-architecture/assets/adr-template.md)) | Coberto |
| M3 | DNS/failover, L4/L7, LB, gateway/proxy, CDN/edge | [rede-trafego](../../skills/design-system-architecture/references/rede-trafego.md), [cloud-architect](../../skills/cloud-architect/SKILL.md), [idempotencia-quotas-http](../../skills/api-rest-design/references/idempotencia-quotas-http.md) | Arquitetura, cloud, API | Tabela de decisões por salto (dono do retry, timeouts, cache) | Coberto |
| M4 | Modelos de dados, CAP/PACELC, consistência, replicação, sharding, índices | [consistencia-distribuida](../../skills/design-system-architecture/references/consistencia-distribuida.md), [database-selection](../../skills/design-system-architecture/references/database-selection.md), [indices](../../skills/banco-de-dados-performance/references/indices.md), [transacoes](../../skills/persistencia-jpa/references/transacoes.md) | Arquitetura, banco | [ProcessadorIdempotenteExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java) (concorrência em PostgreSQL) | Provado (idempotência/concorrência); demais Coberto |
| M5 | Cache local/distribuído, padrões, TTL, evicção | [cache-protecao-java](../../skills/spring-data-redis/references/cache-protecao-java.md) | `java-construtor` | [CacheProtegidoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java), [LimiteDistribuidoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java) | Provado |
| M6 | Síncrono/assíncrono, protocolos, EDA, garantias de entrega | [protocolos-comunicacao](../../skills/design-system-architecture/references/protocolos-comunicacao.md), [sqs-dlq-redrive](../../skills/mensageria-sqs-kafka/references/sqs-dlq-redrive.md), [kafka-produtor-consumidor](../../skills/mensageria-sqs-kafka/references/kafka-produtor-consumidor.md) | API, arquitetura, construtor | [ConsumidorKafkaLimitadoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoExternoIT.java), [ConsumidorSqsLimitadoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java) | Provado |
| M7 | Rate limiting local/global, breaker, retry/backoff/jitter, bulkhead, fallback, OAuth2/JWT/TLS | [capacidade-e-limites](../../skills/resiliencia-controle-fluxo-java/references/capacidade-e-limites.md), [resiliencia-controle-fluxo-java](../../skills/resiliencia-controle-fluxo-java/SKILL.md), [abuso-recursos-quotas](../../skills/seguranca-aplicacao-java/references/abuso-recursos-quotas.md), [autenticacao-jwt](../../skills/seguranca-aplicacao-java/references/autenticacao-jwt.md) | Arquiteto, construtor, revisor, segurança | Testes de `fundamentos`, [ProtecoesTest](../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/ProtecoesTest.java), [LimiteDistribuido](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/LimiteDistribuido.java) e [LimiteDistribuidoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java) | Provado (resiliência e quota distribuída); segurança Coberto |
| M8 | Logs, métricas, tracing, alertas, probes e validação de falhas | [slo-saturacao-java](../../skills/monitoramento-java/references/slo-saturacao-java.md), [logs-estruturados](../../skills/monitoramento-java/references/logs-estruturados.md), [logs-mdc-correlacao](../../skills/monitoramento-java/references/logs-mdc-correlacao.md), [logs-por-camada](../../skills/monitoramento-java/references/logs-por-camada.md), [probes-graceful-shutdown](../../skills/devops-cicd/references/probes-graceful-shutdown.md), [experiment-design](../../skills/chaos-engineer/references/experiment-design.md) | Monitoramento, DevOps, chaos | [MetricasProtecaoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/MetricasProtecaoTest.java), [SaudeAplicacaoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/SaudeAplicacaoTest.java), [ExperimentoCoordenadorLentoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java) — exemplo Java de Toxiproxy no módulo de integração | Provado |
| M9 | Encurtador, chat, feed, e-commerce | [estudos-de-caso-java](../../skills/design-system-architecture/references/estudos-de-caso-java.md) | Arquiteto, construtor, revisor | [CheckoutApplicationTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java), [CheckoutSobCargaSimulationCargaIT](../../examples/java/carga/src/test/java/br/com/srportto/exemplos/CheckoutSobCargaSimulationCargaIT.java) | Provado (checkout); demais estudos Coberto |
| M10 | Roteiro de 45 min, trade-offs, armadilhas | [entrevista-system-design](../../skills/design-system-architecture/references/entrevista-system-design.md) | `arquiteto-sistemas` | Roteiro e rubrica | Coberto |
| B1 | Backpressure, buffering limitado, sinalização ao produtor, load shedding, throttling/sampling | [backpressure-java](../../skills/resiliencia-controle-fluxo-java/references/backpressure-java.md), [capacidade-e-limites](../../skills/resiliencia-controle-fluxo-java/references/capacidade-e-limites.md) | Construtor, revisor | [FluxoSobDemandaTest](../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/FluxoSobDemandaTest.java), [AdmissaoPorPrioridadeTest](../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/AdmissaoPorPrioridadeTest.java), [ConsumidorKafkaLimitadoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoTest.java) | Provado |
| B2 | DLQ, replay, idempotência, descarte explícito | [idempotencia-outbox-replay-java](../../skills/mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md), [controle-consumo-java](../../skills/mensageria-sqs-kafka/references/controle-consumo-java.md) | Construtor, revisor, chaos | [PublicadorOutbox](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/PublicadorOutbox.java) (relay at-least-once) ↔ [ProcessadorIdempotente](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java) (deduplicação no consumidor); [ConsumoControladoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumoControladoTest.java), [PublicadorOutboxTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/PublicadorOutboxTest.java), [ReplayControladoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ReplayControladoTest.java) | Provado |
| ENG | Coesão, SOLID, DDD, clean code, testes, contratos, compatibilidade, migração, entrega | [clean-code-principios](../../skills/qualidade-codigo-java/references/clean-code-principios.md), [checklist-correcao](../../skills/revisao-de-codigo-java/references/checklist-correcao.md), [ddd-tatico](../../skills/arquitetura-limpa-java/references/ddd-tatico.md), [concorrencia-resiliencia](../../skills/testes-sistemas-java/references/concorrencia-resiliencia.md), [sealed-e-switch](../../skills/java-moderno/references/sealed-e-switch.md), [openspec-catalogo-java](../../skills/openspec-catalogo-java/SKILL.md) | Construtor, revisor, refatorador | Contrato de evidência (compilação × testes × pendências) nas skills e agents; validação estrutural; [PagamentoTest](../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java) (exaustividade e dominância compiladas), [OpenSpecIntegracaoTest](../../validation/java/src/test/java/br/com/srportto/catalogo/OpenSpecIntegracaoTest.java) | Coberto |

## Avaliação comportamental dos agents

Pendente — ver [avaliações](avaliacoes-agents.md). Cobertura estrutural não substitui a execução dos casos A01–A12.

## Migração de caminhos e fonte × instalação

- Fonte: `skills/`, `agents/`; instalação típica: `.claude/skills`, `.claude/agents` (ver [convenções](convencoes.md)).
- Referências a `apps/...`, `docs/based-java-aplication.md`, `docs/patterns-arquitetura-java/` e `openspec/changes/` são
  contexto do monorepo de origem, marcadas como externas nas skills.
- Probes: `/disponibilidade` (legado do esqueleto) e `/health/live|ready` foram substituídos por
  `/actuator/health/liveness|readiness`; `/disponibilidade` permanece como smoke test.
