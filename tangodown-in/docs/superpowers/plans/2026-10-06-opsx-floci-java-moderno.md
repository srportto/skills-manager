# OPSX + catálogo, exemplos, Floci e Java moderno — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer os comandos `/opsx:*` delegarem ao fluxo OpenSpec (skills `openspec-*` + CLI `openspec`) com as skills e
agents do catálogo encaixados por fase; corrigir e ampliar os exemplos Java; trocar LocalStack por Floci; ampliar
`java-moderno` com sealed classes e as formas novas de `switch`, com fonte executável.

**Architecture:** A integração OpenSpec fica em três camadas, da mais robusta para a mais frágil: (1) um
`openspec/config.yaml` modelo cujas `rules` o CLI injeta em `openspec instructions <artefato>` (sobrevive a
`openspec update`); (2) uma skill nova `openspec-catalogo-java` com o mapa fase → skills → agent → prova; (3) ganchos
curtos nas skills `openspec-*` geradas e comandos `/opsx:*` reduzidos a delegação. Exemplos ganham correções com
teste RED→GREEN e um módulo Maven novo `linguagem` com sealed/switch executáveis. Uma guarda em `validation/java`
impede LocalStack de voltar.

**Tech Stack:** Java 25 (sem preview), Maven 3.8.4, Spring Boot 4.0.7, JUnit 6.0.3, Testcontainers 2.0.5,
`io.floci:testcontainers-floci:2.16.1`, imagem `floci/floci:2.2.0`, OpenSpec CLI 1.4.1 (schema `spec-driven`),
SnakeYAML 2.5, Micrometer 1.16.6, AWS SDK v2 SQS 2.31.54.

**Spec:** não há documento de spec separado. A especificação são os quatro pedidos do usuário (2026-10-06), copiados
literalmente:

> 1 - fazer os comandos OPSX usar o openSpec e também cambar usos de agentes e skills desse repo dentro das abordagens do openSpec
> 2 - Analise esse repo e melhore os exemplo quandoi julgar necessario
> 3 - para conectar com a AWS de forma local use sempre floci ao invés de localstack, ajuste nas skills quando julgar necessario.
> 4 - Na skills de java moderno aicione exemplo e mencoes a "seledClass" e novos tipos de swith-case

Leitura adotada: "cambar" = encaixar; "seledClass" = sealed classes; "swith-case" = `switch`.

## Global Constraints

- Java 25 com `maven.compiler.release` 25 e **sem** `--enable-preview`; recurso em preview (ex.: JEP 507, padrões primitivos) só é citado como preview, nunca usado.
- Spring Boot 4.0.7; JUnit 6.0.3; Testcontainers 2.0.5 (o `testcontainers-floci` 2.16.1 usa exatamente 2.0.5).
- AWS local: Floci — `io.floci:testcontainers-floci:2.16.1` (scope `test`), imagem `floci/floci:2.2.0`, endpoint `http://localhost:4566`, região `us-east-1`, credenciais `test`/`test`. Nenhuma ocorrência nova de LocalStack em `skills/`, `agents/`, `examples/java/` ou `docs/catalogo/`.
- OpenSpec CLI 1.4.1, schema `spec-driven`. Chaves de `rules` no `config.yaml` limitadas a `proposal`, `specs`, `design`, `tasks` (verificado: `rules.apply` é ignorado pelo CLI e não aparece em `openspec instructions apply`).
- Comentários de código e texto do catálogo em português (instrução global do usuário); termos técnicos consagrados em inglês. O texto gerado pelo OpenSpec nas skills `openspec-*` continua em inglês; só os ganchos novos são em português.
- Sempre `mvn ... clean ...` nas verificações (a IDE compila em `target/` concorrentemente).
- `-DskipTests` nunca é prova. Teste de integração sem Docker é pendência, não aprovação.
- Não alterar `.gitignore`. Não alterar `graphify`, `python-pro`, `remover-imports-nao-usados`. `openspec-*` e `terraform-engineer` passam a ser alterados por pedido explícito (itens 1 e 3) — registrar como decisão.
- Commits só com consentimento do usuário dado no início da execução; sem consentimento, os passos "Commit" viram uma linha no ledger e o lote fica para o usuário. Nunca `git push`.
- Subagents só se o usuário escolher a execução subagent-driven.

## Review Focus

1. **`openspec init --tools claude` ou `openspec update` num projeto alvo** regenera skills/comandos locais e apaga os ganchos: o fluxo deve continuar guiado pelo `config.yaml` (rules) e pela skill `openspec-catalogo-java` instalada globalmente. A skill documenta `openspec init --tools none`; T02 verifica de ponta a ponta que as `rules` chegam em `openspec instructions design`.
2. **Change sem código Java** (só docs/specs): o portão de archive não pode exigir `java-revisor`; a regra é condicional a "a change alterou código Java". Testado por `OpenSpecIntegracaoTest.skillsOpenspecDevemCarregarAIntegracaoComOCatalogo` (exige o texto condicional na skill de archive).
3. **Imagem Floci nativa em CPU sem as instruções exigidas** (falha ao iniciar o container): documentar a tag `2.2.0-compat` como alternativa em `compatibilidade.md`; T07 roda o `ExternoIT` real.
4. **Semântica de redrive/DLQ diferente no Floci** (contagem de `maxReceiveCount`): o `ConsumidorSqsLimitadoExternoIT` prova contra o container real; se divergir, registrar decisão em vez de afrouxar a asserção.
5. **Trecho de Java moderno que não compila em Java 25 sem preview**: todo trecho da skill tem fonte executável em `examples/java/linguagem` (links validados por `ReferenciasCatalogoTest`), e `PagamentoTest` compila em memória os casos de erro (exaustividade e dominância) com `--release 25`.

---

## Arquivos

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `skills/openspec-catalogo-java/SKILL.md` | criar | Mapa fase → skills → agent → prova; instalação no projeto alvo |
| `skills/openspec-catalogo-java/references/config.yaml` | criar | Modelo de `openspec/config.yaml` (context + rules por artefato) |
| `skills/openspec-{propose,apply-change,archive-change,explore}/SKILL.md` | modificar | Ganchos para `openspec-catalogo-java` |
| `commands/opsx/{propose,apply,archive,explore,sync}.md` | reescrever | Delegação à skill `openspec-*` correspondente (fonte única) |
| `validation/java/src/test/java/br/com/srportto/catalogo/OpenSpecIntegracaoTest.java` | criar | Prova da integração OpenSpec |
| `validation/java/src/test/java/br/com/srportto/catalogo/EmuladorAwsLocalTest.java` | criar | Guarda Floci × LocalStack |
| `validation/java/src/test/java/br/com/srportto/catalogo/Catalogo.java` | modificar | Motivo das auxiliares `openspec-*`/`terraform-engineer` |
| `examples/java/fundamentos/.../ChamadaComDeadline.java` (+Test) | modificar | Leitura única do orçamento |
| `examples/java/integracao/.../CacheProtegido.java` (+Test) | modificar | Single-flight por chave sem colisão de faixa |
| `examples/java/integracao/.../ConsumidorSqsLimitado.java` (+Test) | modificar | Renovação isolada de falhas; cancelar antes de concluir |
| `examples/java/integracao/.../MetricasProtecao.java` (+Test) | modificar | Séries pré-registradas, sem builder por chamada |
| `examples/java/integracao/pom.xml`, `ServicosExternos.java`, `ConsumidorSqsLimitadoExternoIT.java` | modificar | Floci no lugar de LocalStack |
| `examples/java/linguagem/**` + `examples/java/pom.xml` | criar/modificar | Sealed + switch executáveis |
| `skills/java-moderno/SKILL.md`, `skills/java-moderno/references/sealed-e-switch.md` | modificar/criar | Sealed classes e switch moderno |
| `skills/{mensageria-sqs-kafka,criar-aplicacao-java,testes-sistemas-java,devops-cicd,terraform-engineer}/...` | modificar | Floci |
| `skills/README.md`, `docs/catalogo/{convencoes,matriz-cobertura,compatibilidade}.md`, `examples/java/README.md` | modificar | Inventário, regras, versões, execuções |
| `docs/superpowers/plans/2026-10-06-opsx-floci-java-moderno-decisoes.md` | criar | Decisões versionadas desta execução |

---

### Task 1: Skill `openspec-catalogo-java` e `config.yaml` modelo

**Files:**
- Create: `skills/openspec-catalogo-java/SKILL.md`
- Create: `skills/openspec-catalogo-java/references/config.yaml`
- Create: `validation/java/src/test/java/br/com/srportto/catalogo/OpenSpecIntegracaoTest.java`
- Modify: `skills/README.md` (inventário)

**Interfaces:**
- Consumes: `Catalogo.RAIZ`, `Catalogo.ler(Path)` (existentes).
- Produces: skill `openspec-catalogo-java` com seção `## Mapa de fases`; arquivo `references/config.yaml`; classe `OpenSpecIntegracaoTest` com os métodos `mapaDeFasesDeveCitarApenasSkillsEAgentsExistentes` e `configModeloDeveUsarApenasArtefatosDoSchemaESkillsExistentes` (T02 acrescenta mais dois).

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integração do fluxo OpenSpec (comandos /opsx e skills openspec-*) com as skills e os agents do catálogo. */
class OpenSpecIntegracaoTest {
    private static final Path SKILL = Catalogo.RAIZ.resolve("skills/openspec-catalogo-java/SKILL.md");
    private static final Path CONFIG = Catalogo.RAIZ.resolve("skills/openspec-catalogo-java/references/config.yaml");
    /** Artefatos do schema spec-driven (OpenSpec 1.4.1); outras chaves em rules são ignoradas pelo CLI. */
    private static final Set<String> ARTEFATOS = Set.of("proposal", "specs", "design", "tasks");
    /** Nomes de skill/agent entre crases: minúsculas com ao menos um hífen (ex.: `java-revisor`). */
    private static final Pattern NOME = Pattern.compile("`([a-z0-9]+(?:-[a-z0-9]+)+)`");

    private static boolean existeSkillOuAgent(String nome) {
        return Files.exists(Catalogo.RAIZ.resolve("skills/" + nome + "/SKILL.md"))
                || Files.exists(Catalogo.RAIZ.resolve("agents/" + nome + ".md"));
    }

    private static String secao(String texto, String titulo) {
        int inicio = texto.indexOf(titulo);
        assertTrue(inicio >= 0, "seção ausente: " + titulo);
        int fim = texto.indexOf("\n## ", inicio + titulo.length());
        return texto.substring(inicio, fim < 0 ? texto.length() : fim);
    }

