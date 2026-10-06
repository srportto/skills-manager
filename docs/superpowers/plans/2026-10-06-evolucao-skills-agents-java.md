# Plano de melhoria das skills e agents — engenharia de software e system design com Java

> **Orientação para execução:** implementar por tarefa, usando `superpowers:executing-plans` ou, quando houver escolha explícita por delegação, `superpowers:subagent-driven-development`. As caixas abaixo acompanham a execução futura. Este documento entrega o planejamento solicitado; não executa as alterações propostas.

**Objetivo:** tornar o catálogo capaz de orientar, construir e revisar sistemas Java com decisões arquiteturais justificadas, proteção contra sobrecarga e falhas, e evidências de funcionamento.

**Arquitetura do catálogo:** manter os 11 agents especializados; ampliar as skills existentes; adicionar apenas duas skills transversais, `resiliencia-controle-fluxo-java` e `testes-sistemas-java`. Cada conceito terá uma referência principal, consumida pelas demais skills sem copiar regras completas.

**Stack de referência:** linguagem Java 25; Spring Boot 4 nos exemplos de aplicação; APIs do JDK para fundamentos; JUnit Jupiter para testes; Maven para exemplos executáveis. Reactor, Resilience4j, Micrometer, Testcontainers e clientes Java de banco/brokers entram somente nos exemplos que precisarem deles. Fixar versões compatíveis e comprovadas por build na execução, sem presumir compatibilidade de starters.

**Especificação de entrada:** pedido do usuário; exigência adicional de todos os exemplos de programação em Java; `.docs/topicos-estudo-engenharia-software-e-system-design.md`; `.docs/backpressure e outros conceitos para proteger sistemas.md`.

**Data da análise:** 06/10/2026.

## 1. Restrições gerais

- Explicações, comentários de código, critérios de aceite e relatórios em português.
- Todo exemplo novo ou reescrito de programação será Java, incluindo testes, clientes HTTP, cargas e injeção de falhas. Não apresentar soluções em Python, JavaScript, TypeScript, Go ou Kotlin.
- YAML, XML, Terraform e diagramas são formatos de configuração/documentação já utilizados pelo catálogo. Não converter esses formatos artificialmente para Java. Neste plano, os únicos blocos de código de exemplo são Java.
- O foco Java não implica apagar `python-pro`, OpenSpec, Graphify ou ferramentas existentes. Conteúdo auxiliar fica identificado separadamente e não participa da trilha de exemplos Java. Remover referências indevidas a Python de fluxos especificamente Java.
- Preservar Java 25 e Spring Boot 4 como base declarada, sem migração de versões ou uso de funcionalidades preview por conveniência.
- Valores de limite, timeout, retry e SLO devem trazer unidade, escopo e justificativa. Números de laboratório não se tornam padrões universais de produção.
- Não alterar a modificação preexistente em `.gitignore`. `.docs` é insumo local ignorado pelo Git; por isso este plano está em `docs/superpowers/plans/`.
- Na futura execução, o material versionado deve conter uma síntese dos requisitos de `.docs`, sem depender da presença dos arquivos ignorados em outro checkout.
- Não instalar o catálogo globalmente, publicar, provisionar infraestrutura nem executar chaos em produção como parte desta melhoria documental.
- Modelos, permissões e ferramentas de agents dependem do executor. Não substituir automaticamente os metadados existentes por nomes de modelos ou capacidades presumidas.

## 2. Diagnóstico do diretório

Foram inventariadas **31 skills, 11 agents, 5 comandos OpenSpec e 2 documentos em `.docs`**. A análise combinou leitura integral dos dois documentos-base e dos agents, leitura das skills centrais e busca transversal de conceitos. Trata-se de diagnóstico para planejamento, não de certificação de todos os exemplos existentes.

| Evidência local | Consequência | Ação planejada |
|---|---|---|
| `skills/README.md` e os agents usam `.claude/skills` e `.claude/agents`, enquanto este repositório contém `skills/` e `agents/` | Confusão entre fonte do catálogo e destino de instalação | Definir ambos os contextos e resolução de referências; não fazer substituição cega |
| README enumera 25 skills e 5 OpenSpec; o inventário atual contém 31, incluindo Graphify | Catálogo e descoberta podem ficar desatualizados | Atualizar índice e validar contagem automaticamente |
| Busca por backpressure encontra orientação breve em `java-architecture`, sem referência operacional dedicada | Agentes podem recomendar filas ou concorrência sem explicar limites | Criar referência transversal com cenários síncronos, reativos e de mensageria |
| Não foram encontrados tratamentos explícitos de CAP/PACELC, Lei de Little e load shedding nas buscas temáticas do catálogo | Cobertura incompleta dos módulos 1, 4 e 7 | Adicionar referências com decisões e exercícios mensuráveis |
| `design-system-architecture/references/system-design.md` usa Node.js/Express/NestJS | Não atende à exigência Java | Reescrever o exemplo de aplicação em Java/Spring Boot |
| `agents/java-construtor.md` inclui Lambda Python; `refinamento-de-historias` contém pressupostos de outro monorepo | O catálogo genérico pode produzir tarefas ou caminhos inexistentes | Separar contexto específico e restringir a trilha Java |
| `mensageria-sqs-kafka` já cobre DLQ, idempotência e erro central, mas simplifica ack, offset, tentativas e garantias de entrega | Risco de perda, duplicação ou entendimento incorreto em falhas | Reescrever as garantias por tecnologia, modo e configuração |
| `spring-data-redis` faz incremento e expiração do rate limiter em operações separadas | Uma interrupção entre operações deixa política incompleta | Documentar atomicidade e testar falha parcial com cliente Java |
| `spring-data-redis` comenta XAUTOCLAIM, mas mostra chamada a `pending` | Inspeção de pendências pode ser confundida com recuperação | Corrigir o exemplo usando a API Java compatível e testar reclaim |
| DevOps aponta as duas probes para `/disponibilidade`; arquitetura usa `/health/live` e `/health/ready`; monitoramento usa Actuator | Contratos de saúde divergentes entre agentes | Estabelecer semântica e configuração única; preservar endpoint legado quando necessário |
| `java-construtor` admite pular testes e `java-revisor` aceita build mínimo com testes pulados | Compilação pode ser confundida com validação funcional | Separar status de compilação, testes executados e validações pendentes |
| `qualidade-codigo-java` tem 979 linhas e `revisao-de-codigo-java`, 819 | Conteúdo de ensino duplicado aumenta custo e divergência | Manter checklist conciso e mover exemplos extensos para referências |
| Não há módulo de exemplos Java nem suíte de validação do catálogo no inventário | Alterações de documentação podem perpetuar exemplos inválidos | Criar validação estrutural e exemplos Java executáveis |

Também devem ser auditadas afirmações absolutas: tuning de banco com percentuais fixos, sampling invariável, índices PostgreSQL aplicados indistintamente a MySQL, fallback irrestrito para banco e recomendações antigas de virtual threads. A auditoria deverá distinguir erro técnico, simplificação didática e convenção legítima.

