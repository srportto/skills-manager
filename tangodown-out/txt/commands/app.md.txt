---
name: "Criar-app"
description: Cria aplicação Java com java-construtor e audita ao fim
category: Workflow
tags: [exj, java, aplicacao]
---

Acione o agent `java-construtor` com a entrada abaixo; ele segue a skill `criar-aplicacao-java` (variante, nome e
pacote vêm da entrada ou são perguntados por ele). Ao fim, acione `java-revisor` em modo `auditoria` sobre a
app gerada e relate o veredicto sem suavizá-lo. Este comando não repete os passos da skill.

Entrada: $ARGUMENTS
