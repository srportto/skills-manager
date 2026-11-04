# README e distribuição do catálogo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar o `README.md` da raiz o índice canônico e documentar o uso do catálogo por clone, projeto e usuário.

**Architecture:** O conteúdo de descoberta migra de `skills/README.md` para a raiz. A validação passa a reconhecer o novo índice e o manifesto `.codex-plugin/` formaliza a distribuição das skills no Codex, enquanto o formato Claude existente é preservado.

**Tech Stack:** Markdown, JSON, Java 25, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-10-08-readme-e-distribuicao-design.md`

## Global Constraints

- Todo texto e comentário novo deve estar em português do Brasil.
- Não reescrever ferramentas auxiliares listadas em `Catalogo.AUXILIARES`.
- Preservar os relatórios e planos históricos.
- O inventário deve citar todas as skills e agents existentes.
- Antes de cada commit, executar `mvn -B -ntp -f validation/java/pom.xml verify`.

## Review Focus

- Clone do repositório deve ser apresentado como alternativa utilizável, sem prometer atualização automática.
- Escopo de usuário e escopo de projeto devem apontar para locais de configuração distintos.
- A documentação não pode prometer carregamento automático de `agents/` ou `commands/` pelo Codex.
- Links do novo README devem ser validados, inclusive os relativos a `docs/` e `examples/`.
- A remoção de `skills/README.md` não pode deixar referências internas nem testes apontando ao arquivo removido.

---

### Task 1: Formalizar a compatibilidade do plugin Codex

**Files:**
- Create: `.codex-plugin/plugin.json`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/PluginManifestoTest.java`

**Interfaces:**
- Consumes: os campos `name`, `version`, `description` e `author` de `.claude-plugin/plugin.json`.
- Produces: manifesto Codex que declara `skills` como `./skills/`.

- [ ] **Step 1: Escrever o teste que falha**

Adicione a `PluginManifestoTest` uma constante para `.codex-plugin`, um método de leitura desse diretório e o teste abaixo:

```java
@Test
void manifestoCodexDeclaraAsSkillsDoCatalogo() throws IOException {
    var manifesto = lerCodex("plugin.json");
    assertEquals("catalogo-java", manifesto.get("name"));
    assertEquals("./skills/", manifesto.get("skills"));
    assertTrue(Files.isDirectory(Catalogo.RAIZ.resolve("skills")));
}
```

- [ ] **Step 2: Executar o teste e confirmar a falha**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=PluginManifestoTest test`

Expected: FAIL informando que `.codex-plugin/plugin.json` está ausente.

- [ ] **Step 3: Criar o manifesto compatível**

Crie `.codex-plugin/plugin.json` com:

```json
{
  "name": "catalogo-java",
  "version": "2.0.0",
  "description": "Skills para engenharia de software e system design com Java 25 e Spring Boot 4.",
  "author": { "name": "srportto" },
  "skills": "./skills/"
}
```

- [ ] **Step 4: Executar o teste e confirmar a aprovação**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=PluginManifestoTest test`

Expected: PASS, 4 testes e nenhuma falha.

- [ ] **Step 5: Validar e registrar**

Run: `mvn -B -ntp -f validation/java/pom.xml verify`

Expected: PASS, sem falhas.

```bash
git add .codex-plugin/plugin.json validation/java/src/test/java/br/com/srportto/catalogo/PluginManifestoTest.java
git commit -m "feat(plugin): adiciona manifesto do Codex"
```

### Task 2: Migrar o índice para a raiz

**Files:**
- Create: `README.md`
- Delete: `skills/README.md`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/ReferenciasCatalogoTest.java`
- Modify: `validation/java/src/test/java/br/com/srportto/catalogo/Catalogo.java`

**Interfaces:**
- Consumes: inventário e mapa de tarefas de `skills/README.md`.
- Produces: `README.md` como índice canônico, incluído na verificação de links da trilha.

- [ ] **Step 1: Fazer o teste exigir o índice da raiz**

Em `ReferenciasCatalogoTest`, troque `Catalogo.RAIZ.resolve("skills/README.md")` por `Catalogo.RAIZ.resolve("README.md")` e substitua cada mensagem que cite `skills/README.md` por `README.md`. Em `Catalogo.markdownDaTrilha()`, acrescente `RAIZ.resolve("README.md")` à lista retornada quando ele existir.

- [ ] **Step 2: Executar o teste e confirmar a falha**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=ReferenciasCatalogoTest test`

