---
name: "Java: Feature"
description: Fluxo de feature Java - refinamento, proposta OpenSpec, construção e revisão
category: Workflow
tags: [java, feature, workflow]
---

Conduza a feature em etapas, parando se uma etapa bloquear:

1. Execute a skill `refinamento-de-historias` com a entrada abaixo; só avance sem lacunas classificadas como Bloqueia.
2. Execute `/opsx:propose` com a história refinada.
3. Acione o agent `java-construtor` para implementar as tasks da change.
4. Acione `java-revisor` em modo `tempestivo` durante a implementação e em modo `auditoria` ao fim.

Cada etapa segue a própria skill ou agent; este comando só encadeia.

Entrada: $ARGUMENTS
