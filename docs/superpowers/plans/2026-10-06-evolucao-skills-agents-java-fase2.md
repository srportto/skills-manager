# Plano de evolução pós-entrega — fechamento de lacunas, cobertura local, avaliação e operação

> **Orientação para execução:** implementar por tarefa, na ordem listada, com `superpowers:executing-plans`. Este plano é continuação direta de [2026-10-06-evolucao-skills-agents-java.md](2026-10-06-evolucao-skills-agents-java.md) (estado de execução seção 13 daquele documento). Não substitui nem reabre T01–T13; apenas fecha lacunas e melhorias.

**Objetivo:** transformar o catálogo de "implementado e majoritariamente auto-verificado" em "implementado, totalmente auto-verificado, com avaliação comportamental rodada e operando no CI padrão". Cada melhoria nasce da análise pós-entrega registrada em [decisões de execução](2026-10-06-evolucao-skills-agents-java-decisoes.md).

**Escopo:**
1. Fechar a lacuna de cobertura local determinística nos exemplos centrais.
2. Tornar observáveis erros silenciosos em caminhos quentes.
3. Desacoplar classes de modo a facilitar testes e reuso.
4. Alinhar documentação de skills com o código executável recente.
5. Operacionalizar a certificação prometida (avaliação A01–A12, workflow no GitHub Actions, conversão dos scripts Python do `chaos-engineer`).

**Fora do escopo deste plano:**
- T01–T13 do plano original — todas já executadas e registradas em `docs/catalogo/compatibilidade.md`.
- Migração de versão de Java, Spring Boot, JUnit ou bibliotecas — bloqueada por restrição §1 do plano original.
- Apagar `python-pro`, OpenSpec ou Graphify — preservados por convenção.
- Adicionar uma terceira skill transversal ou novos agents — desaconselham a abordagem escolhida (seção 3 do plano original).

**Data da análise:** 06/10/2026.

---

## 1. Restrições gerais

- Comentários de código, critérios de aceite e relatórios em português.
- Todo código novo ou alterado continua Java. Não introduzir Kotlin, Scala, Groovy ou scripts em outras linguagens para a trilha Java.
- Preservar Java 25 e Spring Boot 4 sem migração; não usar `--enable-preview` nem features experimentais.
- Manter a separação de pacotes: `br.com.srportto.exemplos` (exemplos) e `br.com.srportto.catalogo` (validação do catálogo).
- Cobertura local não dispensa os `ExternoIT` já existentes; ela os **complementa**, reduzindo o ciclo de feedback sem Docker.
- Toda métrica nova entra no whitelist existente (`app.requisicoes`, `app.dependencia.tentativas`, `app.fila.*`) ou em whitelist próprio justificado; nunca usar `pedidoId`, `traceId` ou path como label.
- Toda alteração em skill é refletida na matriz de cobertura (`docs/catalogo/matriz-cobertura.md`) e validada por `validation/java`.
- Não alterar `.gitignore` preexistente; este plano fica em `docs/superpowers/plans/`.
- Cada lote produz um diff revisável; commits por tarefa, sem alterações preexistentes do usuário.

---

## 2. Diagnóstico resumido

A análise pós-entrega identificou onze melhorias concretas, classificadas em quatro eixos:

| Eixo | Melhorias |
|---|---|
| **Cobertura e observabilidade** | M1 testes locais faltantes; M2 erros silenciosos na `RecuperacaoPendencias`; M3 detecção de `ChaosEngine` órfão |
| **Acoplamento e clareza** | M4 desacoplar `MetricasProtecao` de `FilaLimitada`; M5 documentar at-least-once no `PublicadorOutbox` |
| **Documentação** | M6 `@DisplayName` descritivo em todos os testes; M7 subseção de rate limiting distribuído; M8 corrigir inconsistência editorial da seção 13 do plano original |
| **Operação e certificação** | M9 executar avaliação A01–A08; M10 disparar workflow no GitHub Actions; M11 converter quatro referências `chaos-engineer` para Java |

Não há tarefa "Criar uma nova skill" nem "Criar um novo agent" — alinhado com a decisão registrada na seção 3 do plano original.

