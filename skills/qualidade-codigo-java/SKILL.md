---

name: qualidade-codigo-java
description: 'Guia de clean code aplicado a Java — DRY, KISS, YAGNI, naming, imutabilidade, `Optional`, streams, tratamento de exceção, Object Calisthenics — e refactorings do Fowler (Remove Parameter, Extract Method, Replace Magic Number, etc.). É o lado "ativo" da revisão: `revisao-de-codigo-java` diz o que revisar; esta diz como aplicar. Uso: sessão principal e agent `java-construtor` (carregada proativamente quando código Java for gerado/alterado); também `java-revisor`/`refatorador-java` ou `/qualidade-codigo-java`.'
license: MIT
metadata:

  author: https://github.com/srportto/srportto
  version: "1.3.0"
  domain: code-quality
  triggers: clean code, boas praticas, refatorar, DRY, KISS, YAGNI, imutabilidade, Optional, streams, Fowler, Object Calisthenics, Wrap All Primitives, First Class Collections, Law of Demeter, Tell Don't Ask
  role: reference
  scope: code-quality
  output-format: code
  related-skills: revisao-de-codigo-java, padroes-de-projeto-java, java-moderno
---

# Qualidade de Codigo Java (clean code + refactoring + Object Calisthenics)

Guia de **aplicacao** de clean code em Java e de refactorings do Fowler. E o "lado ativo" da revisao:
a `revisao-de-codigo-java` diz **o que revisar**; esta skill diz **como aplicar**.

**Carregamento proativo:** esta skill deve ser consultada **durante a geracao** de codigo Java -
nao so depois, na revisao. Ao escrever classe, metodo ou refactoring novo, aplique DRY/KISS/YAGNI,
Object Calisthenics e as convencoes de nomenclatura antes de entregar - nao espere o `java-revisor`
apontar a violacao.

> **Principio-mestre (Clean Code for AI):** todo codigo deste catalogo deve estar **otimizado para a
> janela de contexto do LLM** - nomes grepaveis, metodos curtos, arquivos pequenos, tipos explicitos
> e comentarios "por que".

## Quando usar / Quando NAO usar

- **Usar:** ao gerar ou alterar codigo Java, ao refatorar, ao corrigir o que a revisao apontou.
- **NAO usar:** revisar diff/PR com checklist por severidade -> `revisao-de-codigo-java`; regra de
  dependencia entre camadas -> `arquitetura-limpa-java`; JPA/Hibernate (N+1, dirty checking) ->
  `persistencia-jpa`; logging (formato, MDC) -> `monitoramento-java`.

## Entradas

Trecho de codigo Java (novo ou existente) e, se houver, o achado da revisao (sintoma). Sem testes
cobrindo o trecho, escreva a prova antes de refatorar (`testes-sistemas-java`).

## Decisao: sintoma -> principio -> reference -> refactoring

| Sintoma no codigo | Principio | Reference | Refactoring |
|---|---|---|---|
| Mesma validacao/regra em 2+ lugares | DRY (regra das 3 ocorrencias) | [clean-code-principios.md](references/clean-code-principios.md) | Extract Method |
| Interface com implementacao unica "para o futuro" | KISS / YAGNI | [clean-code-principios.md](references/clean-code-principios.md) | Inline (chamada direta) |
| Nome generico (`Handler`, `Util`, `get`), sigla, parametro sem unidade | Don't Abbreviate | [nomenclatura.md](references/nomenclatura.md) | Rename; Introduce Parameter Object |
| `String status`/`int tipo` restrito a poucos valores | Tipos expressivos | [nomenclatura.md](references/nomenclatura.md) | Replace Magic Number com enum |
| Setters publicos, estado mutavel pos-construcao | Imutabilidade | [imutabilidade-optional-streams.md](references/imutabilidade-optional-streams.md) | Converter em `record` / `final` |
| `Optional.get()` sem checagem | Optional correto | [imutabilidade-optional-streams.md](references/imutabilidade-optional-streams.md) | `orElseThrow`/`map` |
| `forEach` mutando lista externa | Stream sem efeito colateral | [imutabilidade-optional-streams.md](references/imutabilidade-optional-streams.md) | Replace Loop with Pipeline |
| `catch` amplo, causa perdida, `RuntimeException` generica | Exception handling | [excecoes.md](references/excecoes.md) | Excecao de dominio + causa preservada |
| Raw type, parametro `ArrayList` concreto | Type safety | [genericos-tipos.md](references/genericos-tipos.md) | Generic explicito; interface no parametro |
| Primitivo com invariante, colecao com regra, cadeia `a.b().c()`, classe inchada, `else` aninhado | Object Calisthenics | [object-calisthenics.md](references/object-calisthenics.md) e [refatoracoes-fowler.md](references/refatoracoes-fowler.md) | Wrap primitives, First Class Collection, Guard Clause |
| Parametro nao usado/redundante, metodo longo, `switch` por tipo | Fowler | [refatoracoes-fowler.md](references/refatoracoes-fowler.md) | Remove Parameter, Extract Method, Replace Conditional with Polymorphism |