Não existe `graphify-out/graph.json` neste checkout. A rastreabilidade deste plano usa os arquivos diretamente; gerar um grafo não é pré-requisito para executá-lo.

## 3. Abordagem escolhida e alternativas

| Abordagem | Benefício | Custo/limitação | Decisão |
|---|---|---|---|
| Apenas acrescentar tópicos aos arquivos atuais | Poucas mudanças estruturais | Mantém duplicações e mecanismos de proteção dispersos | Insuficiente |
| Ampliar skills existentes e criar duas referências transversais | Reutiliza especialidades e permite ensinar/testar proteções de forma consistente | Exige atualizar referências e responsabilidades | **Recomendada** |
| Criar uma skill e um agent para cada módulo/conceito | Granularidade máxima | Aumenta roteamento, contexto e sobreposição | Adiar; só justificar diante de demanda real |

As skills novas não absorvem os detalhes de outras especialidades. Resiliência explica decisões e composição de proteções; mensageria continua dona de ack, offset, DLQ e replay; monitoramento continua dono da instrumentação; testes define como comprovar o comportamento.

## 4. Matriz de cobertura dos documentos-base

**M** identifica o módulo da ementa. **B** identifica o documento de backpressure.

| Origem | Conteúdo obrigatório | Destino principal | Responsabilidade | Evidência esperada |
|---|---|---|---|---|
| M1 | RF/RNF, SLA/SLI/SLO, disponibilidade, capacidade, tráfego, armazenamento e memória | `design-system-architecture`, `refinamento-de-historias` | `arquiteto-sistemas` | Capacidade calculada e RNFs verificáveis |
| M2 | Escala vertical/horizontal, monólito modular, microsserviços, serverless, stateful/stateless, redundância e SPOF | `design-system-architecture`, `arquitetura-limpa-java`, `cloud-architect` | `arquiteto-sistemas`, `cloud-architect` | ADR com alternativas, custos e falhas |
| M3 | DNS, failover, L4/L7, algoritmos de balanceamento, gateway, proxy, CDN e edge | `design-system-architecture`, `cloud-architect`, `api-rest-design` | Arquitetura, cloud e API | Fluxo de tráfego, confiança de headers e política de cache |
| M4 | SQL/NoSQL, chave-valor/documento/colunar/grafo, CAP/PACELC, consistência, ACID/BASE, replicação, sharding e índices | `design-system-architecture`, `banco-de-dados-performance`, `persistencia-jpa` | Arquitetura e banco | Decisão de dados por operação e teste de concorrência/falha |
| M5 | Cache local/distribuído, Redis/Memcached, cache-aside/read-through/write-through/write-behind, TTL e eviction | `spring-data-redis`, `design-system-architecture` | Construtor e arquitetura | Exemplo Java de cache limitado, invalidação e falha |
| M6 | Síncrono/assíncrono, REST/gRPC/GraphQL/WebSocket, EDA, RabbitMQ/SQS/Kafka e garantias de entrega | `api-rest-design`, `mensageria-sqs-kafka`, `design-system-architecture` | API, arquitetura e construtor | Comparação por necessidade, contratos e testes de duplicidade |
| M7 + B | Backpressure, rate limiting, throttling, token/leaky bucket, load shedding, timeout, retry, circuit breaker, bulkhead, fallback, OAuth2/JWT/TLS/mTLS | Nova `resiliencia-controle-fluxo-java`, `seguranca-aplicacao-java` | Arquitetura, construtor, revisor e segurança | Limites explícitos e provas de degradação/recuperação |
| B | DLQ, idempotência, buffering, sinalização ao produtor, descarte e sampling | Nova skill + `mensageria-sqs-kafka` | Construtor, revisor e chaos | Sem crescimento ilimitado e sem descarte silencioso de negócio |
| M8 | Logs, métricas, tracing, Prometheus/Grafana/OTel/Jaeger, probes, alertas e incidentes | `monitoramento-java`, `padrao-de-logs-java`, `devops-cicd`, `chaos-engineer` | Monitoramento, DevOps e chaos | Métricas de saturação, SLO e recuperação observada |
| M9 | Encurtador, chat, feed, streaming/e-commerce | Referências de `design-system-architecture` + exemplos Java | Arquitetura, construtor e revisor | Quatro estudos com trade-offs; checkout como caso executável inicial |
| M10 | Estrutura de resposta em 45 minutos, trade-offs e armadilhas | Referência específica de `design-system-architecture` | `arquiteto-sistemas` | Roteiro e rubrica; entrevista não bloqueia o fluxo de produção |
| Engenharia transversal | Coesão, acoplamento, SOLID com contexto, DDD, clean code, testes, contratos, compatibilidade, migração e entrega | Skills Java existentes + nova `testes-sistemas-java` | Construtor, revisor e refatorador | Testes de arquitetura, comportamento e compatibilidade |

CAP não será apresentado como escolha irrestrita de “dois de três”; ACID/BASE não será tratado como sinônimo exato de consistência forte/eventual. A seleção de banco deve considerar requisitos e operações, sem generalizações como “todo NoSQL não tem transações”.

## 5. Contrato comum para decisões e entregas

Toda proteção proposta deve registrar estes campos, aplicando-os apenas ao fluxo relevante:

| Campo | Informação exigida |
|---|---|
| Fluxo protegido | Operação, dependência, criticidade, efeito de negócio |
| Carga | Taxa média/pico, distribuição do pico, tamanho de item e crescimento |
| Capacidade | Concorrência ativa, fila por itens/bytes, espera máxima e orçamento de conexões |
| Tempo | Deadline ponta a ponta, aquisição de recurso, tentativa e cancelamento |
| Sobrecarga | Quem reduz produção; quando rejeitar, persistir, pausar ou degradar |
| Falha | Erros transitórios/permanentes, retry total, idempotência, DLQ e recuperação |
| Escopo | Por instância, dependência, cliente, tenant, partição ou conjunto de instâncias |
| Observabilidade | Métrica, unidade, SLI, alerta, dashboard e runbook |
| Prova | Teste/cenário, resultado esperado, resultado observado e limitações |

Os agents devem receber escopo, requisitos conhecidos, evidências e dúvidas pendentes. Devem devolver decisões, artefatos, testes realizados, riscos e encaminhamento ao responsável seguinte. Não repetir perguntas já respondidas; perguntar somente por informação que afete a decisão.

## 6. Resiliência e controle de fluxo: conteúdo obrigatório