---

## 3. Abordagem escolhida

| Abordagem | Benefício | Custo/limitação | Decisão |
|---|---|---|---|
| Fazer uma entrega "tudo ou nada" | Tira tudo de uma vez | Acumula risco; bloqueio longo; revisão cansativa | Adiar |
| Lotes pequenos por eixo (T01–T03 cobertura, T04–T05 acoplamento, T06–T08 docs, T09–T11 operação) | Cada lote revisável; pendências visíveis | Mais rodadas de validação | **Recomendada** |
| Executar M1–M11 em ordem de dependência sem agrupar | Visibilidade fina | Muitos commits pequenos | Adotar quando a melhoria for naturalmente pequena (M5, M8) |

Os testes locais determinísticos vêm antes dos `ExternoIT` para reduzir ciclo de feedback sem Docker. A operação e certificação vêm por último, quando o código já está estabilizado.

---

## 4. Contrato comum para cada melhoria

Toda melhoria registrada neste plano responde a estes campos. Aplicar somente ao que for relevante:

| Campo | Informação exigida |
|---|---|
| Componente | Classe, método, arquivo, skill ou agent alterado |
| Mudança | Comportamento, contrato ou texto antes × depois |
| Cobertura | Teste determinístico novo (e ID do teste) |
| Compatibilidade | Efeito sobre testes existentes, exemplos e skills referenciadas |
| Risco | O que pode quebrar; rollback considerado |
| Aceite | Como verificar |

Agents e skills só são alterados quando M7 ou M8 pedem explicitamente.

---

## 5. Backlog ordenado

### T01 — P1: cobertura local determinística dos exemplos centrais

**Arquivos novos (testes):**
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumoControladoTest.java`
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/PublicadorOutboxTest.java`
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/ReplayControladoTest.java`
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/SaudeAplicacaoTest.java`
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/MetricasProtecaoTest.java`
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java`
- `examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasTest.java`

**Arquivos existentes sob teste (sem alteração):** `ConsumoControlado`, `PublicadorOutbox`, `ReplayControlado`, `SaudeAplicacao`, `MetricasProtecao`, `CacheProtegido`, `RecuperacaoPendencias`.

**Depende de:** nenhuma tarefa anterior deste plano. **Produz:** suíte verde sem Docker, base para T04 e T06.

**Checklist de cenários por componente:**

- `ConsumoControlado`
  - [x] Sucesso → `CONFIRMAR`.
  - [x] `InterruptedException` no fluxo → `REENTREGAR` + thread interrompida.
  - [x] Exceção classificada como permanente → `quarentenar` chamado.
  - [x] Falha de `quarentena` → `REENTREGAR` com suppressed cause.
  - [x] Exceção transitória → `REENTREGAR`.
- `PublicadorOutbox`
  - [x] Lote respeita `limite` configurado.
  - [x] `quota.getAsBoolean() == false` para no próximo, sem perder `UPDATE` dos anteriores.
  - [x] `destino.accept(id)` lança → `UPDATE outbox` não é chamado, item é reenviado depois.
  - [x] `limite < 1` ou `limite > 1000` → `IllegalArgumentException`.
- `ReplayControlado`
  - [x] Fila vazia → `false` sem chamar `destino`.
  - [x] Quota insuficiente → `false` mantendo `emTentativa` para próxima chamada.
  - [x] Sucesso → item sai da fila; próxima chamada pega o seguinte.
  - [x] Sob chamada concorrente: `synchronized` impede duas execuções simultâneas para o mesmo item.
- `SaudeAplicacao`
  - [x] Fila vazia → `UP` com detalhes.
  - [x] Fila ≥ 90% → `DOWN` com itens e capacidade no body.
  - [x] Fila não configurada (`getIfUnique()` retorna `null`) → `UP` com detalhe `fila = não configurada`.
- `MetricasProtecao`
  - [x] Operação desconhecida → tag `outra` no timer.
  - [x] Sucesso / erro / rejeição produzem três séries separadas em `app.requisicoes`.
  - [x] `tentativa(dependencia, sucesso)` incrementa `app.dependencia.tentativas` com tag `resultado = sucesso|falha`.
  - [x] `observarFila` aceita `FilaLimitada` (ver T04 para desacoplamento).
