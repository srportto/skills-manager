---
name: "EXJ: Avaliar"
description: Executa o protocolo de avaliação de agents e casos de skills
category: Workflow
tags: [exj, avaliacao]
---

Siga o protocolo de `docs/catalogo/avaliacoes-agents.md`: cenários A01 a A12 (ou os indicados na entrada), 2
execuções por cenário, saída em `docs/catalogo/avaliacoes/<data>/`. Aceite também casos de eval indicados na
entrada; quando o cenário envolver uma skill, use os casos de `skills/*/evals/evals.json` como entrada adicional.

Relate por cenário: passou, falhou ou não executado, com evidência. Cenário não executado é pendente, nunca
aprovado. Este comando não repete os critérios do protocolo.

Entrada: $ARGUMENTS
