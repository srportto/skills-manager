---
name: padroes-de-projeto-java
description: "Catálogo de referência rápida dos 21 padrões GoF (criacionais, estruturais, comportamentais) com exemplos antes/depois — e os critérios de quando **não** aplicar um padrão. Use ao decidir qual padrão resolve um problema concreto, refatorar código rígido/acoplado ou quando pedirem um padrão específico. Uso: agents `java-revisor`/`refatorador-java` ou `/padroes-de-projeto-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.1.0"
  domain: design-patterns
  triggers: aplique o pattern, usa strategy, refatorar com factory, esse código está rígido, GoF, factory, builder, strategy, observer, decorator
  role: reference
  scope: code-design
  output-format: code
  related-skills: qualidade-codigo-java, java-moderno, arquitetura-limpa-java
---

# Padrões de Projeto Java (GoF)

## Quando usar

Ao decidir qual padrão resolve um problema concreto, refatorar código rígido/acoplado ou quando pedirem um
padrão específico.

## Quando NÃO usar

Quando não há problema real a resolver: interface com uma única implementação, factory para `new` simples e
Singleton manual onde a injeção do Spring resolve. Critérios e antes/depois em
[references/quando-nao-aplicar.md](references/quando-nao-aplicar.md).

## Visão geral

Catálogo de referência rápida dos 21 padrões de projeto GoF (criacionais, estruturais e
comportamentais), com exemplos **ANTES/DEPOIS** resumidos em `references/` (originalmente extraídos de
`docs/patterns-arquitetura-java/` do monorepo de origem — contexto externo, ausente deste repositório).
Use para decidir **qual** pattern resolve um problema concreto e
para saber quando **não** aplicar nenhum pattern.

## Decisão: tabela problema → pattern

| Problema | Pattern | Categoria |
|----------|---------|-----------|
| Construção complexa (muitos parâmetros opcionais/obrigatórios) | Builder | Criacional |
| Criação por tipo em runtime | Factory Method | Criacional |
| Famílias de objetos relacionados | Abstract Factory | Criacional |
| Instância única (⚠️ alerta de testabilidade — ver "Quando NÃO aplicar") | Singleton | Criacional |
| Clonagem de objetos | Prototype | Criacional |
| Interfaces incompatíveis | Adapter | Estrutural |
| Abstração × implementação variando independentemente | Bridge | Estrutural |
| Árvore todo-parte (composição hierárquica) | Composite | Estrutural |
| Comportamento dinâmico (adicionado em runtime) | Decorator | Estrutural |
| Simplificar acesso a um subsistema | Facade | Estrutural |
| Muitos objetos baratos (compartilhar estado repetido) | Flyweight | Estrutural |
| Controle de acesso a um recurso | Proxy | Estrutural |
| Cadeia de tratadores | Chain of Responsibility | Comportamental |
| Ação como objeto | Command | Comportamental |
| Percorrer uma coleção sem expor sua estrutura | Iterator | Comportamental |
| Snapshot de estado (undo/histórico) | Memento | Comportamental |
| Notificar dependentes de uma mudança | Observer | Comportamental |
| Comportamento que muda por estado interno | State | Comportamental |
| Algoritmos intercambiáveis | Strategy | Comportamental |
| Esqueleto de algoritmo com passos variáveis | Template Method | Comportamental |
| Comunicação centralizada entre componentes | Mediator | Comportamental |

> **Preferência do projeto:** para "escolher um serviço entre vários candidatos em runtime" em Spring, use
> Strategy por lista injetada, sem factory dedicada — ver
> [references/strategy-lista-injetada.md](references/strategy-lista-injetada.md).

## Validação

Depois de aplicar um pattern em código real (não apenas em um exemplo didático), peça revisão ao
agent `java-revisor` antes de considerar a mudança concluída. O objetivo é confirmar que o pattern
resolveu o problema real do código (e não introduziu indireção desnecessária — ver "Quando NÃO
aplicar pattern" acima).

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [references/criacionais.md](references/criacionais.md) | Builder, Factory Method, Abstract Factory, Singleton, Prototype |
| [references/estruturais.md](references/estruturais.md) | Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy |
| [references/comportamentais.md](references/comportamentais.md) | Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method |
| [references/strategy-lista-injetada.md](references/strategy-lista-injetada.md) | Escolher implementação em runtime com Spring; alternativa `sealed` + `switch` (com exemplo executável em `examples/java`) |
| [references/quando-nao-aplicar.md](references/quando-nao-aplicar.md) | Antes de introduzir um pattern, para confirmar que não é over-engineering |

Cada entrada traz: problema (2-3 linhas), exemplo ANTES/DEPOIS resumido, e quando usar/evitar.

## Quem aplica o quê

| Situação | Use |
|---|---|
| Revisar se o pattern resolveu o problema | agent `java-revisor` |
| Refatorar código rígido para um pattern | agent `refatorador-java` / skill `qualidade-codigo-java` |
| Feature moderna (`sealed`, `switch`, records) no lugar de um pattern | skill `java-moderno` |
