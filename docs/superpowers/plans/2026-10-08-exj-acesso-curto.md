# Plano de implementação: acesso curto EXJ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expor o catálogo Java sob a marca EXJ, com entrada `$exj`, plugin `exj` e comandos curtos `/exj:*`.

**Architecture:** Adicionar uma skill de roteamento que seleciona uma skill ou agent Java existente sem duplicar seu conteúdo. Mover os sete comandos próprios para `commands/exj/`, atualizar os identificadores dos manifestos e marketplace e alinhar documentação e testes.

**Tech Stack:** Markdown de skills e comandos, JSON dos manifestos, Java 25/JUnit 5 na validação do catálogo, Maven.

**Spec:** `docs/superpowers/specs/2026-10-08-exj-acesso-curto-design.md`

## Global Constraints

- Manter os IDs de skills especializadas, agents e comandos `/opsx:*`.
- Usar `exj` minúsculo como slug técnico e **EXJ — Especialista em tudo Java** como marca.
- Manter comandos finos, delegando às skills e agents em vez de repetir seus procedimentos.
- Escrever textos e comentários em português do Brasil.
- Não reescrever ferramentas auxiliares do catálogo.

## Review Focus

- Instalação Codex com nome antigo: exemplos de marketplace e `config.toml` precisam apontar para `exj` (Task 4).
- Marketplace Claude com slug divergente: o nome do plugin deve coincidir nos dois manifestos e no registro marketplace (Task 4).
- Entrada `$exj` roteando para a capacidade errada: casos de avaliação devem cobrir criação, revisão e arquitetura, além de recusar tarefas fora do escopo Java (Task 2).
- Comandos renomeados ainda citando skills ou agents ausentes: o teste atual de nomes citados deve continuar cobrindo todos os arquivos em `commands/` (Task 3).
- Namespace antigo ainda documentado como comando suportado: inventário, README e instruções locais devem listar apenas `/exj:*` para os sete fluxos renomeados (Tasks 3 e 4).

---

### Task 1: Fixar os contratos dos manifestos e comandos

**Files:**
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/PluginManifestoTest.java`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/ComandosCatalogoTest.java`

**Interfaces:**
- Consumes: os caminhos de manifesto e comandos já usados pelos testes atuais.
- Produces: asserções que definem `exj`, `srportto-exj` e os sete arquivos do namespace `/exj`.

- [ ] **Step 1: Escrever as asserções novas**

Em `PluginManifestoTest.manifestoCodexDeclaraAsSkillsDoCatalogo`, trocar a expectativa `catalogo-java` por `exj`. Acrescentar ao teste de marketplace a igualdade `assertEquals("srportto-exj", marketplace.get("name"))`.

Em `ComandosCatalogoTest`, adicionar um teste que verifique estes arquivos com `Files.isRegularFile(Catalogo.RAIZ.resolve(caminho))`: `commands/exj/app.md`, `feat.md`, `ref.md`, `rev.md`, `adr.md`, `av.md` e `val.md`. O mesmo teste deve percorrer `commands/` e falhar se encontrar arquivos em `commands/java/`, `commands/arq/` ou `commands/catalogo/`.

Exemplo do teste de inventário:

```java
@Test
void comandosPublicosUsamNamespaceExj() throws IOException {
    var esperados = List.of("app", "feat", "ref", "rev", "adr", "av", "val");
    for (var nome : esperados) {
        assertTrue(Files.isRegularFile(Catalogo.RAIZ.resolve("commands/exj/" + nome + ".md")), nome);
    }
    try (var arquivos = Files.walk(Catalogo.RAIZ.resolve("commands"))) {
        assertTrue(arquivos.map(Catalogo::relativo).noneMatch(p -> p.startsWith("commands/java/")
                || p.startsWith("commands/arq/")
                || p.startsWith("commands/catalogo/")));
    }
}
```

- [ ] **Step 2: Executar os testes direcionados e confirmar a falha**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=PluginManifestoTest,ComandosCatalogoTest test`
Expected: FAIL porque os manifestos e os caminhos de comando ainda usam `catalogo-java` e os namespaces antigos.

---

### Task 2: Criar a skill de entrada EXJ

**Files:**
- Create: `skills/exj/SKILL.md`
- Create: `skills/exj/evals/evals.json`

**Interfaces:**
- Consumes: nomes e limites descritos nos agents e skills Java existentes.
- Produces: `$exj`, um roteador que aponta para os IDs especializados sem substituí-los.

- [ ] **Step 1: Criar `skills/exj/SKILL.md`**

Usar o seguinte conteúdo inicial e completar a tabela de roteamento para dados, segurança, plataforma e operação:

```markdown
---
name: exj
description: "Especialista de entrada para engenharia Java: encaminha a tarefa à skill e ao agent adequados do catálogo."
---

