---

name: java-moderno
description: "Referência de features modernas do Java 25 (records, sealed classes, pattern matching, switch expressions, text blocks, virtual threads, `var`) com exemplos antes/depois. Use ao escrever código que se beneficia de feature moderna, migrar de Java 8/11/17/21, ou perguntar \"qual o jeito moderno de fazer X em Java\". Uso: agents `java-construtor`/`java-revisor` ou `/java-moderno`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.3.0"
  domain: language-features
  triggers: records, sealed classes, pattern matching, virtual threads, text blocks, switch expressions, Java 25, var, switch pattern, when, case null, non-sealed, record patterns, ScopedValue
  role: reference
  scope: java-language
  output-format: code
  related-skills: qualidade-codigo-java, padroes-de-projeto-java, arquitetura-limpa-java
---

# Java Moderno

Guia de decisão das features modernas do Java na stack fixa deste catálogo — **Java 25 (sem preview) +
Spring Boot 4.0.7**. O corpo detalhado de cada feature fica em `references/`; os exemplos executáveis estão no
módulo [linguagem](../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java).

## Quando usar / quando NÃO usar

- **Usar:** escrever código que se beneficia de feature moderna; migrar código de Java 8/11/17/21; responder
  "qual o jeito moderno de fazer X".
- **NÃO usar:** aplicar design pattern GoF (Strategy, Factory, Builder...) — use `padroes-de-projeto-java`; revisão
  completa de código (não só modernização) — use `revisao-de-codigo-java`; feature em preview (não compila neste catálogo).

## Entradas

Trecho de código ou intenção; versão de origem do Java (se for migração); se a carga é I/O-bound ou CPU-bound
(virtual threads); se a hierarquia de tipos é fechada ou extensível por terceiros (sealed).

## Decisão: problema → feature → reference

| Problema no código | Feature moderna | Reference |
|---|---|---|
| Classe "de dados" com getters/`equals`/`hashCode` escritos à mão | `record` (+ construtor compacto para validar) | [records](references/records.md) |
| Hierarquia de domínio finita (tipos de pagamento, estados) que o compilador deveria conhecer | `sealed` + `permits` / `non-sealed` | [sealed-e-switch](references/sealed-e-switch.md) |
| `if/else` em cadeia por tipo ou `default` escondendo tipo novo | `switch` de padrões exaustivo, sem `default` | [sealed-e-switch](references/sealed-e-switch.md) |
| `instanceof` seguido de cast manual | pattern matching com binding / record pattern | [pattern-matching](references/pattern-matching.md) |
| `switch` com `break` e fallthrough, valor atribuído por variável auxiliar | switch expression com `->` e `yield` | [sealed-e-switch](references/sealed-e-switch.md) |
| SQL/JSON/HTML multilinha concatenado com `\n` | text block `"""` | [text-blocks-var](references/text-blocks-var.md) |
| Tipo repetido nos dois lados da atribuição local | `var` (só quando o tipo é óbvio) | [text-blocks-var](references/text-blocks-var.md) |
| Muitas requisições I/O-bound esgotando pool de threads | virtual threads (+ `Semaphore` para recurso finito) | [virtual-threads](references/virtual-threads.md) |
| `ThreadLocal` com remoção esquecida ou memória por thread | `ScopedValue` (JEP 506, final no JDK 25) | [virtual-threads](references/virtual-threads.md) |
| Código vindo do Java 8/11/17/21 ou dúvida sobre `void main()` no Spring Boot | roteiro por versão | [migracao-por-versao](references/migracao-por-versao.md) |

## Passo a passo

- [ ] Identifique o problema na tabela e abra **só** a reference da linha correspondente.
- [ ] Garanta teste verde antes de modernizar (a modernização não pode mudar comportamento).
- [ ] Aplique a feature de forma incremental: um tipo ou um método por vez.
- [ ] Hierarquia fechada: feche com `sealed` e remova o `default` do `switch` para o compilador acusar tipo novo.
- [ ] Virtual threads: confirme carga I/O-bound e limite o recurso finito (conexões) com `Semaphore`/bulkhead.
- [ ] Rode a suíte afetada e peça revisão ao `java-revisor`.

## Saída

Código com a feature aplicada, comentários em português, e uma linha justificando por que a feature cabe (ou por
que não cabe) no caso — citando "Quando evitar" da reference.

## Validação

Depois de qualquer modernização em código real (classe → record, introdução de sealed, pattern matching), peça
revisão ao agent `java-revisor` antes de concluir: ele aplica o checklist de `revisao-de-codigo-java` e confirma
que o comportamento e o contrato existente não mudaram. Provas executáveis das regras de linguagem:
[PagamentoTest](../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java) (inclui
compilação em memória dos erros de exaustividade e dominância) e
[TarifacaoTest](../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/TarifacaoTest.java).

## Gotchas

- `default` num switch sobre tipo `sealed` ou enum **esconde** tipo novo; use só em domínio realmente aberto.
- `var` vale só para variável local; não existe em campo, parâmetro ou retorno — e não deve esconder o tipo.
- Record é imutável só de forma rasa: componente mutável exige cópia defensiva.
- Virtual threads não aumentam capacidade de recurso: 10 conexões no banco continuam 10; limite a admissão.
- Em Spring Boot, mantenha `public static void main(String[] args)`: `void main()` do JDK 25 quebra o `repackage`.
- Padrões primitivos em `switch` (JEP 507) são preview no Java 25 — não usar.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [records.md](references/records.md) | Modelar dado imutável, DTO, value object; validar no construtor compacto |
| [sealed-e-switch.md](references/sealed-e-switch.md) | Hierarquia fechada, switch exaustivo, `case null`, `when`, `yield`, dominância, antes/depois |
| [pattern-matching.md](references/pattern-matching.md) | Eliminar cast após `instanceof`; desestruturar record na condição |
| [text-blocks-var.md](references/text-blocks-var.md) | SQL/JSON multilinha; decidir quando `var` ajuda ou atrapalha |
| [virtual-threads.md](references/virtual-threads.md) | Habilitar virtual threads, pinning, limite de conexões, `ScopedValue` |
| [migracao-por-versao.md](references/migracao-por-versao.md) | Migrar de Java 8/11/17/21 para o 25; armadilha do entrypoint Spring Boot |

## Quem aplica o quê

- **`java-construtor`:** escolhe a feature pela tabela ao gerar código novo e mantém o entrypoint clássico.
- **`java-revisor`:** confere exaustividade sem `default`, uso correto de `var`, limite de recurso com virtual threads.
- **Sessão principal / `/java-moderno`:** orienta migração por versão e responde "qual o jeito moderno".