    @DisplayName("OpenSpecIntegracao: Mapa de fases deve citar apenas skills e agents existentes")
    @Test
    void mapaDeFasesDeveCitarApenasSkillsEAgentsExistentes() {
        String mapa = secao(Catalogo.ler(SKILL), "## Mapa de fases");
        var erros = new ArrayList<String>();
        for (String fase : List.of("explore", "propose", "apply", "archive")) {
            if (!mapa.contains("`" + fase + "`")) erros.add("fase sem linha no mapa: " + fase);
        }
        mapa.lines().filter(linha -> linha.startsWith("|")).forEach(linha -> {
            var m = NOME.matcher(linha);
            while (m.find()) if (!existeSkillOuAgent(m.group(1))) erros.add("mapa cita inexistente: " + m.group(1));
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("OpenSpecIntegracao: Config modelo deve usar apenas artefatos do schema e skills existentes")
    @Test
    void configModeloDeveUsarApenasArtefatosDoSchemaESkillsExistentes() {
        Map<String, Object> config = new Yaml(new SafeConstructor(new LoaderOptions())).load(Catalogo.ler(CONFIG));
        assertEquals("spec-driven", config.get("schema"));
        assertTrue(String.valueOf(config.get("context")).contains("openspec-catalogo-java"),
                "context deve apontar a skill de integração");
        var regras = (Map<?, ?>) config.get("rules");
        var erros = new ArrayList<String>();
        for (String artefato : ARTEFATOS) if (!regras.containsKey(artefato)) erros.add("rules sem " + artefato);
        for (var entrada : regras.entrySet()) {
            if (!ARTEFATOS.contains(String.valueOf(entrada.getKey()))) {
                erros.add("rules." + entrada.getKey() + " não é artefato do schema (o CLI ignora)");
            }
            for (Object regra : (List<?>) entrada.getValue()) {
                var m = NOME.matcher(String.valueOf(regra));
                while (m.find()) {
                    if (!existeSkillOuAgent(m.group(1))) erros.add("rules." + entrada.getKey() + " cita inexistente: " + m.group(1));
                }
            }
        }
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -f validation/java/pom.xml clean verify -Dtest=OpenSpecIntegracaoTest`
Expected: FAIL nos dois testes — `seção ausente` / `NoSuchFileException` para `openspec-catalogo-java`.

- [ ] **Step 3: Criar `skills/openspec-catalogo-java/references/config.yaml`**

```yaml
# Modelo de openspec/config.yaml para projetos Java que usam este catálogo.
# Copie para <projeto>/openspec/config.yaml e ajuste o bloco context ao projeto.
# O CLI injeta context e rules em `openspec instructions <artefato>`. Chaves de rules fora de
# proposal/specs/design/tasks são ignoradas; a fase apply é guiada pela skill openspec-catalogo-java.
schema: spec-driven
context: |
  Projeto Java 25 (sem preview) + Spring Boot 4, arquitetura hexagonal.
  Skills e agents por fase: skill openspec-catalogo-java, seção "Mapa de fases".
  AWS local: Floci em http://localhost:4566.
  Prova = teste executado com saída lida; -DskipTests nunca é prova.
rules:
  proposal:
    - "Refine a demanda com `refinamento-de-historias`: DoR, critérios observáveis, limites de carga e recuperação."
    - "Aplicação nova: declare nome e variante de `criar-aplicacao-java`; diga o que fica fora do escopo."
  specs:
    - "Contrato HTTP segue `api-rest-design` (Problem Details, 413/429/503, Idempotency-Key)."
    - "Autenticação, autorização e abuso de recursos seguem `seguranca-aplicacao-java`."
  design:
    - "Decisões de sistema seguem `design-system-architecture`: capacidade, consistência e ADR com alternativas."
    - "Toda dependência remota ou fila declara limites e proteções de `resiliencia-controle-fluxo-java`."
    - "Camadas e portas seguem `arquitetura-limpa-java`; mensageria segue `mensageria-sqs-kafka`."
  tasks:
    - "Cada tarefa nomeia a prova (teste e comando) segundo `testes-sistemas-java`; TDD com teste falhando primeiro."
    - "Se a change altera código Java, a última tarefa é a auditoria do `java-revisor` com veredicto APROVADO."
```

- [ ] **Step 4: Criar `skills/openspec-catalogo-java/SKILL.md`**

~~~~markdown
---
name: openspec-catalogo-java
description: "Encaixa skills e agents do catálogo Java nas fases do OpenSpec (explore, propose, apply, archive): qual skill orienta cada artefato, qual agent executa cada tarefa e qual prova encerra a change. Use ao rodar /opsx:* ou as skills openspec-* num projeto Java, ou ao configurar openspec/config.yaml. Uso: carregada pelas skills openspec-* ou `/openspec-catalogo-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.0.0"
  domain: workflow
  triggers: opsx, openspec, propose, apply, archive, spec-driven, config.yaml
  role: workflow
  scope: openspec
  output-format: document
  related-skills: openspec-propose, openspec-apply-change, openspec-archive-change, refinamento-de-historias, design-system-architecture, criar-aplicacao-java, revisao-de-codigo-java
---

# OpenSpec com o catálogo Java

## Visão geral

O OpenSpec governa **o quê** (proposal, specs, design, tasks e o CLI `openspec`). O catálogo governa **o como**:
skills de engenharia orientam cada artefato e agents executam e revisam as tarefas. Esta skill liga os dois. As
skills `openspec-*` continuam sendo o fluxo; elas carregam esta skill nos pontos marcados.

**Quando NÃO usar:** projeto sem OpenSpec (use os agents direto) ou change que não envolve Java nem arquitetura
(o fluxo OpenSpec puro basta).

## Instalação no projeto alvo

1. Instale o catálogo globalmente (fonte `skills/` e `agents/` deste repositório → `~/.claude/skills` e
   `~/.claude/agents`; comandos `commands/opsx/` → `~/.claude/commands/opsx/`).
2. No projeto: `openspec init --tools none` — cria só `openspec/`, sem gerar skills e comandos locais que
   sobrescreveriam os do catálogo. Pela mesma razão, não rode `openspec update` com ferramentas no projeto.
3. Copie [config.yaml](references/config.yaml) para `openspec/config.yaml` e ajuste o `context`. O CLI injeta
   `context` e `rules` em `openspec instructions <artefato>`; é a camada que sobrevive à regeneração das skills.
4. Confira: `openspec new change teste && openspec instructions design --change teste --json` deve trazer as
   regras de `design` em `rules`. Apague a change de teste depois.

## Mapa de fases

| Fase / artefato | Skills que orientam | Agent | Saída e prova |
|---|---|---|---|
| `explore` | `refinamento-de-historias`, `design-system-architecture` | `arquiteto-sistemas` | Perguntas, riscos, números de capacidade; nada implementado |
| `propose` → proposal | `refinamento-de-historias`, `criar-aplicacao-java` (só aplicação nova) | — | DoR, critérios observáveis, limites, fora de escopo |
| `propose` → specs | `api-rest-design`, `seguranca-aplicacao-java` | `projetista-api` | Requisitos com cenários; contrato e erros |
| `propose` → design | `design-system-architecture`, `resiliencia-controle-fluxo-java`, `arquitetura-limpa-java`, `mensageria-sqs-kafka`, `persistencia-jpa`, `cloud-architect` | `arquiteto-sistemas` | ADR com alternativas, capacidade, matriz de falhas |
| `propose` → tasks | `testes-sistemas-java`, `criar-aplicacao-java` | — | Cada tarefa com teste nomeado e comando |
| `apply` — código de aplicação | `criar-aplicacao-java`, `qualidade-codigo-java`, `java-moderno` | `java-construtor` | Teste RED→GREEN executado |
| `apply` — refatoração | `qualidade-codigo-java`, `refactoring-remove-parameter` | `refatorador-java` | Testes antes e depois |
| `apply` — SQL e banco | `banco-de-dados-performance`, `persistencia-jpa` | `especialista-banco-dados` | Plano de execução antes/depois |
| `apply` — pipeline e deploy | `devops-cicd` | `engenheiro-devops` | Build e manifest validados |
| `apply` — observabilidade | `monitoramento-java`, `padrao-de-logs-java` | `especialista-monitoramento` | Métrica/alerta verificado |
| Verificação antes do `archive` | `revisao-de-codigo-java`, `testes-sistemas-java` | `java-revisor` (modo auditoria); `engenheiro-seguranca` se tocar autenticação ou dados sensíveis | Veredicto APROVADO |
| `archive` / sync | — | — | Specs sincronizadas; ADR do design preservada |

## Regras transversais

- **Proporcionalidade:** CRUD de tráfego baixo não ganha broker, cache, WebFlux nem microsserviços; o design
  justifica cada proteção pelo risco.
- **Prova:** uma tarefa só é marcada `[x]` com o teste executado e a saída lida. Compilação não é teste;
  `-DskipTests` não é prova; teste pulado é pendência.
- **Delegação:** delegue ao agent da tabela quando a sessão permitir subagents; senão, carregue as skills do agent
  e execute na sessão, com o mesmo critério de prova.
- **Portão de archive:** se a change alterou código Java, arquivar exige veredicto APROVADO do `java-revisor`
  (modo auditoria). PENDENTE ou REPROVADO bloqueia. Change só de documentação ou spec não passa por esse portão.
- **Stack:** Java 25 sem preview; AWS local com Floci (`http://localhost:4566`).

## Quem aplica o quê

As skills `openspec-propose`, `openspec-apply-change`, `openspec-archive-change` e `openspec-explore` carregam
esta skill. Os comandos `/opsx:*` delegam a essas skills. O `config.yaml` leva as regras de artefato ao CLI.
~~~~

- [ ] **Step 5: Registrar a skill no inventário**

Em `skills/README.md`, logo antes de `### Ferramentas auxiliares`, acrescentar:

```markdown
### Fluxo spec-driven

| Skill | Responsabilidade | Agents principais |
|---|---|---|
| `openspec-catalogo-java` | Encaixa skills e agents do catálogo nas fases do OpenSpec; `config.yaml` modelo | todos, por fase |
```

E trocar o motivo da linha `openspec-*` na tabela de auxiliares por:
`Fluxo OpenSpec 1.4.1 gerado, com ganchos para `openspec-catalogo-java``.

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn -f validation/java/pom.xml clean verify`
Expected: PASS em todos (os 20 anteriores + 2 novos). Se `ExemplosJavaTest` reclamar de linguagem do bloco
`yaml`/`markdown`, conferir `PROIBIDAS` — `yaml` é configuração e já é aceito.

- [ ] **Step 7: Verificar de ponta a ponta com o CLI (manual, registrar saída no ledger)**

```bash
D="$SCRATCH/opsx-e2e"; rm -rf "$D"; mkdir -p "$D" && cd "$D"
openspec init --tools none .
ls -a; ls openspec
cp <repo>/skills/openspec-catalogo-java/references/config.yaml openspec/config.yaml
openspec new change teste
openspec instructions design --change teste --json | grep -E 'design-system-architecture|resiliencia'
```
Expected: `openspec/` criado sem `.claude/`; o JSON de `design` traz as três regras e o `context`. Se
`--tools none` gerar `.claude/`, registrar decisão e trocar o passo 2 da instalação na skill pelo comando que
de fato não gera arquivos.

- [ ] **Step 8: Commit**

```bash
git add skills/openspec-catalogo-java skills/README.md validation/java/src/test/java/br/com/srportto/catalogo/OpenSpecIntegracaoTest.java
git commit -m "feat(openspec): mapa de fases do catálogo e config.yaml modelo"
```

---

### Task 2: Ganchos nas skills `openspec-*` e comandos `/opsx:*` como delegação

**Files:**
- Modify: `skills/openspec-propose/SKILL.md`, `skills/openspec-apply-change/SKILL.md`, `skills/openspec-archive-change/SKILL.md`, `skills/openspec-explore/SKILL.md`
- Rewrite: `commands/opsx/propose.md`, `apply.md`, `archive.md`, `explore.md`, `sync.md`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/OpenSpecIntegracaoTest.java`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/Catalogo.java:24-28`

**Interfaces:**
- Consumes: skill `openspec-catalogo-java` (T01).
- Produces: comandos que contêm `` `openspec-<skill>` `` e `$ARGUMENTS`; skills `openspec-*` que citam `openspec-catalogo-java`.

- [ ] **Step 1: Acrescentar os testes que falham** (em `OpenSpecIntegracaoTest`)

```java
    @DisplayName("OpenSpecIntegracao: Comandos opsx devem delegar para a skill openspec correspondente")
    @Test
    void comandosOpsxDevemDelegarParaASkillOpenspecCorrespondente() {
        var mapa = Map.of("explore", "openspec-explore", "propose", "openspec-propose",
                "apply", "openspec-apply-change", "archive", "openspec-archive-change", "sync", "openspec-sync-specs");
        var erros = new ArrayList<String>();
        mapa.forEach((comando, skill) -> {
            String texto = Catalogo.ler(Catalogo.RAIZ.resolve("commands/opsx/" + comando + ".md"));
            String corpo = texto.substring(texto.indexOf("\n---", 4) + 4);
            if (!corpo.contains("`" + skill + "`")) erros.add(comando + " não delega para " + skill);
            if (!corpo.contains("$ARGUMENTS")) erros.add(comando + " não repassa $ARGUMENTS");
            // Fonte única: o comando não pode voltar a duplicar os passos da skill.
            if (corpo.lines().count() > 30) erros.add(comando + " duplica a skill (" + corpo.lines().count() + " linhas)");
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }

    @DisplayName("OpenSpecIntegracao: Skills openspec devem carregar a integracao com o catalogo")
    @Test
    void skillsOpenspecDevemCarregarAIntegracaoComOCatalogo() {
        var erros = new ArrayList<String>();
        var exigidos = Map.of(
                "openspec-explore", List.of("openspec-catalogo-java", "arquiteto-sistemas"),
                "openspec-propose", List.of("openspec-catalogo-java", "openspec instructions"),
                "openspec-apply-change", List.of("openspec-catalogo-java", "java-construtor", "-DskipTests"),
                // Portão condicional: só change que altera código Java exige o veredicto.
                "openspec-archive-change", List.of("openspec-catalogo-java", "java-revisor", "APROVADO", "alterou código Java"));
        exigidos.forEach((skill, trechos) -> {
            String texto = Catalogo.ler(Catalogo.RAIZ.resolve("skills/" + skill + "/SKILL.md"));
            for (String trecho : trechos) if (!texto.contains(trecho)) erros.add(skill + " sem '" + trecho + "'");
        });
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -f validation/java/pom.xml clean verify -Dtest=OpenSpecIntegracaoTest`
Expected: FAIL — comandos com 100+ linhas, sem `$ARGUMENTS`; skills sem `openspec-catalogo-java`.

- [ ] **Step 3: Ganchos nas skills geradas** (texto em português inserido; o restante do texto gerado fica intacto)

`skills/openspec-propose/SKILL.md` — entre o passo 3 e o passo 4, inserir:

```markdown
3b. **Catálogo Java** — carregue a skill `openspec-catalogo-java` e, para cada artefato, consulte as skills
    indicadas no "Mapa de fases" (ex.: `design` → `design-system-architecture`). As `rules` do
    `openspec/config.yaml` chegam pelo JSON de `openspec instructions`; aplique-as como restrição, sem copiá-las.
```

`skills/openspec-apply-change/SKILL.md` — no início do passo 6, antes do loop, inserir:

```markdown
   **Catálogo Java:** para cada tarefa, escolha o agent pelo "Mapa de fases" de `openspec-catalogo-java`
   (código de aplicação → `java-construtor`; refatoração → `refatorador-java`; SQL → `especialista-banco-dados`;
   pipeline → `engenheiro-devops`; observabilidade → `especialista-monitoramento`). Delegue quando a sessão
   permitir subagents; senão, carregue as skills do agent e implemente com TDD. Marque `[x]` só com o teste
   executado e a saída lida — `-DskipTests` não é prova.
```

`skills/openspec-archive-change/SKILL.md` — entre o passo 3 e o passo 4, inserir:

```markdown
3b. **Portão do catálogo Java** — se a change alterou código Java, exija veredicto APROVADO do `java-revisor`
    (modo auditoria), conforme `openspec-catalogo-java`. PENDENTE ou REPROVADO: mostre o veredicto e pergunte
    antes de arquivar. Change só de documentação ou spec não passa por este portão.
```

`skills/openspec-explore/SKILL.md` — ao fim da seção `## OpenSpec Awareness`, inserir (e trazer do comando a frase
"If the user mentioned a specific change name, read its artifacts for context." para o início da mesma seção):

```markdown
**Lentes do catálogo Java:** para capacidade, trade-offs e desenho de sistema, use as skills e o agent da linha
`explore` de `openspec-catalogo-java` (`arquiteto-sistemas`, `design-system-architecture`,
`refinamento-de-historias`) — ainda em modo de pensamento, sem implementar.
```

- [ ] **Step 4: Reescrever os 5 comandos** — frontmatter preservado byte a byte; corpo novo. Exemplo `commands/opsx/propose.md`:

```markdown
---
name: "OPSX: Propose"
description: Propose a new change - create it and generate all artifacts in one step
category: Workflow
tags: [workflow, artifacts, experimental]
---

Execute a skill `openspec-propose` (ferramenta Skill) com a entrada abaixo e siga-a integralmente. A skill é a
fonte única do fluxo; este comando não repete os passos.

Entrada: $ARGUMENTS

A skill usa o CLI `openspec` e carrega `openspec-catalogo-java` para encaixar skills e agents do catálogo.
Se a skill não estiver instalada, avise e pare: instale o catálogo (`skills/` → `~/.claude/skills`).
```

Os outros quatro seguem o mesmo corpo, trocando o nome: `apply` → `openspec-apply-change`, `archive` →
`openspec-archive-change`, `explore` → `openspec-explore`, `sync` → `openspec-sync-specs` (no `sync`, omitir a
frase sobre `openspec-catalogo-java`, que a skill de sync não carrega).

- [ ] **Step 5: Atualizar o motivo das auxiliares** em `Catalogo.java`: as 5 entradas `openspec-*` passam a
`"fluxo OpenSpec 1.4.1 gerado, com ganchos do catálogo (OpenSpecIntegracaoTest)"`.

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn -f validation/java/pom.xml clean verify`
Expected: PASS em todos (24 testes).

- [ ] **Step 7: Commit**

```bash
git add commands/opsx skills/openspec-* validation/java
git commit -m "feat(opsx): comandos delegam às skills openspec e ganchos do catálogo"
```

---

### Task 3: `ChamadaComDeadline` lê o orçamento uma única vez

**Files:**
- Modify: `examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ChamadaComDeadline.java:23-35`
- Test: `examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ChamadaComDeadlineTest.java`

**Interfaces:** `obter(URI, OrcamentoTempo)` mantém assinatura e exceções declaradas.

- [ ] **Step 1: Teste que falha** (acrescentar; imports: `java.util.ArrayDeque`, `java.util.List`, `java.util.function.LongSupplier`)

```java
    @DisplayName("ChamadaComDeadline: Orcamento que zera entre a checagem e a requisicao deve lancar TimeoutException")
    @Test
    void orcamentoQueZeraEntreAChecagemEARequisicaoDeveLancarTimeoutException() {
        long limite = Duration.ofMillis(200).toNanos();
        // Leituras do relógio: início, primeira leitura com prazo cheio, depois prazo esgotado.
        var leituras = new ArrayDeque<>(List.of(0L, 0L, limite));
        LongSupplier relogio = () -> leituras.size() > 1 ? leituras.poll() : leituras.peek();
        var chamada = new ChamadaComDeadline(Duration.ofSeconds(2));
        // Com leitura dupla, o segundo valor (zero) chega ao HttpRequest e vira IllegalArgumentException.
        assertThrows(TimeoutException.class,
                () -> chamada.obter(uri("/lenta"), new OrcamentoTempo(Duration.ofMillis(200), relogio)));
    }
```

- [ ] **Step 2:** Run: `mvn -f examples/java/pom.xml -pl fundamentos clean verify -Dtest=ChamadaComDeadlineTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `Unexpected exception type thrown, expected: <TimeoutException> but was: <IllegalArgumentException>`.

- [ ] **Step 3: Implementação**

```java
    public String obter(URI uri, OrcamentoTempo orcamento) throws IOException, InterruptedException, TimeoutException {
        // Uma única leitura: checar e usar o mesmo valor evita prazo zerado entre a checagem e a requisição.
        Duration restante = orcamento.restante();
        if (restante.isZero()) throw new TimeoutException("Deadline esgotado antes de chamar " + uri);
        var requisicao = HttpRequest.newBuilder(uri).timeout(restante).GET().build();
        try {
            return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString()).body();
        } catch (HttpTimeoutException erro) {
            var timeout = new TimeoutException("Deadline esgotado aguardando " + uri);
            timeout.initCause(erro);
            throw timeout;
        }
    }
```

- [ ] **Step 4:** mesmo comando do Step 2. Expected: PASS (todos os testes da classe).
- [ ] **Step 5: Commit** — `git commit -m "fix(exemplos): ChamadaComDeadline usa uma leitura do orçamento"`

---

### Task 4: `CacheProtegido` com recomputação única por chave, sem colisão de faixa

**Files:**
- Modify: `examples/java/integracao/src/main/java/br/com/srportto/exemplos/CacheProtegido.java` (reescrita)
- Test: `examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java`

**Interfaces:** construtor mantém a assinatura `(Function<String,T> ler, BiConsumer<String,T> gravar, Carregador<T> origem, int limiteBanco, int limiteRecomputacoes)` — o 5º parâmetro muda de nome (antes `quantidadeFaixas`) e de semântica (máximo de chaves recomputando ao mesmo tempo). `obter(String)` mantém contrato: `RejectedExecutionException` quando a chave já está sendo recomputada ou o limite foi atingido.

- [ ] **Step 1: Testes que falham** (acrescentar)

```java
    @DisplayName("CacheProtegido: Chaves diferentes nao devem se bloquear por colisao de hash")
    @Test
    void chavesDiferentesNaoDevemSeBloquearPorColisaoDeHash() throws Exception {
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        // "a" (97) e "c" (99) caíam na mesma faixa com 2 faixas; agora cada chave tem a própria vaga.
        var cache = new CacheProtegido<String>(chave -> null, (chave, valor) -> {}, chave -> {
            if (chave.equals("a")) {
                entrou.countDown();
                liberar.await(5, TimeUnit.SECONDS);
            }
            return "valor-" + chave;
        }, 2, 2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> primeira = executor.submit(() -> cache.obter("a"));
            try {
                assertTrue(entrou.await(5, TimeUnit.SECONDS));
                assertEquals("valor-c", cache.obter("c"));
            } finally {
                liberar.countDown();
            }
            assertEquals("valor-a", primeira.get(5, TimeUnit.SECONDS));
        }
    }

    @DisplayName("CacheProtegido: Limite de recomputacoes simultaneas deve rejeitar chave excedente")
    @Test
    void limiteDeRecomputacoesSimultaneasDeveRejeitarChaveExcedente() throws Exception {
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var cache = new CacheProtegido<String>(chave -> null, (chave, valor) -> {}, chave -> {
            entrou.countDown();
            liberar.await(5, TimeUnit.SECONDS);
            return "valor";
        }, 4, 1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> primeira = executor.submit(() -> cache.obter("a"));
            try {
                assertTrue(entrou.await(5, TimeUnit.SECONDS));
                assertThrows(RejectedExecutionException.class, () -> cache.obter("b"));
            } finally {
                liberar.countDown();
            }
            assertEquals("valor", primeira.get(5, TimeUnit.SECONDS));
            // A vaga é devolvida ao terminar: a próxima recomputação é aceita.
            assertEquals("valor", cache.obter("b"));
        }
    }
```

- [ ] **Step 2:** Run: `mvn -f examples/java/pom.xml -pl integracao -am clean verify -Dtest=CacheProtegidoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL em `chavesDiferentesNaoDevemSeBloquearPorColisaoDeHash` (`RejectedExecutionException: Recomputação em andamento`). O segundo teste pode passar já na versão antiga (1 faixa); ele protege a nova semântica.

- [ ] **Step 3: Implementação** (também remove imports curinga e o formato compactado, fora do padrão dos demais exemplos)

```java
package br.com.srportto.exemplos;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cache-aside que protege a origem quando o cache falha ou expira:
 * <ul>
 *   <li>uma única recomputação por chave (single-flight); chamadas concorrentes da mesma chave são rejeitadas
 *       e o chamador degrada (valor anterior, 503 ou resposta parcial);</li>
 *   <li>no máximo {@code limiteRecomputacoes} chaves recomputando ao mesmo tempo — o mapa de chaves em voo
 *       nunca passa desse tamanho;</li>
 *   <li>a origem continua limitada por {@code limiteBanco} mesmo com o cache inteiro fora.</li>
 * </ul>
 */
public final class CacheProtegido<T> {
    @FunctionalInterface
    public interface Carregador<T> {
        T carregar(String chave) throws Exception;
    }

    private final Function<String, T> ler;
    private final BiConsumer<String, T> gravar;
    private final Carregador<T> origem;
    private final ControleConcorrencia banco;
    private final Semaphore vagasRecomputacao;
    private final ConcurrentHashMap<String, Boolean> emRecomputacao = new ConcurrentHashMap<>();

    public CacheProtegido(Function<String, T> ler, BiConsumer<String, T> gravar,
                          Carregador<T> origem, int limiteBanco, int limiteRecomputacoes) {
        if (limiteRecomputacoes <= 0) throw new IllegalArgumentException("Limite de recomputações deve ser positivo");
        this.ler = Objects.requireNonNull(ler);
        this.gravar = Objects.requireNonNull(gravar);
        this.origem = Objects.requireNonNull(origem);
        this.banco = new ControleConcorrencia(limiteBanco);
        this.vagasRecomputacao = new Semaphore(limiteRecomputacoes);
    }

    public T obter(String chave) throws Exception {
        Objects.requireNonNull(chave);
        T valor = lerDisponivel(chave);
        if (valor != null) return valor;
        // Vaga antes da chave: assim o mapa nunca guarda mais chaves do que o limite.
        if (!vagasRecomputacao.tryAcquire()) throw new RejectedExecutionException("Limite de recomputações atingido");
        try {
            if (emRecomputacao.putIfAbsent(chave, Boolean.TRUE) != null) {
                throw new RejectedExecutionException("Recomputação em andamento para a chave");
            }
            try {
                return recomputar(chave);
            } finally {
                emRecomputacao.remove(chave);
            }
        } finally {
            vagasRecomputacao.release();
        }
    }

    private T recomputar(String chave) throws Exception {
        // Outro chamador pode ter gravado enquanto esperávamos a vaga.
        T valor = lerDisponivel(chave);
        if (valor != null) return valor;
        T carregado = banco.executar(() -> origem.carregar(chave));
        if (carregado != null) {
            try {
                gravar.accept(chave, carregado);
            } catch (RuntimeException indisponivel) {
                // O valor da origem continua válido sem o cache.
            }
        }
        return carregado;
    }

    private T lerDisponivel(String chave) {
        try {
            return ler.apply(chave);
        } catch (RuntimeException indisponivel) {
            return null;
        }
    }
}
```

- [ ] **Step 4:** mesmo comando do Step 2. Expected: PASS nos 8 testes de `CacheProtegidoTest` (os 6 antigos continuam válidos: `chaveQuente...` usa limite 1 e a mesma chave; `cacheIndisponivel...` é rejeitado pelo `limiteBanco` 1).
- [ ] **Step 5:** Atualizar a frase sobre faixas em `skills/spring-data-redis/references/cache-protecao-java.md` (linha que cita `CacheProtegido`) para "uma recomputação por chave e no máximo N chaves recomputando; excedente rejeitado". Rodar `mvn -f validation/java/pom.xml clean verify`. Expected: PASS.
- [ ] **Step 6: Commit** — `git commit -m "fix(exemplos): CacheProtegido sem rejeição por colisão de faixa"`

---

### Task 5: `ConsumidorSqsLimitado` — renovação resiste a falha e para antes da conclusão

**Files:**
- Modify: `examples/java/integracao/src/main/java/br/com/srportto/exemplos/ConsumidorSqsLimitado.java:74-85`
- Test: `examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoTest.java`

**Interfaces:** API pública inalterada.

- [ ] **Step 1: Teste que falha.** Em `FilaFalsa`, acrescentar o campo e a primeira linha de `changeMessageVisibility`
(import `software.amazon.awssdk.services.sqs.model.SqsException`):

```java
        final AtomicInteger renovacoesAFalhar = new AtomicInteger();

        @Override
        public synchronized ChangeMessageVisibilityResponse changeMessageVisibility(ChangeMessageVisibilityRequest pedido) {
            // Só renovações (timeout > 0) falham; a reentrega com atraso 0 não é afetada.
            if (pedido.visibilityTimeout() > 0 && renovacoesAFalhar.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw SqsException.builder().message("falha transitória na renovação").build();
            }
            extensoes.add(pedido.visibilityTimeout());
            // ... corpo existente sem alteração
```

E o teste:

```java
    @DisplayName("ConsumidorSqsLimitado: Falha em uma renovacao nao deve interromper as seguintes")
    @Test
    void falhaEmUmaRenovacaoNaoDeveInterromperAsSeguintes() {
        fila.renovacoesAFalhar.set(1);
        // Visibilidade 1 s → renovação a cada 0,5 s; processamento de 2,5 s: a 1ª renovação falha.
        iniciar(m -> Thread.sleep(2_500), 1, Duration.ofSeconds(1));
        fila.enviar("longa");

        cicloAte(() -> fila.apagadas.contains("longa"));
        // Exceção em tarefa periódica cancela as execuções seguintes; isolada, as renovações continuam.
        long renovacoesOk = fila.extensoes.stream().filter(segundos -> segundos == 1).count();
        assertTrue(renovacoesOk >= 2, fila.extensoes::toString);
    }
```

- [ ] **Step 2:** Run: `mvn -f examples/java/pom.xml -pl integracao -am clean verify -Dtest=ConsumidorSqsLimitadoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `extensoes` sem nenhum `1` (`[]`).

- [ ] **Step 3: Implementação**

```java
    private static final System.Logger LOG = System.getLogger(ConsumidorSqsLimitado.class.getName());

    private void processar(Message mensagem) {
        long periodoMs = visibilidadeSegundos * 1000L / 2;
        // Renova antes de expirar; para quando o processamento termina (com sucesso ou falha).
        ScheduledFuture<?> renovacao = renovador.scheduleAtFixedRate(() -> renovar(mensagem),
                periodoMs, periodoMs, TimeUnit.MILLISECONDS);
        ConsumoControlado.Resultado resultado;
        try {
            resultado = controle.consumir(mensagem);
        } finally {
            // Cancela antes de publicar a conclusão: o delete não disputa com renovações ainda agendadas.
            renovacao.cancel(false);
        }
        conclusoes.add(new Conclusao(mensagem, resultado));
    }

    private void renovar(Message mensagem) {
        try {
            sqs.changeMessageVisibility(r -> r.queueUrl(filaUrl).receiptHandle(mensagem.receiptHandle())
                    .visibilityTimeout(visibilidadeSegundos));
        } catch (RuntimeException erro) {
            // Exceção não tratada cancelaria o agendamento; registra e tenta de novo no próximo período.
            LOG.log(System.Logger.Level.WARNING, "Falha ao renovar visibilidade de " + mensagem.messageId(), erro);
        }
    }
```

- [ ] **Step 4:** mesmo comando do Step 2. Expected: PASS (5 testes).
- [ ] **Step 5: Commit** — `git commit -m "fix(exemplos): renovação de visibilidade SQS resiste a falha transitória"`

---

### Task 6: `MetricasProtecao` com séries pré-registradas

**Files:**
- Modify: `examples/java/integracao/src/main/java/br/com/srportto/exemplos/MetricasProtecao.java`
- Test: `examples/java/integracao/src/test/java/br/com/srportto/exemplos/MetricasProtecaoTest.java`

**Interfaces:** API pública inalterada (`medir`, `tentativa`, `observarFila`).

- [ ] **Step 1: Teste que falha** (acrescentar; imports `io.micrometer.core.instrument.Timer`, `java.util.List`, `assertNotNull`)

```java
    @DisplayName("MetricasProtecao: Series devem existir zeradas antes do primeiro uso")
    @Test
    void seriesDevemExistirZeradasAntesDoPrimeiroUso() {
        // Série ausente vira "sem dados" no alerta; série zerada é taxa zero.
        for (String operacao : List.of("criar-pedido", "outra")) {
            for (String resultado : List.of("sucesso", "erro", "rejeitada")) {
                Timer timer = registro.find("app.requisicoes").tag("operacao", operacao).tag("resultado", resultado).timer();
                assertNotNull(timer, operacao + "/" + resultado);
                assertEquals(0, timer.count());
            }
        }
        for (String dependencia : List.of("pagamentos", "outra")) {
            for (String resultado : List.of("sucesso", "falha")) {
                assertNotNull(registro.find("app.dependencia.tentativas").tag("dependencia", dependencia)
                        .tag("resultado", resultado).counter(), dependencia + "/" + resultado);
            }
        }
    }
```

E ajustar `valoresDinamicosNaoDevemVirarTagsDeAltaCardinalidade`: medir o número de séries `app.*` **antes** do
laço e afirmar que é igual **depois** (`assertEquals(antes, depois)`), no lugar de `assertEquals(2, series)` —
a propriedade testada é "não cresce com valores dinâmicos", e o total fixo agora é 10 (6 timers + 4 counters).

- [ ] **Step 2:** Run: `mvn -f examples/java/pom.xml -pl integracao -am clean verify -Dtest=MetricasProtecaoTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL em `seriesDevemExistirZeradasAntesDoPrimeiroUso` (`criar-pedido/sucesso ==> expected: not <null>`).

- [ ] **Step 3: Implementação** (campos novos e construtor; `medir` e `tentativa` passam a consultar os mapas)

```java
    private static final List<String> RESULTADOS_REQUISICAO = List.of("sucesso", "erro", "rejeitada");
    private static final List<String> RESULTADOS_TENTATIVA = List.of("sucesso", "falha");

    private final Map<String, Map<String, Timer>> requisicoes = new HashMap<>();
    private final Map<String, Map<String, Counter>> tentativas = new HashMap<>();

    public MetricasProtecao(MeterRegistry registro, Set<String> operacoes, Set<String> dependencias) {
        this.registro = Objects.requireNonNull(registro);
        this.operacoes = Set.copyOf(operacoes);
        this.dependencias = Set.copyOf(dependencias);
        // Conjunto fechado registrado uma vez: sem builder por chamada e sem série que surge só no primeiro erro.
        for (String operacao : comOutra(this.operacoes)) {
            for (String resultado : RESULTADOS_REQUISICAO) {
                requisicoes.computeIfAbsent(operacao, o -> new HashMap<>()).put(resultado, Timer.builder("app.requisicoes")
                        .description("Requisições lógicas por operação e resultado")
                        .tag("operacao", operacao).tag("resultado", resultado)
                        .publishPercentileHistogram().register(registro));
            }
        }
        for (String dependencia : comOutra(this.dependencias)) {
            for (String resultado : RESULTADOS_TENTATIVA) {
                tentativas.computeIfAbsent(dependencia, d -> new HashMap<>()).put(resultado, Counter.builder("app.dependencia.tentativas")
                        .description("Tentativas (inclusive retries) por dependência")
                        .tag("dependencia", dependencia).tag("resultado", resultado).register(registro));
            }
        }
    }

    private static Set<String> comOutra(Set<String> nomes) {
        var todos = new HashSet<>(nomes);
        todos.add(OUTRA);
        return todos;
    }
```

No `finally` de `medir`: `requisicoes.get(tagOperacao).get(resultado).record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);`
Em `tentativa`: `tentativas.get(dependencias.contains(dependencia) ? dependencia : OUTRA).get(sucesso ? "sucesso" : "falha").increment();`
Mapas só lidos depois do construtor (publicação segura via campos `final`).

- [ ] **Step 4:** mesmo comando do Step 2. Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "refactor(exemplos): MetricasProtecao pré-registra séries de conjunto fechado"`

---

### Task 7: Floci nos exemplos executáveis

**Files:**
- Create: `validation/java/src/test/java/br/com/srportto/catalogo/EmuladorAwsLocalTest.java`
- Modify: `examples/java/integracao/pom.xml:15`
- Modify: `examples/java/integracao/src/test/java/br/com/srportto/exemplos/ServicosExternos.java:13`
- Modify: `examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java`
- Modify: `examples/java/README.md:29`

**Interfaces:**
- Produces: `ServicosExternos.FLOCI` (`DockerImageName`); `EmuladorAwsLocalTest` com método `exemplosExecutaveisDevemUsarFlociENaoLocalStack` (T08 acrescenta outro).

- [ ] **Step 1: Teste que falha**

```java
package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regra do catálogo: AWS local é emulada pelo Floci; LocalStack não volta a exemplos, skills, agents ou docs. */
class EmuladorAwsLocalTest {
    private static final Pattern LOCALSTACK = Pattern.compile("(?i)localstack");

    static List<Path> arquivos(String raiz, List<String> extensoes) {
        Path base = Catalogo.RAIZ.resolve(raiz);
        if (!Files.isDirectory(base)) return List.of();
        try (Stream<Path> caminhos = Files.walk(base)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> extensoes.stream().anyMatch(p.toString()::endsWith))
                    .sorted().toList();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    @DisplayName("EmuladorAwsLocal: Exemplos executaveis devem usar Floci e nao LocalStack")
    @Test
    void exemplosExecutaveisDevemUsarFlociENaoLocalStack() {
        var erros = new ArrayList<String>();
        for (Path arquivo : arquivos("examples/java", List.of(".java", ".xml", ".md", ".yaml", ".yml", ".properties"))) {
            if (LOCALSTACK.matcher(Catalogo.ler(arquivo)).find()) erros.add(Catalogo.relativo(arquivo));
        }
        String pom = Catalogo.ler(Catalogo.RAIZ.resolve("examples/java/integracao/pom.xml"));
        if (!pom.contains("<artifactId>testcontainers-floci</artifactId>")) erros.add("integracao/pom.xml sem testcontainers-floci");
        assertTrue(erros.isEmpty(), () -> "LocalStack/Floci:\n" + String.join("\n", erros));
    }
}
```

- [ ] **Step 2:** Run: `mvn -f validation/java/pom.xml clean verify -Dtest=EmuladorAwsLocalTest`
Expected: FAIL listando `integracao/pom.xml`, `ServicosExternos.java`, `ConsumidorSqsLimitadoExternoIT.java`, `README.md`.

- [ ] **Step 3: Trocar a dependência** em `examples/java/integracao/pom.xml` (mesma linha, mesmo estilo compacto):

```xml
<dependency><groupId>io.floci</groupId><artifactId>testcontainers-floci</artifactId><version>2.16.1</version><scope>test</scope></dependency>
```

- [ ] **Step 4: Imagem fixa** em `ServicosExternos`:

```java
    static final DockerImageName FLOCI = DockerImageName.parse("floci/floci:2.2.0");
```

- [ ] **Step 5: Trocar o container no IT** (o resto do teste não muda)

```java
import io.floci.testcontainers.FlociContainer;
import java.net.URI;
// ...
/**
 * SQS real (Floci): mensagem processada é apagada; mensagem que sempre falha vai para a DLQ pela
 * RedrivePolicy (maxReceiveCount=3), sem descarte silencioso e sem loop infinito.
 */
class ConsumidorSqsLimitadoExternoIT {
    static final FlociContainer FLOCI = new FlociContainer(ServicosExternos.FLOCI);
    // ...
    @BeforeAll
    static void iniciar() {
        FLOCI.start();
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(FLOCI.getEndpoint()))
                .region(Region.of(FLOCI.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
                .build();
        // ... criação de DLQ e fila sem alteração
    }

    @AfterAll
    static void parar() {
        if (sqs != null) sqs.close();
        FLOCI.stop();
    }
```

README dos exemplos, linha da tabela: `| ConsumidorSqsLimitadoExternoIT | Floci (SQS) | ... |`; na coluna de dependências do
módulo `integracao`, "Testcontainers (Floci para AWS) nos `ExternoIT`".

- [ ] **Step 6:** Run: `mvn -f validation/java/pom.xml clean verify -Dtest=EmuladorAwsLocalTest` → Expected: PASS.
- [ ] **Step 7: Prova contra o container real (Docker obrigatório)**

Run: `mvn -f examples/java/pom.xml -Pintegracao clean verify -Dit.test=ConsumidorSqsLimitadoExternoIT -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: unitários PASS e `ConsumidorSqsLimitadoExternoIT` PASS (1 teste). Se o container não iniciar por CPU,
trocar a tag para `2.2.0-compat` e registrar decisão. Se a DLQ divergir (Review Focus 4), registrar a saída e a
decisão no ledger — não afrouxar a asserção sem decisão registrada.

- [ ] **Step 8: Commit** — `git commit -m "test(exemplos): SQS de integração com Floci no lugar de LocalStack"`

---

### Task 8: Floci nas skills e nas convenções

**Files:**
- Modify: `skills/mensageria-sqs-kafka/SKILL.md:80-89`
- Modify: `skills/mensageria-sqs-kafka/references/controle-consumo-java.md:71`
- Modify: `skills/criar-aplicacao-java/SKILL.md:169-175` e tabela de problemas (linha do `SdkClientException`)
- Modify: `skills/testes-sistemas-java/references/integracao-carga.md:5`
- Modify: `skills/devops-cicd/SKILL.md:31-32`
- Modify: `skills/terraform-engineer/references/providers.md:98-101`
- Modify: `docs/catalogo/convencoes.md`, `docs/catalogo/compatibilidade.md:30,40,50`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/EmuladorAwsLocalTest.java`

**Interfaces:** consome `EmuladorAwsLocalTest.arquivos(String, List<String>)` (T07).

- [ ] **Step 1: Teste que falha** (acrescentar em `EmuladorAwsLocalTest`)

```java
    /** Frase de substituição explícita ("em vez de LocalStack") é permitida; uso como ferramenta, não. */
    private static final Pattern SUBSTITUICAO = Pattern.compile("(?i)(em vez de|ao invés de|não use|nunca)\\s+(o\\s+)?localstack");

    @DisplayName("EmuladorAwsLocal: Skills agents e docs do catalogo devem indicar Floci")
    @Test
    void skillsAgentsEDocsDoCatalogoDevemIndicarFloci() {
        var erros = new ArrayList<String>();
        for (String raiz : List.of("skills", "agents", "docs/catalogo")) {
            for (Path arquivo : arquivos(raiz, List.of(".md", ".yaml", ".yml"))) {
                String texto = Catalogo.ler(arquivo);
                texto.lines().filter(linha -> LOCALSTACK.matcher(linha).find())
                        .filter(linha -> !SUBSTITUICAO.matcher(linha).find())
                        .forEach(linha -> erros.add(Catalogo.relativo(arquivo) + ": " + linha.strip()));
            }
        }
        String convencoes = Catalogo.ler(Catalogo.RAIZ.resolve("docs/catalogo/convencoes.md"));
        if (!convencoes.contains("Floci")) erros.add("convencoes.md sem a regra de AWS local (Floci)");
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
```

- [ ] **Step 2:** Run: `mvn -f validation/java/pom.xml clean verify -Dtest=EmuladorAwsLocalTest`
Expected: FAIL listando `mensageria-sqs-kafka/SKILL.md`, `controle-consumo-java.md`, `compatibilidade.md` e `convencoes.md sem a regra`.

- [ ] **Step 3: `mensageria-sqs-kafka/SKILL.md`** — substituir a frase "Via AWS CLI (LocalStack), ..." e o bloco por:

```markdown
Localmente, use o **Floci** (emulador AWS, porta 4566): `docker run -d -p 4566:4566 floci/floci:2.2.0`. Com
`AWS_ENDPOINT_URL=http://localhost:4566`, `AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test` e
`AWS_DEFAULT_REGION=us-east-1`, AWS CLI e AWS SDK v2 apontam para ele sem mudança de código. A mesma regra: criar
a DLQ, obter o ARN e só então criar a fila com `RedrivePolicy`:
```

seguido do bloco `bash` existente com `aws --endpoint-url=http://localhost:4566 ...` inalterado.

- [ ] **Step 4: Demais arquivos** — substituições literais:
  - `controle-consumo-java.md:71`: `(LocalStack: mensagem ...` → `(Floci: mensagem ...`.
  - `criar-aplicacao-java/SKILL.md` passo 2: "IaC local, ex. Terraform contra o emulador" → "IaC local, ex. Terraform contra o Floci em `http://localhost:4566`"; passo 3, após "Testcontainers (Docker)": "— para SQS e demais serviços AWS, `FlociContainer` (`io.floci:testcontainers-floci`)"; tabela de problemas: "Variante com SQS exige o emulador rodando" → "Variante com SQS exige o Floci rodando (`docker run -d -p 4566:4566 floci/floci:2.2.0`)".
  - `integracao-carga.md`, fim do parágrafo do perfil integração: "Serviços AWS (SQS, S3...) rodam no Floci via `FlociContainer`."
  - `devops-cicd/SKILL.md` passo 1: após `docker-compose.yml`, acrescentar "(dependência AWS local no compose: serviço `floci/floci:2.2.0` na porta 4566)".
  - `terraform-engineer/references/providers.md`: comentário `# Floci (emulador AWS local) — só em ambiente local` acima de `endpoints {` e acrescentar `sqs = "http://localhost:4566"`.
  - `convencoes.md`, nova seção:

```markdown
## AWS local

Serviços AWS em desenvolvimento e testes usam o **Floci** (`floci/floci`, porta 4566, credenciais `test`/`test`):
`docker run` ou compose localmente e `FlociContainer` (`io.floci:testcontainers-floci`) nos testes de integração.
Versões fixadas em [compatibilidade](compatibilidade.md).
```

  - `compatibilidade.md`: lista de módulos Testcontainers `(core, postgresql, kafka, toxiproxy)` + linha `| testcontainers-floci | 2.16.1 | fixada (usa Testcontainers 2.0.5) |`; linha de imagem `| SQS (AWS) | floci/floci:2.2.0 | alternativa 2.2.0-compat se a CPU não suportar a imagem nativa |`; nas execuções, "SQS/Floci". O registro histórico da licença do LocalStack fica só no arquivo de decisões (fora da trilha).

- [ ] **Step 5:** Run: `mvn -f validation/java/pom.xml clean verify` → Expected: PASS em todos.
- [ ] **Step 6: Commit** — `git commit -m "docs(skills): AWS local com Floci"`

---

### Task 9: Módulo `linguagem` — sealed e switch executáveis

**Files:**
- Modify: `examples/java/pom.xml:4` (módulo novo)
- Create: `examples/java/linguagem/pom.xml`
- Create: `examples/java/linguagem/src/main/java/br/com/srportto/exemplos/{Pagamento,Tarifacao,ResultadoCobranca,Notificacao}.java`
- Create: `examples/java/linguagem/src/test/java/br/com/srportto/exemplos/{Pagamento,Tarifacao,ResultadoCobranca,Notificacao}Test.java`

**Interfaces:**
- Produces: `sealed interface Pagamento` com `Pix(String chave, BigDecimal valor)`, `Cartao(Bandeira bandeira, int parcelas, BigDecimal valor)`, `Boleto(String linhaDigitavel, BigDecimal valor)`, `enum Bandeira`; `Tarifacao.taxa(Pagamento): BigDecimal`, `Tarifacao.prazoEstorno(Tarifacao.Canal): Duration`; `ResultadoCobranca.proximaAcao(ResultadoCobranca): ResultadoCobranca.Acao`; `Notificacao.destino(Notificacao): String`. T10 cita estes caminhos.

- [ ] **Step 1: POMs** — `examples/java/pom.xml`: `<modules>` passa a `fundamentos, linguagem, reativo, integracao, carga`.
`examples/java/linguagem/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><parent><groupId>br.com.srportto.exemplos</groupId><artifactId>exemplos-java</artifactId><version>1.0.0</version></parent><artifactId>linguagem</artifactId></project>
```

- [ ] **Step 2: Testes que falham**

`PagamentoTest.java`:

```java
package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagamentoTest {
    @TempDir Path destino;

    /** Compila em memória com --release 25 (sem preview) e devolve as mensagens de erro. */
    private List<String> compilar(String fonte) {
        var diagnosticos = new DiagnosticCollector<JavaFileObject>();
        var arquivo = new SimpleJavaFileObject(URI.create("string:///Uso.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignorarErros) { return fonte; }
        };
        ToolProvider.getSystemJavaCompiler()
                .getTask(null, null, diagnosticos, List.of("--release", "25", "-d", destino.toString()), null, List.of(arquivo))
                .call();
        return diagnosticos.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ROOT)).toList();
    }

    private static String hierarquiaCom(String casos) {
        return """
                sealed interface Meio permits Pix, Cartao, Boleto {}
                record Pix() implements Meio {}
                record Cartao() implements Meio {}
                record Boleto() implements Meio {}
                class Uso {
                    int taxa(Meio meio) {
                        return switch (meio) {
                %s
                        };
                    }
                }
                """.formatted(casos);
    }

    @DisplayName("Pagamento: Hierarquia selada deve permitir somente os tipos declarados")
    @Test
    void hierarquiaSeladaDevePermitirSomenteOsTiposDeclarados() {
        assertTrue(Pagamento.class.isSealed());
        assertEquals(Set.of(Pagamento.Pix.class, Pagamento.Cartao.class, Pagamento.Boleto.class),
                Set.of(Pagamento.class.getPermittedSubclasses()));
    }

    @DisplayName("Pagamento: Valor nao positivo deve ser rejeitado na construcao")
    @Test
    void valorNaoPositivoDeveSerRejeitadoNaConstrucao() {
        assertThrows(IllegalArgumentException.class, () -> new Pagamento.Pix("chave", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Pagamento.Cartao(Pagamento.Bandeira.ELO, 13, BigDecimal.TEN));
    }

    @DisplayName("Pagamento: Switch sem default deve quebrar a compilacao quando falta um subtipo")
    @Test
    void switchSemDefaultDeveQuebrarACompilacaoQuandoFaltaUmSubtipo() {
        // Controle positivo: todos os subtipos tratados compila sem erro.
        assertEquals(List.of(), compilar(hierarquiaCom("case Pix p -> 0; case Cartao c -> 3; case Boleto b -> 2;")));
        List<String> erros = compilar(hierarquiaCom("case Pix p -> 0; case Cartao c -> 3;"));
        assertTrue(erros.stream().anyMatch(e -> e.contains("does not cover all possible input values")), erros::toString);
    }

    @DisplayName("Pagamento: Caso geral antes do especifico deve ser erro de dominancia")
    @Test
    void casoGeralAntesDoEspecificoDeveSerErroDeDominancia() {
        List<String> erros = compilar(hierarquiaCom("case Meio m -> 1; case Pix p -> 0;"));
        assertTrue(erros.stream().anyMatch(e -> e.contains("dominated")), erros::toString);
    }
}
```

`TarifacaoTest.java`:

```java
package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TarifacaoTest {
    private static final BigDecimal CEM = new BigDecimal("100.00");

    @DisplayName("Tarifacao: Taxa deve depender do tipo e dos componentes do pagamento")
    @Test
    void taxaDeveDependerDoTipoEDosComponentesDoPagamento() {
        assertEquals(BigDecimal.ZERO, Tarifacao.taxa(new Pagamento.Pix("chave", CEM)));
        assertEquals(new BigDecimal("2.99"), Tarifacao.taxa(new Pagamento.Cartao(Pagamento.Bandeira.VISA, 1, CEM)));
        // Guarda "when parcelas > 1": 2,99% + 1% por parcela adicional.
        assertEquals(new BigDecimal("4.99"), Tarifacao.taxa(new Pagamento.Cartao(Pagamento.Bandeira.VISA, 3, CEM)));
        assertEquals(new BigDecimal("2.50"), Tarifacao.taxa(new Pagamento.Boleto("linha", CEM)));
    }

    @DisplayName("Tarifacao: Pagamento nulo deve ser tratado pelo case null")
    @Test
    void pagamentoNuloDeveSerTratadoPeloCaseNull() {
        var erro = assertThrows(IllegalArgumentException.class, () -> Tarifacao.taxa(null));
        assertEquals("Pagamento ausente", erro.getMessage());
    }

    @DisplayName("Tarifacao: Prazo de estorno deve usar rotulos multiplos e yield")
    @Test
    void prazoDeEstornoDeveUsarRotulosMultiplosEYield() {
        assertEquals(Duration.ofDays(1), Tarifacao.prazoEstorno(Tarifacao.Canal.APP));
        assertEquals(Duration.ofDays(1), Tarifacao.prazoEstorno(Tarifacao.Canal.WEB));
        assertEquals(Duration.ofDays(3), Tarifacao.prazoEstorno(Tarifacao.Canal.LOJA));
        assertEquals(Duration.ofDays(5), Tarifacao.prazoEstorno(Tarifacao.Canal.TELEFONE));
    }
}
```

`ResultadoCobrancaTest.java`:

```java
package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.srportto.exemplos.ResultadoCobranca.Acao.CONCLUIR;
import static br.com.srportto.exemplos.ResultadoCobranca.Acao.INFORMAR_RECUSA;
import static br.com.srportto.exemplos.ResultadoCobranca.Acao.OFERECER_OUTRO_MEIO;
import static br.com.srportto.exemplos.ResultadoCobranca.Acao.RECONCILIAR;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultadoCobrancaTest {
    @DisplayName("ResultadoCobranca: Cada resultado deve levar a uma acao explicita")
    @Test
    void cadaResultadoDeveLevarAUmaAcaoExplicita() {
        assertEquals(CONCLUIR, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Aprovada("aut-1")));
        assertEquals(OFERECER_OUTRO_MEIO, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Recusada("SALDO_INSUFICIENTE")));
        assertEquals(INFORMAR_RECUSA, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Recusada("CARTAO_BLOQUEADO")));
    }

    @DisplayName("ResultadoCobranca: Resultado desconhecido deve reconciliar e nunca repetir a cobranca")
    @Test
    void resultadoDesconhecidoDeveReconciliarENuncaRepetirACobranca() {
        assertEquals(RECONCILIAR, ResultadoCobranca.proximaAcao(new ResultadoCobranca.Desconhecida("chave-123")));
    }
}
```

`NotificacaoTest.java`:

```java
package br.com.srportto.exemplos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacaoTest {
    /** Possível só porque Webhook é non-sealed: a hierarquia reabre a partir dele. */
    static final class WebhookAssinado extends Notificacao.Webhook {
        WebhookAssinado(URI url, String mensagem) { super(url, mensagem); }
    }

    @DisplayName("Notificacao: Classe selada deve declarar subclasses final e non-sealed")
    @Test
    void classeSeladaDeveDeclararSubclassesFinalENonSealed() {
        assertTrue(Notificacao.class.isSealed());
        assertEquals(Set.of(Notificacao.Email.class, Notificacao.Webhook.class),
                Set.of(Notificacao.class.getPermittedSubclasses()));
        assertFalse(Notificacao.Webhook.class.isSealed());
    }

    @DisplayName("Notificacao: Switch exaustivo deve cobrir subclasses de tipo non-sealed")
    @Test
    void switchExaustivoDeveCobrirSubclassesDeTipoNonSealed() {
        assertEquals("email:ana@exemplo.com", Notificacao.destino(new Notificacao.Email("ana@exemplo.com", "oi")));
        assertEquals("webhook:https://exemplo.com/h",
                Notificacao.destino(new WebhookAssinado(URI.create("https://exemplo.com/h"), "oi")));
    }
}
```

- [ ] **Step 3:** Run: `mvn -f examples/java/pom.xml -pl linguagem clean verify`
Expected: FAIL de compilação — `cannot find symbol: class Pagamento` (e demais).

- [ ] **Step 4: Implementação**

`Pagamento.java`:

```java
package br.com.srportto.exemplos;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Meios de pagamento como hierarquia selada. Os records aninhados são os únicos subtipos ({@code permits}
 * implícito por estarem no mesmo arquivo) e, sendo {@code final}, fecham a hierarquia aqui.
 */
public sealed interface Pagamento {
    BigDecimal valor();

    record Pix(String chave, BigDecimal valor) implements Pagamento {
        public Pix {
            Objects.requireNonNull(chave);
            exigirPositivo(valor);
        }
    }

    record Cartao(Bandeira bandeira, int parcelas, BigDecimal valor) implements Pagamento {
        public Cartao {
            Objects.requireNonNull(bandeira);
            if (parcelas < 1 || parcelas > 12) throw new IllegalArgumentException("Parcelas fora de 1..12: " + parcelas);
            exigirPositivo(valor);
        }
    }

    record Boleto(String linhaDigitavel, BigDecimal valor) implements Pagamento {
        public Boleto {
            Objects.requireNonNull(linhaDigitavel);
            exigirPositivo(valor);
        }
    }

    enum Bandeira { VISA, MASTERCARD, ELO }

    private static void exigirPositivo(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Valor deve ser positivo");
    }
}
```

`Tarifacao.java`:

```java
package br.com.srportto.exemplos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/** Switch moderno: padrões de tipo e de record, guarda {@code when}, {@code case null}, {@code _}, rótulos múltiplos e yield. */
public final class Tarifacao {
    private static final BigDecimal TAXA_CARTAO = new BigDecimal("0.0299");
    private static final BigDecimal ACRESCIMO_POR_PARCELA = new BigDecimal("0.0100");
    private static final BigDecimal TARIFA_BOLETO = new BigDecimal("2.50");

    public enum Canal { APP, WEB, LOJA, TELEFONE }

    private Tarifacao() {}

    /** Exaustivo sobre o tipo selado e sem default: um subtipo novo de Pagamento quebra a compilação aqui. */
    public static BigDecimal taxa(Pagamento pagamento) {
        return switch (pagamento) {
            // Sem "case null", um pagamento nulo lançaria NullPointerException.
            case null -> throw new IllegalArgumentException("Pagamento ausente");
            case Pagamento.Pix _ -> BigDecimal.ZERO;
            // A guarda refina o padrão e precisa vir antes do caso sem guarda do mesmo tipo (dominância).
            case Pagamento.Cartao(_, int parcelas, BigDecimal valor) when parcelas > 1 ->
                    percentual(valor, TAXA_CARTAO.add(ACRESCIMO_POR_PARCELA.multiply(BigDecimal.valueOf(parcelas - 1))));
            case Pagamento.Cartao(_, _, BigDecimal valor) -> percentual(valor, TAXA_CARTAO);
            case Pagamento.Boleto _ -> TARIFA_BOLETO;
        };
    }

    /** Switch sobre enum sem default: exaustivo; adicionar um Canal sem tratá-lo não compila. */
    public static Duration prazoEstorno(Canal canal) {
        return switch (canal) {
            case APP, WEB -> Duration.ofDays(1);
            case LOJA -> Duration.ofDays(3);
            case TELEFONE -> {
                // Ramo com mais de uma instrução devolve o valor com yield.
                Duration analiseManual = Duration.ofDays(2);
                yield analiseManual.plus(Duration.ofDays(3));
            }
        };
    }

    private static BigDecimal percentual(BigDecimal valor, BigDecimal taxa) {
        return valor.multiply(taxa).setScale(2, RoundingMode.HALF_EVEN);
    }
}
```

`ResultadoCobranca.java`:

```java
package br.com.srportto.exemplos;

/** Resultado de uma cobrança remota; o tipo selado obriga quem consome a decidir o caso "desconhecido". */
public sealed interface ResultadoCobranca {
    record Aprovada(String autorizacao) implements ResultadoCobranca {}

    record Recusada(String motivo) implements ResultadoCobranca {}

    /** Timeout ou queda sem resposta: a cobrança pode ter acontecido no provedor. */
    record Desconhecida(String chaveIdempotencia) implements ResultadoCobranca {}

    enum Acao { CONCLUIR, OFERECER_OUTRO_MEIO, INFORMAR_RECUSA, RECONCILIAR }

    static Acao proximaAcao(ResultadoCobranca resultado) {
        return switch (resultado) {
            case Aprovada _ -> Acao.CONCLUIR;
            case Recusada(String motivo) when "SALDO_INSUFICIENTE".equals(motivo) -> Acao.OFERECER_OUTRO_MEIO;
            case Recusada _ -> Acao.INFORMAR_RECUSA;
            // Nunca repetir às cegas: consultar o provedor pela chave idempotente antes de qualquer nova tentativa.
            case Desconhecida _ -> Acao.RECONCILIAR;
        };
    }
}
```

`Notificacao.java`:

```java
package br.com.srportto.exemplos;

import java.net.URI;
import java.util.Objects;

/** Classe abstrata selada: cada subclasse escolhe fechar ({@code final}) ou reabrir ({@code non-sealed}). */
public abstract sealed class Notificacao permits Notificacao.Email, Notificacao.Webhook {
    private final String mensagem;

    protected Notificacao(String mensagem) {
        this.mensagem = Objects.requireNonNull(mensagem);
    }

    public String mensagem() {
        return mensagem;
    }

    public static final class Email extends Notificacao {
        private final String endereco;

        public Email(String endereco, String mensagem) {
            super(mensagem);
            this.endereco = Objects.requireNonNull(endereco);
        }

        public String endereco() {
            return endereco;
        }
    }

    /** non-sealed: integrações podem estender (ex.: webhook assinado) sem alterar esta hierarquia. */
    public static non-sealed class Webhook extends Notificacao {
        private final URI url;

        public Webhook(URI url, String mensagem) {
            super(mensagem);
            this.url = Objects.requireNonNull(url);
        }

        public URI url() {
            return url;
        }
    }

    /** Exaustivo sem default: o caso Webhook cobre também as subclasses dele. */
    public static String destino(Notificacao notificacao) {
        return switch (notificacao) {
            case Email email -> "email:" + email.endereco();
            case Webhook webhook -> "webhook:" + webhook.url();
        };
    }
}
```

- [ ] **Step 5:** Run: `mvn -f examples/java/pom.xml -pl linguagem clean verify`
Expected: PASS, 11 testes. Se a mensagem do javac divergir de `does not cover all possible input values` ou
`dominated`, copiar a mensagem real da saída para a asserção e registrar no ledger.

- [ ] **Step 6:** Run: `mvn -f validation/java/pom.xml clean verify` — `cadaExemploDeveTerProvaComMesmoNome` deve
continuar PASS (cada classe tem `<Classe>Test`). Atualizar `examples/java/README.md`, tabela de módulos:
`| linguagem | Pagamento, Tarifacao, ResultadoCobranca, Notificacao | somente JDK |`, e a árvore em `skills/README.md`
(`fundamentos, linguagem, reativo, integracao, carga`).
- [ ] **Step 7: Commit** — `git commit -m "feat(exemplos): módulo linguagem com sealed e switch executáveis"`

---

### Task 10: Skill `java-moderno` — sealed classes e switch moderno

**Files:**
- Create: `skills/java-moderno/references/sealed-e-switch.md`
- Modify: `skills/java-moderno/SKILL.md` (seções 2–4, linha 23, linhas 55–56, nota JDK 25, metadata)
- Modify: `skills/criar-aplicacao-java/SKILL.md` (linha do `void main()` na tabela de problemas), conforme Step 5
- Create: `validation/java/src/test/java/br/com/srportto/catalogo/JavaModernoTest.java`

**Interfaces:** consome os caminhos de T09.

- [ ] **Step 1: Teste que falha**

```java
package br.com.srportto.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A skill java-moderno cobre sealed e switch moderno e aponta para a fonte executável do módulo linguagem. */
class JavaModernoTest {
    @DisplayName("JavaModerno: Referencia deve cobrir sealed e switch com fonte executavel")
    @Test
    void referenciaDeveCobrirSealedESwitchComFonteExecutavel() {
        String texto = Catalogo.ler(Catalogo.RAIZ.resolve("skills/java-moderno/references/sealed-e-switch.md"));
        var erros = new ArrayList<String>();
        for (String termo : List.of("permits", "non-sealed", "final", "getPermittedSubclasses", "case null",
                "when", "yield", "case APP, WEB", "_", "domina", "exaustiv", "JEP 507", "preview")) {
            if (!texto.contains(termo)) erros.add("sem '" + termo + "'");
        }
        for (String classe : List.of("Pagamento", "Tarifacao", "ResultadoCobranca", "Notificacao")) {
            if (!texto.contains("examples/java/linguagem/src/main/java/br/com/srportto/exemplos/" + classe + ".java")) {
                erros.add("sem link para " + classe);
            }
        }
        String skill = Catalogo.ler(Catalogo.RAIZ.resolve("skills/java-moderno/SKILL.md"));
        if (!skill.contains("references/sealed-e-switch.md")) erros.add("SKILL.md não aponta a referência");
        if (skill.contains("4.0.4")) erros.add("SKILL.md ainda cita Spring Boot 4.0.4");
        assertTrue(erros.isEmpty(), () -> String.join("\n", erros));
    }
}
```

- [ ] **Step 2:** Run: `mvn -f validation/java/pom.xml clean verify -Dtest=JavaModernoTest`
Expected: FAIL — `NoSuchFileException: .../sealed-e-switch.md`.

- [ ] **Step 3: Criar `references/sealed-e-switch.md`** com estas seções (trechos = cópias do módulo `linguagem`, cada
seção termina com o link relativo `../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/<Classe>.java`):

1. **Sealed: o que fecha e por quê** — `sealed`/`permits`; `permits` omitido no mesmo arquivo (trecho de `Pagamento`);
   modificadores obrigatórios das subclasses: `final`, `sealed` ou `non-sealed` (trecho de `Notificacao`); records e
   enums como folhas naturais (implicitamente `final`); restrição de pacote/módulo (subclasses no mesmo pacote, ou no
   mesmo módulo nomeado); introspecção com `Class.isSealed()` e `getPermittedSubclasses()`. Quando evitar: tipo
   estendido por plugins de terceiros.
2. **Modelar resultados com tipo selado** — `ResultadoCobranca` (o caso `Desconhecida` obriga reconciliar em vez de
   repetir a cobrança; liga com `resiliencia-controle-fluxo-java`).
3. **Switch moderno — tabela de referência** com colunas *Recurso | Desde | Exemplo*: rótulo `->` sem fallthrough
   (14); rótulos múltiplos `case APP, WEB ->` (14); `yield` (14); switch expression exaustivo sobre enum sem
   `default` (14); padrão de tipo `case Pix p` (21, JEP 441); padrão de record `case Cartao(_, int parcelas, BigDecimal valor)`
   (21, JEP 440); guarda `when` (21); `case null` e `case null, default` (21); constantes de enum qualificadas em
   rótulos (21); variável/padrão sem nome `_` (22, JEP 456); padrões primitivos em `switch`/`instanceof` (JEP 507 —
   **preview no Java 25, não usar**: o catálogo compila sem `--enable-preview`).
4. **Regras que o compilador impõe** — exaustividade (switch expression e todo switch com padrões precisa cobrir
   todos os casos; com tipo selado, sem `default`); dominância (caso mais geral antes do específico é erro; guarda
   antes do caso sem guarda); `case null` (sem ele, `null` lança `NullPointerException`). Citar
   [PagamentoTest](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java), que
   compila os erros em memória.
5. **Armadilhas** — `default` num switch sobre tipo selado esconde subtipos novos (o build deixa de avisar);
   `instanceof` + cast manual continua compilando mas perde a verificação; pattern matching decide por tipo
   concreto e não substitui polimorfismo quando o comportamento pertence ao tipo.

- [ ] **Step 4: Ajustar `SKILL.md`**
  - Linha 23: `Spring Boot 4.0.4` → `Spring Boot 4.0.7`.
  - Linhas 55–56: trocar a frase sobre `StatusAplicacao`/`IdAutorizacao`/`docs/based-java-aplication.md` por:
    "Records executáveis neste catálogo: `Pagamento.Pix`, `Pagamento.Cartao`, `ResultadoCobranca.Aprovada` (módulo
    [linguagem](../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java))."
  - Seção 2 (sealed): acrescentar ao fim "Modificadores das subclasses (`final`, `sealed`, `non-sealed`), classe
    abstrata selada e introspecção: [sealed-e-switch](references/sealed-e-switch.md)."
  - Seção 3: incluir o trecho de `Tarifacao.taxa` (padrão de record, `when`, `case null`, `_`) no lugar do exemplo
    `case Pix p -> ...` atual.
  - Seção 4: no exemplo de `status`, remover `default` e explicar "enum tratado por completo dispensa `default`; com
    `default`, um valor novo do enum passa sem aviso"; acrescentar rótulos múltiplos (`case APP, WEB ->`).
  - Seção 8, linha do Java 21: "o 21 cobre quase tudo; do 22 em diante vem `_` (JEP 456, final no 22)".
  - `metadata.version` → `"1.2.0"`; `triggers` acrescenta `switch pattern, when, case null, non-sealed, record patterns`.

- [ ] **Step 5: Verificar a afirmação sobre `void main()` antes de mantê-la**

```bash
# Projeto mínimo em $SCRATCH/main-instancia: pom com spring-boot-starter 4.0.7 + spring-boot-maven-plugin.
# Classe:
#   @SpringBootApplication public class Aplicacao { void main() { SpringApplication.run(Aplicacao.class); } }
mvn -q -f "$SCRATCH/main-instancia/pom.xml" clean package && java -jar "$SCRATCH/main-instancia/target/"*.jar
```
Expected: um dos dois resultados, registrado no ledger com a saída:
  - **Falha** (plugin não acha a main ou o jar não sobe): manter a nota da seção 8 e a linha da tabela de
    `criar-aplicacao-java`, acrescentando "verificado em 2026-10-06 com Spring Boot 4.0.7".
  - **Funciona** (log `Started Aplicacao`): trocar a nota por "o entrypoint `void main()` do JDK 25 funciona com o
    plugin 4.0.7 (verificado em 2026-10-06); o catálogo mantém `public static void main(String[] args)` por
    compatibilidade com ferramentas que ainda procuram a assinatura clássica" e remover a linha da tabela de
    problemas de `criar-aplicacao-java`.

- [ ] **Step 6:** Run: `mvn -f validation/java/pom.xml clean verify` → Expected: PASS em todos (links da referência
resolvem; nenhum bloco em linguagem proibida).
- [ ] **Step 7: Commit** — `git commit -m "docs(java-moderno): sealed classes e switch moderno com fonte executável"`

---

### Task 11: Consolidação, verificação completa e registro de decisões

**Files:**
- Modify: `docs/catalogo/matriz-cobertura.md` (linha ENG: acrescentar `openspec-catalogo-java` e as provas de `linguagem`)
- Modify: `docs/catalogo/compatibilidade.md` (tabela de execuções)
- Modify: `docs/catalogo/convencoes.md` (seção curta "Fluxo OpenSpec": instalação com `openspec init --tools none` e link para a skill)
- Create: `docs/superpowers/plans/2026-10-06-opsx-floci-java-moderno-decisoes.md`

- [ ] **Step 1: Suíte completa sem Docker**

Run: `mvn -f validation/java/pom.xml clean verify` e `mvn -f examples/java/pom.xml clean verify`
Expected: PASS. Contagens esperadas: validação 20 + 7 novos (`OpenSpecIntegracaoTest` 4, `EmuladorAwsLocalTest` 2,
`JavaModernoTest` 1) = 27; exemplos 96 + 11 (`linguagem`) + 2 (`CacheProtegido`) + 1 (SQS)
+ 1 (métricas) + 1 (deadline) = 112. Divergência de contagem → investigar antes de seguir.

- [ ] **Step 2: Integração com Docker**

Run: `mvn -f examples/java/pom.xml -Pintegracao clean verify`
Expected: unitários + 7 `ExternoIT` PASS (agora com Floci). Sem Docker: registrar como pendência.

- [ ] **Step 3: Ponta a ponta do OpenSpec** — repetir T01 Step 7 com o catálogo instalado numa pasta de teste e
rodar `/opsx:propose` sobre uma change pequena ("endpoint de saúde detalhado") **apenas se o usuário autorizar o
gasto**; sem autorização, registrar a verificação do CLI (rules em `openspec instructions`) como a prova disponível.

- [ ] **Step 4: Atualizar `compatibilidade.md`** com as contagens reais dos Steps 1–2 e a data; e registrar em
`...-decisoes.md`: alteração de auxiliares (`openspec-*`, `terraform-engineer`) por pedido explícito; frontmatter
gerado das `openspec-*` mantido como veio (inclusive o `---` duplicado) para facilitar comparar com o upstream;
semântica nova do 5º parâmetro de `CacheProtegido`; troca do teste de cardinalidade de `MetricasProtecao`; resultado
do `void main()`; tag Floci usada; histórico "LocalStack `latest` exigia licença → Floci".

- [ ] **Step 5: Commit** — `git commit -m "docs(catalogo): consolida OpenSpec, Floci e Java moderno"`

---

## Fora de escopo (registrado para não se perder)

- Correções de agents apontadas pela avaliação (A05, A07, A08 com nota 1) e casos A02, A04, A06, A09–A12 pendentes:
  exigem sessões independentes; plano próprio.
- Schema OpenSpec próprio (`openspec schema fork`): adiado — `config.yaml` + skill cobrem o pedido sem manter uma
  cópia dos templates. Reavaliar se a fase `apply` precisar de instrução no CLI.
- Primeira execução do workflow no GitHub Actions.