Expected: FAIL porque `README.md` ainda não existe.

- [ ] **Step 3: Criar o README canônico e remover o índice aninhado**

Migre integralmente o inventário, o mapa rápido, os fluxos de trabalho, a migração de nomes, as regras de validação e os princípios de `skills/README.md`. Antes do inventário, escreva as seções abaixo:

```markdown
## Comece por clone

```bash
git clone https://github.com/srportto/skills-manager.git
cd skills-manager
```

O clone permite consultar as fontes, executar as validações e copiar somente as skills necessárias para outro projeto. Ao copiar uma skill, mantenha juntos `SKILL.md`, `references/`, `assets/` e `evals/`; esse modo não atualiza cópias automaticamente.
```

Inclua também:

- uma seção **Instalação no Codex** com marketplace pessoal em `~/.agents/plugins/marketplace.json`, plugin em `~/.codex/plugins/` e configuração de projeto em `.agents/plugins/marketplace.json` + `.codex/config.toml`;
- uma seção **Instalação no Claude Code** que preserve os comandos atuais do marketplace;
- uma tabela que diferencie skill, agent e comando;
- exemplos de prompts para criar app, revisar PR, projetar API, configurar CI/CD e iniciar uma change OpenSpec;
- uma observação de portabilidade: o plugin Codex distribui as skills; `agents/` e `commands/` são definições de fluxo compatíveis com Claude Code e orientam o uso manual das skills no Codex.

Atualize todos os links migrados para serem relativos à raiz, por exemplo `docs/catalogo/convencoes.md` e `examples/java/README.md`. Remova `skills/README.md` depois de conferir que seu conteúdo foi incorporado.

- [ ] **Step 4: Executar a validação de referências**

Run: `mvn -B -ntp -f validation/java/pom.xml -Dtest=ReferenciasCatalogoTest test`

Expected: PASS, 5 testes e nenhuma falha.

- [ ] **Step 5: Validar e registrar**

Run: `mvn -B -ntp -f validation/java/pom.xml verify`

Expected: PASS, sem falhas.

```bash
git add README.md skills/README.md validation/java/src/test/java/br/com/srportto/catalogo/ReferenciasCatalogoTest.java validation/java/src/test/java/br/com/srportto/catalogo/Catalogo.java
git commit -m "docs(catalogo): centraliza índice na raiz"
```

### Task 3: Atualizar as referências ao índice canônico

**Files:**
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `commands/catalogo/validar.md`
- Modify: `docs/catalogo/convencoes.md`

**Interfaces:**
- Consumes: `README.md` criado na tarefa 2.
- Produces: referências internas sem dependência de `skills/README.md`.

- [ ] **Step 1: Atualizar os quatro consumidores**

Faça as substituições abaixo:

| Arquivo | Substituição |
|---|---|
| `AGENTS.md` | `Índice em README.md` |
| `CLAUDE.md` | `Índice em README.md` |
| `commands/catalogo/validar.md` | seção `Validação do catálogo` de `README.md` |
| `docs/catalogo/convencoes.md` | `[README.md](../../README.md)` como índice canônico |

- [ ] **Step 2: Verificar resíduos antes da validação**

Run: `rg -n "skills/README\.md" AGENTS.md CLAUDE.md commands docs validation README.md`

Expected: nenhuma ocorrência fora de planos históricos sob `docs/superpowers/plans/`.

- [ ] **Step 3: Executar a suíte do catálogo**

Run: `mvn -B -ntp -f validation/java/pom.xml verify`

Expected: PASS, 40 ou mais testes, sem falhas ou erros.

- [ ] **Step 4: Registrar a tarefa**

```bash
git add AGENTS.md CLAUDE.md commands/catalogo/validar.md docs/catalogo/convencoes.md
git commit -m "docs(catalogo): atualiza referências ao índice"
```

## Revisão final

- [ ] Confirmar que `README.md` cita cada skill e agent existente.
- [ ] Confirmar que o clone, o escopo de usuário e o escopo de projeto possuem instruções separadas.
- [ ] Confirmar que o README não promete suporte automático de agents e comandos pelo Codex.
- [ ] Executar `mvn -B -ntp -f validation/java/pom.xml verify` e registrar o resultado.
- [ ] Revisar o diff inteiro antes do commit final.