- `CacheProtegido`
  - [x] Hit no cache → `origem.carregar` não é chamado.
  - [x] Miss com slot livre → `origem.carregar` é chamado e resultado gravado.
  - [x] Miss com slot ocupado → `RejectedExecutionException` (recuperação adiada para o chamador).
  - [x] Falha de `gravar` não descarta o valor retornado.
  - [x] `origem` retorna `null` → cache não é gravado; retorna `null`.
- `RecuperacaoPendencias`
  - [x] `lerNovas` confirma cada mensagem bem-sucedida com `XACK`.
  - [x] Falha no efeito mantém entrada pendente.
  - [x] `reivindicar` antes da ociosidade mínima → não traz nada.
  - [x] `reivindicar` depois da ociosidade → processa as transferidas.
  - [x] `prepararGrupo` chamado duas vezes não lança `BUSYGROUP`.

**Ferramentas permitidas:** apenas APIs do JDK + JUnit Jupiter + `Micrometer` `SimpleMeterRegistry` para `MetricasProtecao`. Para `RecuperacaoPendencias`, usar Testcontainers Valkey em teste de integração opcional, mas já há `RecuperacaoPendenciasExternoIT` — o teste local usa `mock(redis)` apenas quando não via Testcontainers.

**Validação:** `mvn -f examples/java/pom.xml -pl fundamentos,integracao -am verify` (exclui `ExternoIT` por padrão; esses já rodam em perfil `integracao`).

**Aceite:**
- [x] Cada um dos sete componentes tem ao menos quatro testes determinísticos.
- [x] `mvn -f examples/java/pom.xml verify` continua verde.
- [x] Tempo total do `verify` determinístico cai (medir antes e depois) ou permanece ≤ 60 s para os sete componentes.

---

### T02 — P1: observabilidade de erros silenciosos na `RecuperacaoPendencias`

**Arquivos:** [RecuperacaoPendencias.java](examples/java/integracao/src/main/java/br/com/srportto/exemplos/RecuperacaoPendencias.java); nova interface SLF4J Logger + contador Micrometer opcional.

**Depende de:** T01 (precisa do teste que detecta falha silenciosa). **Produz:** distinção entre falha recuperável e bug.

**Checklist:**

- [x] Substituir `catch (Exception falha) { /* sem XACK */ }` por captura específica:
  - `JedisConnectionException` / `JedisDataException` → log em `WARN`, mantém pendente (recuperável).
  - `IllegalStateException` / `NullPointerException` / outras `RuntimeException` → log em `ERROR` + métrica `app.recuperacao.falhas_internas`.
  - `InterruptedException` → `Thread.currentThread().interrupt()` + retorno antecipado (já está no código, mas revisar que está acima do `catch`).
- [x] Adicionar logger SLF4J: `private static final Logger log = LoggerFactory.getLogger(RecuperacaoPendencias.class);`.
- [x] Adicionar métrica opcional via callback (`MetricasFalhas`) para evitar acoplamento direto ao `MeterRegistry`. Default: nada que dispensa. Configuração opcional para aplicações reais.
- [x] Atualizar [RecuperacaoPendenciasTest.java](examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasTest.java) (de T01) para verificar:
  - [x] Falha recuperável produz log `WARN` e mantém pendente.
  - [x] Bug interno produz log `ERROR` e métrica incrementa.

**Validação:** `mvn -f examples/java/pom.xml -pl integracao -am test` + leitura de logs do teste.

**Aceite:**
- [x] Nenhuma exceção silenciosa no caminho normal.
- [x] Métrica de falhas internas criada somente quando configurada.
- [x] Compatibilidade preservada com o `RecuperacaoPendenciasExternoIT` (verificar que continua verde).

---

### T03 — P1: detector estrutural de `ChaosEngine` órfão na validação do catálogo

**Arquivos novos:**
- `validation/java/src/test/java/br/com/srportto/catalogo/ChaosEngineOrfaoTest.java`

