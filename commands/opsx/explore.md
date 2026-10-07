---
name: "OPSX: Explore"
description: "Enter explore mode - think through ideas, investigate problems, clarify requirements"
category: Workflow
tags: [workflow, explore, experimental, thinking]
---

Execute a skill `openspec-explore` (ferramenta Skill) com a entrada abaixo e siga-a integralmente. A skill é a
fonte única do fluxo; este comando não repete os passos.

Entrada: $ARGUMENTS

A skill usa o CLI `openspec` e carrega `openspec-catalogo-java` para encaixar skills e agents do catálogo.
Se a skill não estiver instalada, avise e pare: instale o catálogo (`skills/` → `~/.claude/skills`).
