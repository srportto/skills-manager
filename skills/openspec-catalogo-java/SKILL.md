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
| `apply` — refatoração | `qualidade-codigo-java` (`references/refatoracoes-fowler.md`) | `refatorador-java` | Testes antes e depois |
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
  (modo auditoria).
  A change alterou código Java quando suas tarefas ou o diff tocam arquivos `.java`, `pom.xml` ou `build.gradle`.
  Veredicto ausente conta como PENDENTE. PENDENTE ou REPROVADO bloqueia o archive, a menos que o usuário assuma o
  risco explicitamente; registre essa decisão no resumo do archive.
  Change só de documentação ou spec não passa por esse portão.
- **Stack:** Java 25 sem preview; AWS local com Floci (`http://localhost:4566`).

## Quem aplica o quê

As skills `openspec-propose`, `openspec-apply-change`, `openspec-archive-change` e `openspec-explore` carregam
esta skill. Os comandos `/opsx:*` delegam a essas skills. O `config.yaml` leva as regras de artefato ao CLI.