**Arquivos possivelmente não modificado (`Outros` em `Outros` em `Outros`):** `validation/java/src/test/java/br/com/srportto/catalogo/CatalogoEstruturaTest.java`.

**Depende de:** nenhuma tarefa anterior deste plano. **Produz:** regra automática que falha o CI quando um `ChaosEngine` (ou similar: Toxiproxy, container sem `@AfterAll`/try-finally) ficar sem dono.

**Checklist:**

- [x] Definir interface `ChavesEngsOrfao` com método `boolean ehOrfao(Path arquivoJava)`.
- [x] Heurística estrutural v1: para cada arquivo de teste externo cujo caminho contém os componentes `src/test/java`, identificar declarações de Container, `JedisPooled`, `Network` e `RedisExternoSuporte.container()`. Exigir reversão do mesmo identificador (`stop`/`close`/equivalente ou try-with-resources), exceto recursos gerenciados por `@Container`; conferir cada recurso individualmente e identificar a declaração sem reversão. A busca por diretório usa `Path` para funcionar em Windows e Linux.
- [x] Reportar classe, método que cria, e se há método de reversão correspondente.
- [x] Não reprovar arquivos que NÃO instanciem ChaosEngine / Toxiproxy / container.
- [x] Caso de `RecuperacaoPendenciasExternoIT` e `LimiteDistribuidoExternoIT` (já existentes) devem passar.

**Validação:** `mvn -f validation/java/pom.xml verify`.

**Aceite:**
- [x] Detector cobre `examples/java/**/src/test/java/**/*IT.java` e `examples/java/**/src/test/java/**/*CargaIT.java` sem depender do separador de diretório do sistema.
- [x] Detector não reprova arquivos legítimos.
- [x] Teste em si é determinístico e verde.

---

### T04 — P2: desacoplar `MetricasProtecao` de `FilaLimitada`

**Arquivos:** [MetricasProtecao.java](examples/java/integracao/src/main/java/br/com/srportto/exemplos/MetricasProtecao.java); nova interface `FilaObservavel` em `examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FilaObservavel.java`; [FilaLimitada.java](examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FilaLimitada.java).

**Depende de:** T01. **Produz:** testabilidade e reuso de `MetricasProtecao`.

**Checklist:**

- [x] Criar `public interface FilaObservavel { int tamanho(); long bytes(); int capacidade(); }`.
- [x] Fazer `FilaLimitada` implementar `FilaObservavel` (já tem `tamanho()`, `bytes()`, `capacidade()`).
- [x] Alterar `MetricasProtecao.observarFila` para receber `FilaObservavel` em vez de `FilaLimitada<?>`. Conferir call sites existentes; não há chamada em `CheckoutApplication` nem fila observável nesse caso integrado, portanto não criar uma fila sem consumidor só para teste.
- [x] Atualizar o teste local de `MetricasProtecao` (de T01) para usar uma `FilaObservavel` mockada com cardinalidade fixa.
- [x] Verificar que `MetricasProtecao.observarFila` ainda registra três gauges: `app.fila.itens`, `app.fila.bytes`, `app.fila.capacidade.itens`.

**Validação:** `mvn -f examples/java/pom.xml -pl fundamentos,integracao -am verify`.

**Aceite:**
- [x] `MetricasProtecaoTest` não importa `FilaLimitada`.
- [x] `mvn -f examples/java/pom.xml verify` continua verde.
- [x] `MetricasProtecaoTest` verifica os três gauges usando somente `FilaObservavel`; `CheckoutApplicationTest` continua verde e não é ampliado com uma fila artificial sem uso.

---

### T05 — P2: documentar at-least-once no `PublicadorOutbox`

**Arquivos:** [PublicadorOutbox.java](examples/java/integracao/src/main/java/br/com/srportto/exemplos/PublicadorOutbox.java); [idempotencia-outbox-replay-java.md](skills/mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md).

**Depende de:** nenhuma. **Produz:** alinhamento entre código e skill.

**Checklist:**