# EXJ — Especialista em tudo Java

## Quando usar

Use quando a tarefa envolver desenvolvimento, arquitetura, revisão ou operação de sistemas Java.

## Quando não usar

Não use como roteador para tarefas sem relação com Java. Para uma tarefa fora desse escopo, selecione diretamente a skill apropriada.

## Roteamento

| Pedido | Skill | Agent |
| Criar aplicação ou feature | `criar-aplicacao-java` | `java-construtor` |
| Revisar código Java | `revisao-de-codigo-java` | `java-revisor` |
| Projetar API REST | `api-rest-design` | `projetista-api` |
| Arquitetura e ADR | `design-system-architecture` | `arquiteto-sistemas` |

Carregue somente os recursos do assunto. No Codex, siga a skill indicada; em clientes com agents instalados, acione também o agent indicado. Não replique as regras nem os passos desses recursos.
```

- [ ] **Step 2: Criar `skills/exj/evals/evals.json`**

Definir `skill_name` como `exj` e estes cinco casos no formato de evals já usado pelo catálogo: criar uma API Spring deve selecionar `criar-aplicacao-java`/`java-construtor`; revisar diff Java deve selecionar `revisao-de-codigo-java`/`java-revisor`; desenhar arquitetura de checkout deve selecionar `design-system-architecture`/`arquiteto-sistemas`; investigar latência e saturação deve selecionar `monitoramento-java`/`especialista-monitoramento`; escrever uma aplicação Python deve declarar que está fora do escopo da trilha EXJ e não carregar skills Java sem necessidade. Cada caso deve incluir `id`, `prompt`, `expected_output`, `files` e `expectations`; o nível raiz deve incluir `trigger.should_trigger` e `trigger.should_not_trigger`.

- [ ] **Step 3: Executar a validação de anatomia e inventário**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=CatalogoEstruturaTest,ReferenciasCatalogoTest test`
Expected: PASS; a nova skill e seu arquivo de avaliações devem seguir a anatomia existente.

---

### Task 3: Publicar os comandos curtos

**Files:**
- Create: `commands/exj/app.md`, `commands/exj/feat.md`, `commands/exj/ref.md`, `commands/exj/rev.md`, `commands/exj/adr.md`, `commands/exj/av.md`, `commands/exj/val.md`
- Delete: `commands/java/nova-app.md`, `commands/java/feature.md`, `commands/java/refatorar.md`, `commands/java/revisar.md`, `commands/arq/adr.md`, `commands/catalogo/avaliar.md`, `commands/catalogo/validar.md`
- Modify: `docs/catalogo/convencoes.md`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/ComandosCatalogoTest.java`

**Interfaces:**
- Consumes: os corpos e delegações dos comandos atuais.
- Produces: os mesmos sete fluxos sob `/exj:{app,feat,ref,rev,adr,av,val}`.

- [ ] **Step 1: Copiar cada delegação para o novo caminho e encurtar o frontmatter**

Preservar o corpo de cada comando existente e ajustar título, descrição e tags para o nome curto. Cada arquivo deve continuar aceitando `$ARGUMENTS` e deve continuar apontando para as mesmas skills e agents; `/exj:adr` mantém o template ADR atual e `/exj:av` mantém o protocolo de avaliação. Exemplo do formato de `/exj:rev`:

```markdown
---
name: "EXJ: Revisar"
description: Revisa código Java com java-revisor
category: Workflow
tags: [java, revisao]
---

Acione o agent `java-revisor` com a entrada abaixo. Modo: primeiro argumento (`tempestivo` ou `auditoria`); sem argumento, use `tempestivo`.

