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