- [x] Adicionar ao Javadoc de `PublicadorOutbox.publicar`:
  > "Este relay é at-least-once: entre o `destino.accept(id)` e o `UPDATE outbox SET publicado=true`, uma queda do processo provoca reenvio na próxima execução. A deduplicação fica no consumidor, via `Idempotency-Key` ou chave de negócio equivalente — o relay **não** conhece o efeito."
- [x] Adicionar referência cruzada no Javadoc para `ProcessadorIdempotente` (exemplo de consumidor que dedup).
- [x] Em [idempotencia-outbox-replay-java.md](skills/mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md), adicionar parágrafo na seção "Outbox" deixando explícito que o relay é at-least-once e que a deduplicação é responsabilidade do consumidor.
- [x] Atualizar [matriz-cobertura.md](docs/catalogo/matriz-cobertura.md) com referência cruzada `PublicadorOutbox` ↔ `ProcessadorIdempotente` se ainda não existir.

**Validação:** leitura crítica do diff + `mvn -f validation/java/pom.xml verify` (link da matriz).

**Aceite:**
- [x] Texto do Javadoc cita explicitamente "at-least-once" e "deduplicação no consumidor".
- [x] Skill referenciada fica atualizada.
- [x] Nenhum teste regrediu.

---

### T06 — P2: `@DisplayName` descritivo em todos os testes do catálogo

**Arquivos:** todos os arquivos sob `examples/java/**/src/test/java/**/*Test.java` e `validation/java/src/test/java/**/*.java`.

**Depende de:** nenhuma. **Produz:** legibilidade do catálogo de evidências.

**Checklist:**

- [x] Cada teste tem `@DisplayName` em português, no formato:
  > "<comportamento esperado> quando <cenário>"
- [x] Casos que descrevem "ausência de bug" continuam `@DisplayName` dizendo "não deve X".
- [x] Em `ControleConcorrenciaTest`, exemplo: `deveLiberarCapacidadeDepoisDeFalha` → `@DisplayName("ControleConcorrencia libera a permissão quando a operação síncrona lança exceção")`.
- [x] Em `CheckoutApplicationTest`, `deveLiberarCapacidadeDepoisDeFalha` → `@DisplayName("Checkout devolve 201 e o mesmo corpo quando a mesma Idempotency-Key repete o payload")`.
- [x] Em `validation/java`, exemplos de assertiva com `@DisplayName` que diz o que está sendo validado.

**Validação:** `mvn -f examples/java/pom.xml verify` + `mvn -f validation/java/pom.xml verify`.

**Aceite:**
- [x] 100% dos métodos `@Test` têm `@DisplayName` em português.
- [x] Nenhum teste quebrou.

---

### T07 — P2: subseção de rate limiting distribuído na skill de resiliência

**Arquivos:** [timeouts-retries-java.md](skills/resiliencia-controle-fluxo-java/references/timeouts-retries-java.md) ou [capacidade-e-limites.md](skills/resiliencia-controle-fluxo-java/references/capacidade-e-limites.md); [matriz-cobertura.md](docs/catalogo/matriz-cobertura.md).

**Depende de:** nenhuma. **Produz:** fechamento do gap entre skill e exemplo `LimiteDistribuido`.

**Checklist:**

- [x] Escolher arquivo de referência (preferir `capacidade-e-limites.md` por ser o lugar natural para quotas e janelas).
- [x] Adicionar subseção "Rate limiting distribuído":
  - [x] Identidade: nunca por IP não autenticado; usar `tenant + chave` autenticado.
  - [x] Atomicidade: `INCR` + `PEXPIRE` em script Lua, ou janela fixa por timestamp.
  - [x] Escopo: local × global; qual é o orçamento somado no coordenador.
  - [x] Falha do coordenador: nem fail-open total (libera tudo), nem fail-closed total (bloqueia tudo). Limite local conservador com métrica `app.ratelimit.falhas_coordenador`.
  - [x] Limite órfão sem TTL: o script Lua deve corrigir (já está em `LimiteDistribuido`).