Entrada: $ARGUMENTS
```

- [ ] **Step 2: Atualizar convenções e teste de inventário**

Em `docs/catalogo/convencoes.md`, trocar a lista de diretórios de comandos para `commands/exj/` e `commands/opsx/`, explicando que os primeiros são os fluxos do EXJ. Em `ComandosCatalogoTest`, verificar também que os comandos existentes em `commands/opsx/` não foram renomeados.

- [ ] **Step 3: Rodar testes de comandos**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=ComandosCatalogoTest test`
Expected: PASS; os tokens kebab-case citados continuam resolvendo para skills ou agents existentes.

---

### Task 4: Atualizar identidade do plugin e instruções de uso

**Files:**
- Modify: `.claude-plugin/plugin.json`
- Modify: `.claude-plugin/marketplace.json`
- Modify: `.codex-plugin/plugin.json`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/PluginManifestoTest.java`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/ComandosCatalogoTest.java`

**Interfaces:**
- Consumes: os IDs de plugin `catalogo-java` e marketplace `srportto-catalogo` existentes.
- Produces: identidade técnica `exj` e instruções de instalação/comando consistentes.

- [ ] **Step 1: Atualizar os manifestos**

Definir `name: exj` nos manifestos Claude e Codex. Definir `name: srportto-exj` em `.claude-plugin/marketplace.json` e também `plugins[0].name: exj`; atualizar descrição para apresentar a marca EXJ.

- [ ] **Step 2: Atualizar README e instruções de manutenção**

No `README.md`, atualizar nomes e configuração do plugin para o slug `exj`, atualizar os comandos de instalação Claude para `/plugin install exj@srportto-exj`, documentar `$exj` e trocar a tabela dos sete comandos para `/exj:*`. Manter os exemplos de caminho do clone e do diretório de instalação existentes. Atualizar o inventário de 31 para 32 skills e listar `exj` como roteador de entrada da trilha Java. Incluir uma nota curta para atualizar instalações existentes do identificador do plugin. Em `AGENTS.md` e `CLAUDE.md`, trocar o comando de avaliação afetado por `/exj:av`.

- [ ] **Step 3: Cobrir as referências públicas com testes**

Em `PluginManifestoTest`, confirmar `exj` nos manifestos Claude/Codex, igualdade entre plugin Claude e entrada marketplace, e `srportto-exj` no marketplace. Em `ComandosCatalogoTest`, confirmar que o README contém `/exj:app`, `/exj:av`, `/exj:val` e `$exj`, e que não documenta os sete nomes antigos como comandos ativos. A asserção de documentação deve carregar `README.md` com `Files.readString` e verificar os tokens com `assertTrue`.

- [ ] **Step 4: Executar testes direcionados**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=PluginManifestoTest,ComandosCatalogoTest test`
Expected: PASS; nomes e caminhos públicos estão alinhados entre arquivos e testes.

---

### Task 5: Validar a entrega e revisar referências restantes

**Files:**
- Modify: `README.md` somente se a validação revelar referências ativas antigas.
- Modify: `docs/catalogo/convencoes.md` somente se a validação revelar a estrutura de comandos antiga.

**Interfaces:**
- Consumes: todos os arquivos alterados nas Tasks 1–4.
- Produces: validação do catálogo concluída e relatório das verificações executadas.

- [ ] **Step 1: Procurar referências antigas em arquivos ativos**

Executar `rg -n --hidden --glob '!**/.git/**' 'catalogo-java|srportto-catalogo|/catalogo:(avaliar|validar)|/java:(feature|nova-app|refatorar|revisar)|/arq:adr' README.md AGENTS.md CLAUDE.md .claude-plugin .codex-plugin commands skills docs/catalogo validation/java/src/test`.
Expected: nenhuma referência ativa a comandos ou slug antigos; referências históricas em planos e avaliações datadas podem permanecer.

- [ ] **Step 2: Executar a verificação de catálogo**

Run: `mvn -f validation/java/pom.xml verify`
Expected: BUILD SUCCESS e todos os testes do catálogo aprovados.

- [ ] **Step 3: Avaliar os casos da nova skill**

Executar `/exj:av skills/exj/evals/evals.json` e registrar os quatro casos no diretório de avaliações com data. Se a avaliação revelar roteamento incorreto, ajustar `skills/exj/SKILL.md` ou seus evals e repetir os casos afetados.

- [ ] **Step 4: Resumir os comandos publicados e as verificações**

Relatar os sete comandos `/exj:*`, a nova entrada `$exj`, os slugs de instalação `exj` e `srportto-exj`, o resultado da verificação e eventuais verificações não executadas.