## Passo a passo (checklist)

- [ ] Identifique o sintoma na tabela e leia **so** a reference indicada.
- [ ] Garanta teste verde antes de mexer (ou escreva a prova primeiro).
- [ ] Aplique uma tecnica por vez, sem mudar comportamento.
- [ ] Rode os testes; remova imports nao usados (`remover-imports-nao-usados`).

## Saida

Codigo Java que preserva o comportamento, com nomes grepaveis, tipos explicitos e menos acoplamento.

## Validacao

Testes antes e depois verdes. **Refatorar preserva comportamento** - inclusive o que nao aparece no
tipo: ordem de processamento, momento do ack/commit, transacao, liberacao de recursos em `finally`,
cancelamento e timeouts.

## Gotchas

- Object Calisthenics sao **heuristicas de design**, nao bugs: aplique quando reduzem um problema
  concreto; classe coesa com 3 atributos nao precisa ser quebrada.
- DRY com bom senso: na 1a e 2a ocorrencia duplicar pode ser mais barato que a abstracao errada.
- Records sao o padrao para DTO/value object (ver `java-moderno`); nao use quando precisar de
  mutabilidade ou heranca.
- Use portugues ou ingles de forma consistente dentro do mesmo pacote/classe.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [clean-code-principios.md](references/clean-code-principios.md) | Duplicacao de regra ou abstracao especulativa (DRY, KISS, YAGNI) |
| [nomenclatura.md](references/nomenclatura.md) | Nomear classe/metodo/campo/parametro; nomes genericos e siglas |
| [imutabilidade-optional-streams.md](references/imutabilidade-optional-streams.md) | Setters, `Optional.get()`, streams com efeito colateral |
| [excecoes.md](references/excecoes.md) | `try/catch`, excecao de dominio, try-with-resources |
| [genericos-tipos.md](references/genericos-tipos.md) | Raw types e parametros com tipo concreto |
| [object-calisthenics.md](references/object-calisthenics.md) | Decidir quando uma tecnica vale a pena (tabela) |
| [refatoracoes-fowler.md](references/refatoracoes-fowler.md) | Passo a passo e exemplos completos de refactorings do Fowler |

# Quem aplica o que

| Situacao | Quem | Skill |
|---|---|---|
| Aplicar refactoring em uma classe/metodo | sessao principal | esta skill |
| Revisar diff/PR com checklist de severidade | agent `java-revisor` | `revisao-de-codigo-java` |
| Remocao de parametro focada (passo-a-passo) | sessao principal | esta skill (`references/refatoracoes-fowler.md#remove-parameter`) |
| Limpar imports nao usados | sessao principal | `remover-imports-nao-usados` |
| Centralizar configuracao dispersa (Shotgun Surgery) | session/engenheiro-devops | `arquitetura-limpa-java` (`references/modulos-spring.md`) |
| Decidir onde mora um value object novo | session/java-construtor | `arquitetura-limpa-java` |