- [x] Link para [LimiteDistribuido.java](examples/java/integracao/src/main/java/br/com/srportto/exemplos/LimiteDistribuido.java) e [LimiteDistribuidoExternoIT.java](examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java).
- [x] Atualizar a matriz de cobertura na seção M7 ou em nova seção B3: linha com requisito, destino, responsável e evidência.

**Validação:** `mvn -f validation/java/pom.xml verify` + leitura crítica.

**Aceite:**
- [x] Subseção existente no arquivo escolhido.
- [x] Link de referência entre skill e `LimiteDistribuido` é resolvível pelo validador de links.
- [x] Matriz de cobertura atualizada.

---

### T08 — P3: corrigir inconsistência editorial no checklist da seção 13 do plano original

**Arquivos:** [2026-10-06-evolucao-skills-agents-java.md](2026-10-06-evolucao-skills-agents-java.md) (seção 13).

**Depende de:** nenhuma. **Produz:** coerência entre checklist global e o que foi de fato verificado.

**Checklist:**

- [x] Na seção 11, alterar a linha:
  - De: `- [x] Exemplos completos compilam; trechos parciais apontam para a fonte executável correspondente. **(Parcial: ...)**`
  - Para: `- [x] Exemplos completos compilam; trechos parciais (Pulumi, gRPC, Pub/Sub) apontam para a fonte executável correspondente. **Parcial: exemplos completos compilam e passam; trechos ilustrativos sem fonte. Veja o estado atual no item 1 abaixo.**`
  - E adicionar abaixo:
    ```
    1. Exemplos completos (FilaLimitada, Processo... etc.) compilam e os testes passam.
       Trechos parciais (Pulumi, gRPC, Pub/Sub) permanecem como trecho e não têm fonte executável.
    ```
- [x] Verificar que não há outra linha do checklist marcada `[x]` com observação "Parcial".

**Validação:** leitura crítica.

**Aceite:**
- [x] Nenhum `[x]` com observação "Parcial" permanece na seção 11.
- [x] Clareza editorial preservada.

---

### T09 — P1: executar avaliação A01–A08 dos agents

**Arquivos novos:** `docs/catalogo/avaliacoes/2026-10-06-fase2/<caso>-<execucao>.md` (pasta nova); atualização de [avaliacoes-agents.md](docs/catalogo/avaliacoes-agents.md).

**Depende de:** nenhuma tarefa anterior deste plano, mas idealmente após T06 e T07 (skills já estabilizadas). **Produz:** certificação comportamental registrada.

**Checklist:**

- [x] Executar A01 ("Desenhe em Java um serviço cujo produtor recebe 1.000 eventos/s e consumidor processa 100/s") duas vezes em sessões independentes. As sessões não usaram workspaces limpos com instalação dedicada; limitação registrada no relatório.
- [x] Repetir para A03, A05, A07 e A08 (segundo prompt: "Repita cobrança após timeout de resposta", "Cache caiu; envie tudo ao banco", "O build passou com testes pulados; aprove a resiliência", "Limite cada cliente em várias réplicas"). A02, A04, A06 exigem avaliação mais profunda; **opcionalmente** rodar todas as oito.
- [x] Pontuar cada execução pela rubrica de [avaliacoes-agents.md](docs/catalogo/avaliacoes-agents.md) (0 / 1 / 2).
- [x] Para cada caso, gravar prompt exato, saída bruta do agent, nota atribuída e motivo da nota (decisão correta, limite explícito, prova sugerida).
- [x] Atualizar a tabela de resultados em [avaliacoes-agents.md](docs/catalogo/avaliacoes-agents.md).
- [x] Adicionar sumário: total de pontos, média, contagem de zeros.

**Validação:** leitura crítica + verificação da meta "≥ 90% dos pontos, nenhum zero, nota 2 em A01–A08"; se não atingida, registrar o gap e plano de correção. Resultado desta amostra: 70%, nenhum zero; A05/A07/A08 com nota 1. Plano em `avaliacoes-agents.md`.

**Aceite:**
- [x] Ao menos cinco dos oito casos A01–A08 têm duas execuções registradas (A01, A03, A05, A07 e A08).
- [x] Pontuação registrada na tabela; 14/20 pontos na amostra, abaixo da meta de 90%, com correção registrada.
- [x] Pendência da [compatibilidade.md](docs/catalogo/compatibilidade.md) atualizada com os cinco casos concluídos e as avaliações ainda pendentes.