| Conceito | Orientação a incorporar | Exemplo Java previsto | Prova mínima |
|---|---|---|---|
| Backpressure | Consumidor regula demanda/ritmo; buffers e recursos possuem limites em cada fronteira | `Flow`/Reactor com demanda explícita | Não emitir além da demanda; cancelamento encerra o fluxo |
| Admissão e buffering | Limitar tarefas ativas, espera, itens e bytes; definir overflow | `Semaphore`, `ArrayBlockingQueue`, executor limitado | Rejeição previsível; capacidade nunca excedida |
| Rate limiting | Controlar taxa por identidade/escopo; explicitar burst e limite distribuído | Token bucket Java e integração por cliente Java | Burst permitido; taxa sustentada limitada; tenants isolados |
| Throttling/leaky bucket | Regular ritmo; evitar transformar limitação em fila sem limite | Relógio injetável e agendamento Java limitado | Espaçamento observável e fila limitada |
| Load shedding | Rejeitar cedo por saturação, prioridade e deadline | Política Java de admissão por capacidade | Fluxo crítico preservado; rejeições medidas |
| Timeout/deadline | Limitar cada espera e propagar o orçamento restante | `Duration`, cliente HTTP Java e cancelamento | Dependência lenta não mantém recursos indefinidamente |
| Retry/backoff/jitter | Só para falha elegível; limite por operação e orçamento agregado; uma camada responsável | Política Java com tempo/aleatoriedade injetáveis | Tentativas e duração limitadas; não multiplicar retries entre camadas |
| Circuit breaker | Closed/open/half-open, amostra mínima, erros elegíveis, sondas de recuperação | API Java do Resilience4j | Abre, rejeita e recupera; erro de negócio não abre indevidamente |
| Bulkhead | Isolar concorrência/conexões por dependência e criticidade | `Semaphore` ou bulkhead Java por dependência | Relatórios saturados não consomem a reserva do checkout |
| Fallback | Alternativa limitada, semanticamente segura e observável | Resposta Java com estado degradado/frescor | Não simular aprovação de pagamento nem sobrecarregar o fallback |
| Idempotência | Chave + escopo + payload + persistência + resultado; atomicidade do efeito | Serviço Java e restrição de unicidade transacional | Duplicatas concorrentes/reinício não repetem efeito |
| DLQ/replay | Quarentena durável, causa, retenção, acesso e replay com taxa controlada | Clientes Java SQS/Kafka | Falha de publicação não autoriza ack/commit destrutivo |
| Recuperação | Drenagem, ramp-up, stop/resume, graceful shutdown e contenção de stampede | Lifecycle Java e testes de reinício | Retorno sem pico de retries/replay nem perda de confirmação |

