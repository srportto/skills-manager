---
name: "Sync"
description: Sync delta specs from a change to main specs
category: Workflow
tags: [workflow, specs, experimental]
---

Execute a skill `openspec-sync-specs` (ferramenta Skill) com a entrada abaixo e siga-a integralmente. A skill é a
fonte única do fluxo; este comando não repete os passos.

Entrada: $ARGUMENTS

A skill usa o CLI `openspec` para resolver a change e as specs.
Se a skill não estiver instalada, avise e pare: instale o catálogo (`skills/` → `~/.claude/skills`).