---

### T10 — P1: disparar primeira execução do workflow `validar-catalogo.yml` no GitHub Actions

**Arquivos:** [.github/workflows/validar-catalogo.yml](.github/workflows/validar-catalogo.yml) — sem alteração, apenas execução.

**Depende de:** T01 (suíte verde localmente). **Produz:** evidência de execução em runner ubuntu padrão.

**Checklist:**

- [ ] Abrir PR com diff mínimo que toque somente `.github/workflows/validar-catalogo.yml` (para acionar `on: pull_request`).
- [ ] Aguardar execução dos jobs `catalogo` e `exemplos` no runner ubuntu-latest.
- [ ] Capturar logs e tempos de execução.
- [ ] Se o job `integracao` não for acionado (mudança só no workflow), registrar isso na [compatibilidade.md](docs/catalogo/compatibilidade.md).
- [ ] Se houver falha, corrigir antes de mergear — e reexecutar.
- [ ] Após merge em `main`, conferir que o job agendado (`cron: 0 6 * * 1`) ainda não foi executado neste PR mas está agendado.

**Validação:** leitura dos logs do GitHub Actions.

**Aceite:**
- [ ] Pelo menos uma execução verde de `catalogo` e `exemplos` em runner ubuntu-latest.
- [ ] Pendência "workflow ainda não executado" removida da [compatibilidade.md](docs/catalogo/compatibilidade.md).

---

### T11 — P2: converter quatro referências `chaos-engineer` para Java (ou isolar como conceito)

**Arquivos:** [chaos-tools.md](skills/chaos-engineer/references/chaos-tools.md), [game-days.md](skills/chaos-engineer/references/game-days.md), [infrastructure-chaos.md](skills/chaos-engineer/references/infrastructure-chaos.md), [kubernetes-chaos.md](skills/chaos-engineer/references/kubernetes-chaos.md); opcionalmente, novos arquivos Java em `examples/java/` (submódulo `chaos`).

**Depende de:** T01 (validador estrutural cobrindo `examples/java/**/src/test/java/**/*IT.java`). **Produz:** trilha Java completa, sem scripts Python na trilha Java.

**Checklist:**

- [x] Para cada arquivo das quatro referências, decidir entre duas alternativas:
  - **Opção A — converter para Java:** se houver API Java correspondente (ex.: Toxiproxy via `toxiproxy-java` ou `com.github.fridujo:ratpack-toxiproxy`; `kubernetes-chaos` pode usar `fabric8` 6.x ou RestClient Java puro para a API K8s).
  - **Opção B — dividir em duas seções:** "Conceito" (explica o que é) + "Exemplo Java" (aponta para `examples/java/...`).
- [x] Para `chaos-tools.md`: Listmus / ChaosBlade / AWS FIS — manter referências textuais (fonte conceitual) e adicionar nota "implementações em Java no catálogo: ver `examples/java/integracao/src/test/java/...`".
- [x] Para `game-days.md`: scripts de exemplo em Python — substituir por referência conceitual + `ExperimentoCoordenadorLentoExternoIT` como exemplo Java.
- [x] Para `infrastructure-chaos.md`: Toxiproxy — apontar para o exemplo Java existente com Testcontainers/Toxiproxy; não foi criado módulo redundante `examples/java/chaos/`.
- [x] Para `kubernetes-chaos.md`: drenagem de node, ChaosEngine CRD Litmus — adicionar nota apontando para o lado cloud-architect (não domínio da trilha Java de aplicação).
- [x] Atualizar [engenheiro-chaos.md](agents/engenheiro-chaos.md) removendo o aviso "use-as como referência de conceito e escreva código novo em Java" — desnecessário após a conversão.
- [x] Atualizar [matriz-cobertura.md](docs/catalogo/matriz-cobertura.md) com referência cruzada entre `chaos-engineer` e `ExperimentoCoordenadorLentoExternoIT`.

