---
name: "OPSX: Archive"
description: Archive a completed change in the experimental workflow
category: Workflow
tags: [workflow, archive, experimental]
---

Execute a skill `openspec-archive-change` (ferramenta Skill) com a entrada abaixo e siga-a integralmente. A skill é a
fonte única do fluxo; este comando não repete os passos.

Entrada: $ARGUMENTS

A skill usa o CLI `openspec` e carrega `openspec-catalogo-java` para encaixar skills e agents do catálogo.
Se a skill não estiver instalada, avise e pare: instale o catálogo (`skills/` → `~/.claude/skills`).
