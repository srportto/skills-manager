---
name: "Catálogo: Validar"
description: Roda a validação Maven do catálogo e relata executado x pendente
category: Workflow
tags: [catalogo, validacao]
---

Execute, a partir da raiz do repositório, os quatro comandos Maven da seção "Validação do catálogo" de `skills/README.md` e
registre o resultado de cada um (contagem de testes, falhas, pulados). Não use `-DskipTests`.

Relate em duas listas: **executado** (comando, contagem) e **pendente** (comando não rodado ou teste pulado, com o
motivo, como Docker indisponível). Teste pulado ou comando não executado é pendência, nunca aprovação. Se algum
comando falhar, mostre as primeiras falhas e pare sem corrigir.

Entrada: $ARGUMENTS