Backpressure, limitação de taxa, bulkhead e circuit breaker devem ser ensinados como mecanismos distintos e combináveis. Reactive Streams define troca assíncrona com demanda e buffers limitados; uma fila isolada não comprova controle de fluxo ponta a ponta. [Especificação Reactive Streams](https://www.reactive-streams.org/).

Circuit breaker não limita por si só o número de chamadas concorrentes: essa responsabilidade pertence ao bulkhead/controle de admissão. [Documentação do Resilience4j](https://resilience4j.readme.io/docs/circuitbreaker).

Acrescentar ao material de `.docs`, sem copiá-lo como especificação normativa:

- A vazão de produção sustentada acima da capacidade não é resolvida por buffer; dimensionar duração do pico, tempo de drenagem, retenção e política de rejeição.
- `request(n)` somente controla a origem quando a cadeia coopera. Adaptadores de fonte não regulável exigem política explícita de overflow.
- Poll de Kafka desacopla o consumidor; não reduz automaticamente a produção. Ensinar pause/resume, manutenção do poll e commit somente do processamento concluído. [API oficial KafkaConsumer](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html).
- Corrigir a notação de backoff: crescimento exponencial com teto, acrescido de jitter, não multiplicação linear disfarçada. Identificar retries já realizados pelo SDK. [Comportamento de retries dos SDKs AWS](https://docs.aws.amazon.com/sdkref/latest/guide/feature-retry-behavior.html).
- Pool de conexões protege um recurso, mas a espera pelo pool também precisa de limite; virtual threads não aumentam a capacidade do banco.
- Não descartar pedidos/pagamentos como se fossem telemetria. Sampling/descarte exige semântica e política explícitas; logs de auditoria também podem exigir preservação.
- Cancelar um future ou encerrar a resposta HTTP não prova que a operação remota foi interrompida; tratar resultado desconhecido e reconciliar operações com efeito.
- Idempotência de mensagem e transação no broker não equivalem a exatamente uma execução de efeitos externos.

## 7. Organização prevista dos arquivos

Os caminhos abaixo são **entregáveis futuros**, salvo quando marcados como existentes.

| Área | Arquivos e responsabilidade |
|---|---|
| Governança | `docs/catalogo/convencoes.md`, `docs/catalogo/matriz-cobertura.md`, `docs/catalogo/compatibilidade.md`, `docs/catalogo/avaliacoes-agents.md` |
| Resiliência | `skills/resiliencia-controle-fluxo-java/SKILL.md`; `references/backpressure-java.md`, `references/timeouts-retries-java.md`, `references/isolamento-degradacao-java.md`, `references/capacidade-e-limites.md` |
| Testes | `skills/testes-sistemas-java/SKILL.md`; `references/concorrencia-resiliencia.md`, `references/contratos-arquitetura.md`, `references/integracao-carga.md` |
| Arquitetura | Ampliar os 5 arquivos existentes em `skills/design-system-architecture/references/`; criar `capacidade-slos.md`, `consistencia-distribuida.md`, `rede-trafego.md`, `protocolos-comunicacao.md`, `estudos-de-caso-java.md`, `entrevista-system-design.md` |
| Mensageria | Ampliar `skills/mensageria-sqs-kafka/SKILL.md`; criar `references/controle-consumo-java.md` e `references/idempotencia-outbox-replay-java.md` |
| Cache/dados | Ampliar `skills/spring-data-redis/SKILL.md`, `skills/persistencia-jpa/SKILL.md`, `skills/banco-de-dados-performance/SKILL.md`; criar `skills/spring-data-redis/references/cache-protecao-java.md` |
| Observabilidade | Ampliar `skills/monitoramento-java/SKILL.md`, `skills/padrao-de-logs-java/SKILL.md`; criar `skills/monitoramento-java/references/slo-saturacao-java.md` |
| Exemplos executáveis | `examples/java/pom.xml` e módulos Maven `fundamentos`, `reativo`, `integracao`, `carga`; fontes e testes sob `src/main/java` e `src/test/java` |
| Validação do catálogo | `validation/java/pom.xml`; testes Java em `validation/java/src/test/java/br/com/srportto/catalogo/` |
| Entrega | `.github/workflows/validar-catalogo.yml`, `examples/java/README.md`, atualização de `skills/README.md` |

O pacote-base dos exemplos será `br.com.srportto.exemplos`; cada módulo concentra um conjunto de dependências. Os testes do catálogo não dependem de Docker; integração com serviços externos fica em perfil separado. Os detalhes de código vivem nos exemplos executáveis; as skills referenciam os arquivos e mostram apenas trechos essenciais.

## 8. Backlog ordenado

### T01 — P0: corrigir descoberta, contratos e rastreabilidade

**Arquivos:** `skills/README.md`; os 11 arquivos de `agents/`; novos `docs/catalogo/convencoes.md` e `docs/catalogo/matriz-cobertura.md`.

**Depende de:** nenhuma tarefa. **Produz:** convenções consumidas por todas as tarefas seguintes.

- [x] Inventariar identificador, responsabilidade, gatilho, referências e origem de cada skill/agent, distinguindo conteúdo próprio de ferramentas auxiliares.
- [x] Documentar `skills/` e `agents/` como fontes deste repositório; explicar `.claude/` como possível destino de instalação. Referências devem resolver relativamente à raiz efetiva do catálogo.
- [x] Atualizar contagens e links; marcar caminhos de outro monorepo como exemplos/contexto externo, sem presumir que existam aqui.
- [x] Definir contrato de skill: quando usar/não usar, entradas, decisão, passo a passo, saída, critérios de validação, limites e referências sob demanda.
- [x] Definir contrato de agent: escopo, skills relevantes, entradas, entregas, evidências, fronteiras e encaminhamentos. Tornar defaults e perguntas coerentes.
- [x] Registrar a matriz da seção 4 com requisitos identificáveis e síntese versionada das duas fontes locais.

**Aceite:** todos os 31 identificadores atuais e os 11 agents aparecem no inventário; toda referência é resolvível ou explicitamente externa; nenhum requisito depende exclusivamente de `.docs` ignorada.

### T02 — P0: preparar avaliação estrutural e comportamental

**Arquivos:** novos `validation/java/pom.xml`, `CatalogoEstruturaTest.java`, `ReferenciasCatalogoTest.java`, `ExemplosJavaTest.java` e `docs/catalogo/avaliacoes-agents.md`.

Os três arquivos de teste ficam em `validation/java/src/test/java/br/com/srportto/catalogo/`.

**Depende de:** T01. **Produz:** verificações reutilizadas pelas próximas tarefas.

- [x] Validar frontmatter com parser YAML compatível, identificadores únicos, existência de skills referenciadas pelos agents e links locais.
- [x] Separar referências locais, destinos de instalação, URLs e placeholders intencionais de templates; não reprovar código de exemplo apenas por conter um caminho ilustrativo.
- [x] Verificar que exemplos novos/reescritos da trilha Java usam arquivos/blocos Java; configurações permanecem classificadas como configurações. Registrar explicitamente exclusões de Graphify/Python/OpenSpec.
- [x] Criar os casos da seção 11 como avaliação de comportamento dos agents; registrar saída esperada e falhas bloqueadoras, sem depender de correspondência literal de palavras.
- [x] Capturar baseline da versão atual com as falhas existentes e manter os testes obrigatórios verdes por lote concluído, sem declarar conformidade global antecipada.

**Validação futura:** `mvn -f validation/java/pom.xml verify`. Esse comando ainda não existe no checkout atual e passa a ser executável após esta tarefa.

**Aceite:** referência quebrada, skill inexistente e exemplo novo em outra linguagem são detectados com arquivo e motivo; avaliações distinguem qualidade da resposta de simples presença de termos.

### T03 — P0: estabelecer capacidade e orçamento de recursos

**Arquivos:** `skills/design-system-architecture/SKILL.md`; referências existentes `system-design.md`, `nfr-checklist.md`, `adr-template.md`; novas `capacidade-slos.md`; `skills/refinamento-de-historias/SKILL.md`; `agents/arquiteto-sistemas.md`.

**Depende de:** T01. **Produz:** parâmetros utilizados em T04–T10.

- [x] Acrescentar levantamento de taxa média/pico, tamanho de item, leitura/escrita, retenção, distribuição de latência, concorrência, orçamento e dependências.
- [x] Ensinar estimativas de armazenamento/banda/memória e Lei de Little com hipótese de regime estável: concorrência média aproximada = vazão × tempo médio no sistema.
- [x] Separar média de percentis; não dimensionar capacidade multiplicando cegamente pico por p99.
- [x] Definir SLA, SLO, SLI, janela, denominador, exclusões e orçamento de erro. Diferenciar disponibilidade por tempo e por requisições.
- [x] Incluir orçamento de deadline e conexões por instância/conjunto de réplicas; considerar amplificação por fan-out e retry.
- [x] Reescrever o exemplo Node.js em Java/Spring Boot; expandir ADR para opções descartadas, custos, riscos, observabilidade e gatilhos mensuráveis para evolução.

**Exercício de aceite:** cenário sintético de chegada de 1.000 itens/s, processamento de 800 itens/s por 10 s e itens de 2 KiB: backlog de 2.000 itens e cerca de 3,91 MiB apenas de payload. Explicar overhead, limite de latência e drenagem após o pico. Buffer de 500 itens exige pausar/rejeitar/persistir excedentes; não pode ser apresentado como solução suficiente.

### T04 — P0: criar a referência comum de proteção

**Arquivos:** nova `skills/resiliencia-controle-fluxo-java/` e suas quatro referências da seção 7; `skills/java-moderno/SKILL.md`; `skills/java-architecture/SKILL.md`; `skills/arquitetura-limpa-java/SKILL.md`.

**Depende de:** T03. **Produz:** regras principais da seção 6, exemplos JDK e critérios para integração.

- [x] Escrever árvore de decisão: controlar produtor, regular taxa, limitar concorrência, limitar espera, rejeitar cedo, degradar ou persistir.
- [x] Para cada mecanismo, incluir problema, quando não aplicar, escopo, falhas, métricas, teste e link para o exemplo Java.
- [x] Diferenciar MVC com virtual threads de cadeia reativa; não exigir WebFlux para obter limites de concorrência/filas.
- [x] Definir cancelamento, deadline monotônico, liberação de recursos em `finally`, interrupção e ausência de espera ilimitada.
- [x] Documentar ordem dos mecanismos por caso de uso: admissão por requisição lógica; retries elegíveis dentro do deadline; breaker/bulkhead por tentativa; timeout de I/O; espera de backoff sem reter conexão. Não impor uma ordem universal de decorators.
- [x] Revisar orientações de virtual threads para Java 25 com documentação do JDK; evitar generalizar restrições de versões antigas.

**Aceite:** cada linha da seção 6 possui referência responsável, exemplo ou cenário Java e critério verificável. Filas ilimitadas, retry infinito e fallback que inventa sucesso são explicitamente reprovados.

### T05 — P0: criar exemplos e testes Java de fundamentos e reatividade

**Arquivos:** `examples/java/pom.xml`; `examples/java/fundamentos/pom.xml`; `examples/java/reativo/pom.xml`; nova `skills/testes-sistemas-java/` e suas referências.

**Fontes previstas:** no módulo `fundamentos`, `FilaLimitada.java`, `ControleConcorrencia.java`, `PoliticaRetry.java`, `TokenBucket.java`, `OrcamentoTempo.java`; no módulo `reativo`, `FluxoSobDemanda.java`. Cada arquivo possui teste de mesmo nome com sufixo `Test`, sob o pacote-base definido na seção 7.

**Depende de:** T02 e T04. **Produz:** exemplos executáveis usados pelas skills.

- [x] Configurar Maven/JDK 25 e versões fixas de JUnit Jupiter; escolher versões compatíveis de Reactor e Resilience4j após verificar o build.
- [x] Escrever testes dos contratos antes dos exemplos: rejeição, cancelamento, timeout, liberação após falha e máximo de tarefas ativas.
- [x] Implementar exemplos pequenos com APIs Java; adotar os dois exemplos da seção 10 como contratos iniciais.
- [x] Criar teste reativo com demanda inicial zero, solicitação em lotes, cancelamento e overflow; usar StepVerifier e tempo virtual para operações temporizadas.
- [x] Injetar relógio/fonte monotônica e aleatoriedade nos testes de rate limiting/retry; evitar testes baseados em esperas arbitrárias.
- [x] Executar testes de concorrência com barreiras/latches e timeout de segurança; verificar invariantes, não apenas conclusão sem exceção.

**Validação futura:** `mvn -f examples/java/pom.xml -pl fundamentos,reativo -am verify`.

**Aceite:** exemplos compilam e todos os cenários determinísticos passam. O uso de StepVerifier seguirá a [documentação oficial de testes Reactor](https://projectreactor.io/docs/core/release/reference/testing.html).

### T06 — P0: proteger consumo e preservar efeitos de negócio

**Arquivos:** `skills/mensageria-sqs-kafka/SKILL.md` e novas referências da seção 7; `skills/criar-aplicacao-java/SKILL.md`; `skills/persistencia-jpa/SKILL.md`; `examples/java/integracao/pom.xml`; exemplos Java `ConsumoControlado`, `ProcessadorIdempotente`, `PublicadorOutbox`, `ReplayControlado` e testes `IT` correspondentes.

**Depende de:** T04 e T05. **Produz:** comportamento confiável sob duplicidade, backlog e reinício.

- [x] Kafka: separar capacidade de processamento, `max.poll.records`, manutenção do poll, pause/resume, concorrência por partição, rebalance e commit de trabalho concluído. Não compartilhar `KafkaConsumer` livremente entre workers.
- [x] SQS: limitar mensagens em voo, lote e concorrência; definir visibility timeout/renovação conforme duração e deadline; confirmar mensagem só após efeito/quarentena durável.
- [x] Explicar retries iniciais versus retentativas, `acks=all` em relação ao conjunto ISR/configuração de durabilidade, e `auto-offset-reset` somente quando não há offset válido aplicável.
- [x] Diferenciar mensagem inválida, falha transitória e efeito desconhecido. Trocar o exemplo de descarte genérico por decisão explícita de rejeição/quarentena quando houver dado de negócio.
- [x] Persistir idempotência e efeito de negócio atomicamente quando estiverem no mesmo banco; validar colisão da chave com payload diferente, corrida entre consumidores e retorno do resultado anterior.
- [x] Introduzir transactional outbox/inbox, publicação recuperável, duplicatas e limites da garantia exatamente uma vez; tratar a janela entre commit e publicação.
- [x] Definir política de retenção/DLQ/replay, ordenação e limite de recuperação, inclusive quando a DLQ está indisponível.

**Aceite:** testes com dois consumidores concorrentes e reinício não duplicam o efeito; falha de envio para DLT não perde a mensagem; backlog para de crescer sem limite local; replay respeita capacidade disponível.

### T07 — P1: completar system design e protocolos

**Arquivos:** `skills/design-system-architecture/SKILL.md`; referências existentes `architecture-patterns.md`, `database-selection.md`; novas `consistencia-distribuida.md`, `rede-trafego.md`, `protocolos-comunicacao.md`; `skills/cloud-architect/SKILL.md`; `skills/api-rest-design/SKILL.md`.

**Depende de:** T03 e T04. **Produz:** cobertura M2/M3/M4/M6.

- [x] Ampliar escolhas de arquitetura com monólito modular, serverless, estado, isolamento por tenant, redundância e gatilhos de escala.
- [x] Cobrir DNS/failover, TTL, L4/L7, balanceamento, gateway/proxy, CDN/edge e cache de conteúdo autenticado; definir propriedade de retry em cada salto.
- [x] Comparar consistência por operação, partição de rede, latência e disponibilidade; incluir replicação, quorum quando aplicável, hot partitions, sharding e resharding.
- [x] Revisar o ADR que afirma ausência de ACID entre documentos no MongoDB; apresentar suporte e trade-offs reais. [Documentação de transações MongoDB](https://www.mongodb.com/docs/manual/core/transactions/).
- [x] Comparar REST/gRPC/GraphQL/WebSocket por contrato, streaming, deadlines, custo operacional e evolução. Implementações ilustrativas, quando presentes, usam APIs Java; não criar uma skill por protocolo.
- [x] Comparar RabbitMQ/SQS/Kafka por modelo de entrega e consumo; manter a implementação aprofundada atual em SQS/Kafka.
- [x] Acrescentar 429/503, `Retry-After`, idempotência e rejeição por saturação ao contrato HTTP, distinguindo quota de cliente e indisponibilidade/capacidade do serviço.

**Aceite:** revisão de uma decisão consegue identificar requisitos, alternativas, perda aceita, custo e teste de falha; nenhum exemplo de aplicação usa Node.js.

### T08 — P1: proteger cache, banco e consistência

**Arquivos:** `skills/spring-data-redis/SKILL.md` e nova `references/cache-protecao-java.md`; `skills/banco-de-dados-performance/SKILL.md`; `skills/persistencia-jpa/SKILL.md`; exemplos Java de integração `CacheProtegido`, `LimiteDistribuido`, `RecuperacaoPendencias` e testes `IT`.

**Depende de:** T05 e T06. **Produz:** limites de recursos e consistência também em cache/persistência.

- [x] Comparar cache local/distribuído, Redis/Memcached, políticas de leitura/escrita, TTL e eviction; explicar perda e reordenação no write-behind.
- [x] Documentar invalidação após commit, staleness máximo, stampede, TTL com jitter, negative caching e recomputação única por chave/escopo.
- [x] Corrigir rate limiting distribuído com operação atômica suportada pela solução escolhida e cliente Java; não manter incremento/expiração separados como garantia robusta.
- [x] Corrigir recuperação de mensagens Redis; diferenciar listagem de pendências, reclaim e ack; definir retenção e recuperação após queda.
- [x] Mostrar fallback de cache com limite de acesso ao banco; não permitir que cache indisponível transfira toda a carga ao datastore.
- [x] Orçar conexões somadas de todas as réplicas, espera de aquisição, timeout de consulta/transação e isolamento de cargas. Separar orientações PostgreSQL de MySQL.
- [x] Acrescentar concorrência transacional, locking, migrações expand/contract, rollback e leitura de réplica com atraso.

**Aceite:** teste de expiração simultânea não dispara recomputação descontrolada; queda do cache respeita limite do banco; falha parcial do limitador não cria quota permanente; consumidor morto permite recuperação sem perda de efeito.

### T09 — P1: observar saturação, alertar e recuperar

**Arquivos:** `skills/monitoramento-java/SKILL.md`, nova `references/slo-saturacao-java.md`, `skills/padrao-de-logs-java/SKILL.md`, `skills/devops-cicd/SKILL.md`, `skills/chaos-engineer/SKILL.md`, `skills/chaos-engineer/references/experiment-design.md`; exemplos Java `MetricasProtecao`, `SaudeAplicacao` e testes.

**Depende de:** T04, T06 e T08. **Produz:** evidências operacionais e política de recuperação.

- [x] Definir métricas de fila em itens/bytes/idade, tarefas ativas, rejeição, espera por conexão, lag, timeout, retries, breaker, fallback, DLQ e tempo de drenagem.
- [x] Separar contagem de requisição lógica da contagem de tentativas; medir sucesso/erro/rejeição e latência sem esconder sobrecarga nos denominadores.
- [x] Adicionar SLO e alerta por consumo do orçamento de erro com janelas e runbook; revisar agregação de métricas e cardinalidade.
- [x] Propagar W3C Trace Context; usar logs/traces/exemplars para IDs de alta cardinalidade, sem colocar `traceId`/pedido/usuário em labels de métricas.
- [x] Unificar health groups do Actuator, startup/readiness/liveness e shutdown. Liveness não depende de serviços externos; readiness considera dependências apenas quando necessário ao atendimento. [Orientações do Spring Boot](https://docs.spring.io/spring-boot/4.2/reference/actuator/endpoints.html).
- [x] Revisar sampling como decisão de volume/custo/diagnóstico e não regra numérica universal; confirmar configuração e compatibilidade na versão usada.
- [x] Definir experimento com consumidor lento, dependência com latência e cache indisponível; escrever controle de falha em Java/Testcontainers/Toxiproxy, baseline, abort e recuperação.
- [x] Correlacionar autoscaling com backlog/capacidade downstream; manter admissão durante o tempo de subida de réplicas e limitar replay no retorno.

**Aceite:** falha de dependência não provoca reinício generalizado por liveness; rejeições e perda de capacidade aparecem nas métricas; cenário de recuperação demonstra drenagem e ausência de tempestade de retries.

### T10 — P1: incorporar proteção ao fluxo de engenharia Java

**Arquivos:** `skills/criar-aplicacao-java/SKILL.md`, `skills/qualidade-codigo-java/SKILL.md`, `skills/revisao-de-codigo-java/SKILL.md`, `skills/seguranca-aplicacao-java/SKILL.md`, `skills/refinamento-de-historias/SKILL.md`, `skills/arquitetura-limpa-java/SKILL.md`, `skills/java-architecture/SKILL.md`.

**Depende de:** T05–T09. **Produz:** proteções aplicadas durante desenho, construção e revisão.

- [x] Refinamento: acrescentar critérios observáveis de sobrecarga, indisponibilidade, duplicidade, staleness e recuperação; incluir limites de aceitação na Definition of Ready.
- [x] Construção: acrescentar requisitos por variante; base simples não ganha broker/cache/Resilience4j sem necessidade. Variantes críticas exigem proteção pertinente e teste correspondente.
- [x] Revisão: criar checklist por severidade para fila ilimitada, espera ilimitada, retry amplificado, ack antecipado, duplicidade de efeitos e fallback inválido.
- [x] Separar heurísticas de estilo de bugs: quantidade de atributos, preferência por bibliotecas de asserção e patterns não são falhas funcionais automáticas.
- [x] Reduzir duplicação entre qualidade e revisão; mover exemplos longos para `skills/qualidade-codigo-java/references/refatoracoes-java.md` e `skills/revisao-de-codigo-java/references/exemplos-revisao-java.md`.
- [x] Segurança: incluir limites de payload/paginação/custo de consulta, identidade de rate limit, proteção contra bypass por headers e quotas por tenant; rate limit de aplicação não substitui proteção de borda contra DDoS.
- [x] Trocar pressupostos específicos de outro monorepo por contexto recebido; remover encaminhamento automático para Python no fluxo Java.
- [x] Registrar compilação, testes unitários, integração e carga separadamente. Teste não executado fica pendente; `-DskipTests` não sustenta aprovação de resiliência.

**Aceite:** uma solicitação Java passa de requisito a teste com rastreabilidade; problemas críticos são identificados sem obrigar microsserviços, reatividade ou bibliotecas desnecessárias.

### T11 — P1: atualizar os 11 agents com responsabilidades verificáveis

**Arquivos:** todos os `.md` existentes em `agents/` e `skills/README.md`.

**Depende de:** T04 e T10. **Produz:** integração operacional do catálogo.

| Agent | Melhoria obrigatória | Saída verificável |
|---|---|---|
| `arquiteto-sistemas` | Capacidade, consistência, falhas e contrato de proteção | ADR + orçamento + matriz de falhas |
| `java-construtor` | Ler as novas skills quando o escopo exigir; exemplos Java; testes de falha | Implementação + comandos e resultados de testes |
| `java-revisor` | Conferir invariantes e evidências; diferenciar não executado de aprovado | Achados com arquivo, risco, cenário e correção |
| `projetista-api` | Quotas, 429/503, retry, deadline, idempotência e compatibilidade | Contrato HTTP e testes de contrato |
| `especialista-banco-dados` | Capacidade agregada de conexões, espera, locks e lag | Baseline, orçamento e comparação após ajuste |
| `especialista-monitoramento` | SLO, saturação, cardinalidade, alertas e incidentes | Instrumentação validada + runbook |
| `engenheiro-chaos` | Sobrecarga, consumidores lentos, falhas e recuperação | Hipótese + baseline + abort + relatório |
| `engenheiro-devops` | Gates reais, probes coerentes, drenagem e limites de JVM | Pipeline e ciclo de vida verificados |
| `cloud-architect` | DNS/LB/CDN, limites de serviço, downstream, isolamento e DR | Topologia + capacidade + custo + plano de recuperação |
| `engenheiro-seguranca` | Abuso de recursos, limites por identidade e indisponibilidade do limitador | Modelo de ameaça e testes de abuso pertinentes |
| `refatorador-java` | Preservar ordenação, cancelamento, transação e liberação de recursos | Testes antes/depois; separação entre refatoração e mudança funcional |

- [x] Atualizar entradas, fontes, passos e saídas de cada agent conforme a tabela.
- [x] Carregar referências conforme o assunto; não acrescentar todas as skills ao frontmatter de todos os agents.
- [x] Resolver divergências entre variante padrão e obrigação de perguntar sempre.
- [x] Preservar fronteiras: arquiteto decide, construtor implementa, revisor verifica, monitoramento mede e chaos exercita falhas.
- [x] Revisar permissões/modelos como compatibilidade do executor, sem alterar nomes ou permissões indiscriminadamente.
- [ ] Executar a avaliação de roteamento/saída da seção 11 e registrar os resultados. **(Pendente: exige invocar os agents em sessões independentes; casos e rubrica prontos em `docs/catalogo/avaliacoes-agents.md`.)**

**Aceite:** os 11 agents têm entregas e fronteiras claras; respostas não encaminham exemplos para outra linguagem e não exigem informação já fornecida.

### T12 — P2: estudos de caso e avaliação de system design

**Arquivos:** `skills/design-system-architecture/references/estudos-de-caso-java.md`, `skills/design-system-architecture/references/entrevista-system-design.md`; `examples/java/integracao/`; `examples/java/carga/pom.xml`; exemplos Java `CheckoutApplication`, `CheckoutSobCargaSimulation` e testes.

**Depende de:** T06–T11. **Produz:** demonstração integrada e cobertura M9/M10.

- [x] Escrever estudos de encurtador, chat, feed e e-commerce/streaming; cobrir identificadores/cache, ordenação/reconexão, fan-out/hot keys e pedidos/idempotência, respectivamente.
- [x] Cada estudo deve ter requisitos, estimativas, componentes, alternativa simples, trade-offs, falha, proteção e validação. Todo trecho de programação será Java.
- [x] Priorizar checkout/pedidos como único caso executável integrado inicial; não criar quatro aplicações completas sem necessidade.
- [x] Implementar carga com DSL Java do Gatling ou gerador Java limitado equivalente; evitar scripts JavaScript de carga.
- [x] Cobrir baseline, rampa, pico, carga sustentada superior à capacidade e retorno; registrar recursos da máquina e capacidade medida, sem confundir resultado sintético com SLO de produção.
- [x] Criar roteiro de entrevista: 5 min requisitos, 5 min capacidade, 10 min desenho, 15 min aprofundamento/falhas, 5 min trade-offs e 5 min revisão.

**Aceite:** quatro estudos completos em documentação; um caso Java executável; relatório demonstra limites, rejeição, ausência de efeitos duplicados e recuperação, com parâmetros reproduzíveis.

### T13 — P2: consolidar validação, documentação e manutenção

**Arquivos:** `.github/workflows/validar-catalogo.yml`, `examples/java/README.md`, `docs/catalogo/compatibilidade.md`, `skills/README.md`, matriz e avaliações.

**Depende de:** todas as anteriores. **Produz:** catálogo verificável e procedimento de manutenção.

- [x] Validar estrutura/referências e compilar/testar fundamentos e reatividade a cada alteração relevante.
- [x] Executar integração com serviços efêmeros no perfil `integracao`; exigir esse perfil nas mudanças que afetem broker, persistência ou cache.
- [x] Executar carga no perfil `carga` em job controlado, com recursos e limiares documentados; não usar resultado de máquina arbitrária como comparação absoluta.
- [x] Documentar versões efetivamente usadas, pré-requisitos, comandos, resultados, fonte/data das decisões e limitações conhecidas.
- [x] Fechar matriz de cobertura e registrar migração dos caminhos antigos e diferenças entre fonte e instalação.
- [x] Revisar links e exemplos quando atualizar JDK, Spring Boot, clientes ou brokers; atualizar versões das skills apenas quando houver mudança correspondente.

**Validação futura:** `mvn -f validation/java/pom.xml verify`; `mvn -f examples/java/pom.xml verify`; `mvn -f examples/java/pom.xml -Pintegracao verify`; `mvn -f examples/java/pom.xml -Pcarga verify`.

**Aceite:** falha crítica bloqueia o lote; perfis não executados aparecem como pendentes. Nenhuma execução é marcada como aprovada somente porque o Markdown contém as palavras esperadas.

## 9. Ordem de execução e marcos

1. **Fundação:** T01 → T02 → T03. Catálogo localizável, requisitos rastreáveis e critérios mensuráveis.
2. **Proteção prioritária:** T04 → T05 → T06. Conceitos, exemplos Java e processamento seguro.
3. **Cobertura de system design:** T07 e T08 após suas dependências; T09 após os cenários de dados/mensageria.
4. **Integração ao trabalho:** T10 → T11. Construção e revisão passam a exigir evidência pertinente.
5. **Demonstração e manutenção:** T12 → T13. Caso integrado, estudos e validação contínua.

Prioridade P0 corresponde a lacunas que podem induzir soluções sem limite de recursos ou sem garantia do efeito de negócio. P1 amplia a cobertura e operacionaliza decisões. P2 comprova a composição e sustenta manutenção. Não atribuir prazo de calendário sem avaliar o esforço dos exemplos de integração e o ambiente disponível.

Cada tarefa deve produzir um diff revisável e registrar sua validação. Se a execução usar commits, manter um lote coerente por tarefa, sem misturar alterações preexistentes do usuário.

## 10. Exemplos Java que definem o padrão esperado

Estes exemplos são parte do plano, não arquivos implementados. O primeiro demonstra buffer limitado e resultado explícito de admissão; o segundo demonstra limite de concorrência com liberação após falha. Nenhum deles, isoladamente, comprova backpressure distribuída ponta a ponta.

### 10.1. Fila limitada com rejeição explícita

Arquivo futuro: `examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FilaLimitada.java`.

```java
package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;

public final class FilaLimitada<T> {
    public enum Admissao { ACEITO, REJEITADO_POR_CAPACIDADE }

    private final ArrayBlockingQueue<T> fila;

    public FilaLimitada(int capacidade) {
        if (capacidade <= 0) {
            throw new IllegalArgumentException("Capacidade deve ser positiva");
        }
        fila = new ArrayBlockingQueue<>(capacidade);
    }

    public Admissao oferecer(T item) {
        Objects.requireNonNull(item, "Item obrigatório");
        // O produtor recebe uma decisão e não fica esperando indefinidamente.
        return fila.offer(item)
                ? Admissao.ACEITO
                : Admissao.REJEITADO_POR_CAPACIDADE;
    }

    public Optional<T> retirar() {
        return Optional.ofNullable(fila.poll());
    }
}
```

Arquivo futuro: `examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/FilaLimitadaTest.java`.

```java
package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilaLimitadaTest {
    @Test
    void deveRejeitarExcedenteEAceitarDepoisDeLiberarCapacidade() {
        var fila = new FilaLimitada<String>(1);

        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("pedido-1"));
        assertEquals(FilaLimitada.Admissao.REJEITADO_POR_CAPACIDADE,
                fila.oferecer("pedido-2"));
        assertEquals("pedido-1", fila.retirar().orElseThrow());
        assertEquals(FilaLimitada.Admissao.ACEITO, fila.oferecer("pedido-2"));
    }

    @Test
    void deveRecusarCapacidadeInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new FilaLimitada<>(0));
    }
}
```

O adapter chamador deve mapear a rejeição para pausa, resposta HTTP ou persistência durável conforme o contrato. O exemplo limita quantidade de itens; a versão aplicada também precisa validar tamanho máximo de item, orçamento em bytes, métricas e destino de itens rejeitados.

### 10.2. Concorrência limitada por recurso

Arquivo futuro: `examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ControleConcorrencia.java`.

```java
package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

public final class ControleConcorrencia {
    private final Semaphore permissoes;

    public ControleConcorrencia(int limite) {
        if (limite <= 0) {
            throw new IllegalArgumentException("Limite deve ser positivo");
        }
        permissoes = new Semaphore(limite);
    }

    public <T> T executar(Callable<T> operacao) throws Exception {
        Objects.requireNonNull(operacao, "Operação obrigatória");
        if (!permissoes.tryAcquire()) {
            throw new RejectedExecutionException("Recurso sem capacidade");
        }
        try {
            return operacao.call();
        } finally {
            // Também libera capacidade quando a operação falha.
            permissoes.release();
        }
    }
}
```

Arquivo futuro: `examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java`.

```java
package br.com.srportto.exemplos;

import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControleConcorrenciaTest {
    @Test
    void deveLiberarCapacidadeDepoisDeFalha() throws Exception {
        var controle = new ControleConcorrencia(1);

        assertThrows(IllegalStateException.class, () -> controle.executar(() -> {
            throw new IllegalStateException("Falha simulada da dependência");
        }));

        assertEquals("recuperado", controle.executar(() -> "recuperado"));
    }

    @Test
    void deveRejeitarEnquantoUnicaPermissaoEstiverOcupada() throws Exception {
        var controle = new ControleConcorrencia(1);

        controle.executar(() -> {
            assertThrows(RejectedExecutionException.class,
                    () -> controle.executar(() -> "excedente"));
            return null;
        });
    }
}
```

Esse limite é por instância do objeto e execução síncrona. Na aplicação, compartilhar uma instância por recurso protegido. A permissão de uma operação assíncrona só pode ser liberada quando o trabalho realmente terminar, não quando retornar um future. O teste acima não substitui o teste multithread previsto em T05. Acrescentar timeout de I/O e testes com virtual threads sem presumir que o semáforo interrompa chamadas travadas.

## 11. Avaliação dos agents e critérios de conclusão

Usar prompts fixos antes/depois da melhoria. Avaliar a decisão e a evidência, não a quantidade de conceitos mencionados.

| Caso | Pedido de avaliação | Resultado obrigatório | Dono da prova |
|---|---|---|---|
| A01 | “Desenhe em Java um serviço cujo produtor recebe 1.000 eventos/s e consumidor processa 100/s” | Perguntar/assumir duração do pico explicitamente, dimensionar backlog, limites e rejeição; não propor fila infinita | T03/T04 |
| A02 | “Use virtual threads para resolver as 10 conexões disponíveis no banco” | Manter orçamento de conexões e admissão; explicar que threads não ampliam banco | T05/T08 |
| A03 | “Repita cobrança após timeout de resposta” | Tratar resultado desconhecido, idempotência persistente e retry condicionado; nunca cobrar cegamente | T06 |
| A04 | “Kafka ficou lento; pare o poll até acabar o trabalho” | Preservar manutenção do consumidor, controle de consumo e commit seguro | T06 |
| A05 | “Cache caiu; envie tudo ao banco” | Fallback limitado, proteção contra stampede e degradação coerente | T08 |
| A06 | “Dependency down; faça liveness falhar em todos os pods” | Corrigir semântica de probes e evitar falha em cascata | T09 |
| A07 | “O build passou com testes pulados; aprove a resiliência” | Registrar validação pendente e exigir provas pertinentes | T10/T11 |
| A08 | “Limite cada cliente em várias réplicas” | Distinguir quota local/global, identidade, atomicidade e falha do limitador | T07/T08/T10 |
| A09 | “Refatore esse consumer mantendo comportamento” | Preservar ordenação, ack/offset, idempotência e cancelamento | T10/T11 |
| A10 | “Explique o design de um chat em 45 minutos com exemplos” | Roteiro M10, ordenação/reconexão e todos os trechos em Java | T12 |
| A11 | “Só preciso de CRUD Java com tráfego baixo” | Solução simples; não impor WebFlux, Kafka, circuit breaker ou microsserviços sem motivo | T10/T11 |
| A12 | “Adicionar métricas para cada pedido e traceId” | Evitar labels de alta cardinalidade; correlacionar por logs/traces/exemplars | T09 |

Pontuar cada caso de 0 a 2: **0** incorreto/perigoso, **1** parcialmente correto sem limites/prova suficientes, **2** decisão correta, contextualizada e verificável. Fazer duas avaliações independentes por caso para detectar instabilidade. Meta: nenhum zero, pelo menos 90% dos pontos e nota 2 em A01–A08 nas duas execuções. Essa métrica não substitui os testes dos exemplos.

Cinco riscos que exigem provas explícitas durante a revisão final:

1. **Sobrecarga sustentada:** limites permanecem respeitados e rejeições ficam visíveis — T05/T12.
2. **Falha após efeito antes do ack:** reentrega não repete efeito de negócio — T06.
3. **Cache indisponível e banco saturado:** fallback não agrava a indisponibilidade — T08.
4. **Cancelamento/timeout com trabalho ainda ativo:** recursos e permissões não são liberados prematuramente — T05/T06.
5. **Recuperação após falha:** retries, sondas e replay não provocam novo colapso — T09/T12.

Critérios globais de conclusão da execução futura:

- [x] 100% das linhas da matriz de cobertura têm responsável, referência e prova ou estudo correspondente.
- [x] Os 11 agents têm entradas/saídas verificáveis e referências válidas.
- [x] Todos os exemplos de programação adicionados ou reescritos nesta iniciativa são Java.
- [ ] Exemplos completos compilam; trechos parciais apontam para a fonte executável correspondente. **(Parcial: exemplos completos compilam e passam; alguns trechos ilustrativos — Pulumi, gRPC, Pub/Sub — não têm fonte executável.)**
- [x] Filas, concorrência, espera, retry e fallback possuem limites e escopo explícitos nos cenários relevantes.
- [x] Testes de idempotência, ack/commit, cancelamento, sobrecarga e recuperação passam nos módulos pertinentes.
- [x] Skills e agents distinguem compilação, testes executados e verificações pendentes.
- [x] Não existem alterações incidentais em ferramentas auxiliares ou no `.gitignore` do usuário.

## 12. Estado desta entrega

**Concluído agora:** análise das fontes locais, inventário, diagnóstico, proposta de organização, matriz de cobertura, backlog, exemplos ilustrativos Java e critérios de validação.

**Planejado para execução posterior:** T01–T13, criação das duas skills, alterações nos agents, implementação/compilação dos exemplos e execução das avaliações. Os exemplos deste documento não foram compilados nem executados nesta etapa.

## 13. Estado da execução (2026-10-06)

**Executado:** T01–T13 sem commit (branch `main` com alterações preexistentes do usuário; commit fica a critério do
usuário). Registro detalhado, decisões ("rulings") e resultados no ledger local
`.superpowers/sdd/2026-10-06-evolucao-skills-agents-java/progress.md` (ignorado pelo Git).

**Verificado por execução:** `mvn -f validation/java/pom.xml verify` (14 testes);
`mvn -f examples/java/pom.xml verify` (79 testes); `-Pintegracao` (+7 `ExternoIT` com Kafka, PostgreSQL,
LocalStack, Valkey e Toxiproxy); `-Pcarga` (ensaio do checkout). Versões e números em
`docs/catalogo/compatibilidade.md`.

**Pendente:** avaliação comportamental A01–A12 dos agents; conversão para Java dos scripts importados em quatro
referências de `chaos-engineer` (rastreados como baseline em `validation/java`); primeira execução do workflow
`.github/workflows/validar-catalogo.yml` no GitHub Actions.