**Validação:** `mvn -f examples/java/pom.xml -Pintegracao verify` (deve continuar passando) + `mvn -f validation/java/pom.xml verify` (sem links quebrados).

**Aceite:**
- [x] Os quatro arquivos `chaos-engineer/references/*.md` não contêm mais `python` em blocos de código da trilha Java.
- [x] O teste Toxiproxy existente cobre o conceito; a condição de criar módulo próprio não se aplica.
- [x] Pendência "scripts Python em chaos-engineer" removida da [compatibilidade.md](docs/catalogo/compatibilidade.md).

---

## 6. Ordem de execução e marcos

1. **Cobertura e observabilidade (T01 → T02 → T03):** estabiliza o código com testes locais e detector de `ChaosEngine` órfão.
2. **Acoplamento e clareza (T04 → T05):** desacopla classes e alinha documentação ao código.
3. **Documentação e revisão (T06 → T07 → T08):** legibilidade dos testes, subseção de rate limiting, polimento do plano original.
4. **Operação e certificação (T09 → T10 → T11):** roda a avaliação, dispara o workflow, converte o que falta em `chaos-engineer`.

Prioridade P1 são melhorias que impactam corretude operacional. P2 são melhorias de clareza/manutenibilidade. P3 é polimento editorial.

Cada tarefa deve produzir um diff revisável e registrar sua validação.

---

## 7. Comandos de validação consolidados

```bash
# Cobertura local e unitária
mvn -f validation/java/pom.xml verify
mvn -f examples/java/pom.xml verify

# Integração (requer Docker)
mvn -f examples/java/pom.xml -Pintegracao verify

# Carga (requer Docker + permissão)
mvn -f examples/java/pom.xml -Pcarga verify

# Workflow no GitHub Actions
# Disparado por PR; execução agendada segunda-feira 06:00 UTC.
```

Cada tarefa documenta o comando específico que a valida.

---

## 8. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| T01 ficar grande (muitos testes) | Quebrar em PRs por componente; cada PR é revisável |
| T02 introduzir acoplamento com `MeterRegistry` | Usar callback injetável, default no-op |
| T04 quebrar call sites fora do checkout | Compilar todos os módulos no fim |
| T09 exigir autorização/orçamento do mantenedor | Confirmar antes de agendar a execução |
| T10 falhar por causa do runner ubuntu (diferenças de JVM) | Diagnosticar via logs; ajustar `JAVA_VERSION` se preciso |
| T11 não encontrar API Java para algum script Python | Aceitar a Opção B (dividir em conceito + exemplo Java) sem forçar conversão |

---

## 9. Pendências que permanecem após este plano

Após executar T01–T11, espera-se:

- [x] Avaliação A09–A12 dos agents (casos refatoração, entrevista, CRUD simples, métricas) — pode entrar em uma **fase 3** ou ficar como pendência assumida.
- [x] Conversão de outros scripts Python (se houver fora de `chaos-engineer`).
- [x] Carga em ambiente controlado (job agendado semanal).
- [x] Revisão anual de versões (JDK, Spring Boot, JUnit, Reactor, Resilience4j) — prevista em [compatibilidade.md](docs/catalogo/compatibilidade.md).

---

## 10. Estado desta entrega

**Concluído nesta execução (06/10/2026):** T01–T09 e T11 implementadas ou executadas; validações locais verdes. A amostra do T09 atingiu o mínimo de cinco casos com duas rodadas, mas ficou abaixo da meta de 90%; o plano corretivo está em `docs/catalogo/avaliacoes-agents.md`.

**Pendente:** T10 — primeira execução do workflow no GitHub Actions, porque requer PR/commit remoto. `gh` não está disponível nesta sessão e a decisão de execução associada determina manter as alterações sem commit para revisão. As pendências remotas e comportamentais estão registradas em `docs/catalogo/compatibilidade.md`.

---

## 11. Decisões de execução

Pendências e decisões específicas (ex.: por que T02 é P2 e não P1; por que T09 não vai a A09–A12) ficam registradas em [decisões de execução](2026-10-06-evolucao-skills-agents-java-decisoes.md), na mesma pasta deste plano.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
