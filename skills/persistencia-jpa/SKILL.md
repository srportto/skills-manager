---

name: persistencia-jpa
description: "Referência de bolso dos problemas mais comuns de JPA/Hibernate — N+1, `LazyInitializationException`, transações mal posicionadas, lost-update, listagens lentas sem paginação, design de entidade/projeção, optimistic locking. Use em dúvida de performance ou comportamento de persistência, ou ao revisar código que toca `Repository`/`@Entity`. Uso: agents `especialista-banco-dados`/`java-construtor`/`java-revisor` ou `/persistencia-jpa`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.1.0"
  domain: persistence
  triggers: muitas queries, N+1, LazyInitializationException, transação, lock, paginação lenta, JPA, Hibernate, dirty checking
  role: specialist
  scope: persistence
  output-format: code
  related-skills: banco-de-dados-performance, arquitetura-limpa-java, qualidade-codigo-java
---

# Persistência JPA

Referência de bolso para os problemas de JPA/Hibernate mais comuns: N+1, `LazyInitializationException`,
transações mal posicionadas, atualização concorrente perdida e listagens lentas sem paginação. Use
sempre que houver dúvida de performance ou comportamento de persistência, ou ao revisar código que
acessa `Repository`/`@Entity`.

## Quando usar / Quando NÃO usar

**Use** para dúvida de performance ou comportamento de persistência e ao revisar código que toca
`Repository`/`@Entity`.

**Quando NÃO usar:** para dúvida sobre em qual camada uma classe deve viver (ex.: onde fica o
repository), use `arquitetura-limpa-java`. Para gerar o esqueleto de uma aplicação nova com banco de
dados (overlay `rest-crud-banco`), use `criar-aplicacao-java`. Para revisão de código completa (não só
persistência), use `revisao-de-codigo-java`. Para tuning de banco (índices, `EXPLAIN ANALYZE`,
configuração do SGBD), use `banco-de-dados-performance`.

## Entradas

- Trecho de código (`@Entity`, `Repository`, use case) ou o sintoma observado (log de SQL, exceção, latência).
- Versão do banco/dialeto (PostgreSQL ou MySQL) quando a dúvida envolver lock, timeout ou isolamento.
- Contexto de implantação: várias réplicas da aplicação? réplica de leitura? rolling deploy?

## Decisão

Tabela problema → solução:

| Problema | Sintoma | Solução |
|---|---|---|
| N+1 queries | Muitos `SELECT` no log para uma única listagem | `JOIN FETCH` (JPQL) ou `@EntityGraph` |
| `LazyInitializationException` | Erro ao acessar associação fora da transação | Projeção DTO ou `JOIN FETCH` — **nunca** `enable_lazy_load_no_trans` |
| Update lento | Overhead de dirty checking em consultas grandes | `@Transactional(readOnly = true)` nos métodos de leitura |
| Lost update | Duas transações concorrentes sobrescrevem uma a outra | Locking otimista com `@Version` |
| Listagem lenta | Página carrega tudo de uma vez, sem limite | `Pageable` + projeção (retornar só os campos necessários) |
| Insert em lote lento | Uma query de `INSERT` por registro | `hibernate.jdbc.batch_size` + `saveAll` |

Onde ler o detalhe de cada linha: veja o [guia de references](#guia-de-references).

## Passo a passo (checklist)

- [ ] Reproduza com o SQL visível (`hibernate.SQL: DEBUG`) e **conte** as queries antes de mudar.
- [ ] Identifique o sintoma na tabela acima e abra a reference correspondente.
- [ ] Aplique **uma** correção por vez (fetch, projeção, transação, lock).
- [ ] Confirme que `@Transactional` está no use case e que não há auto-invocação.
- [ ] Se há escrita concorrente: escolha otimista (`@Version`), pessimista ou UPDATE condicional atômico.
- [ ] Se há mudança de schema: aplique expand/contract; índices pesados seguem `banco-de-dados-performance`.
- [ ] Reconte as queries / reexecute o teste de concorrência e registre antes/depois.

## Saída

Código ajustado (entidade, repository, use case) com a mudança de queries medida (antes/depois), ou parecer
de revisão apontando o anti-padrão, a severidade e a correção da reference correspondente.

## Validação

- Número de queries por requisição caiu e é estável (não cresce com o tamanho da lista).
- Nenhuma `LazyInitializationException` com `spring.jpa.open-in-view: false`.
- Teste de concorrência (duas transações) prova o comportamento de lock/`@Version`/unique constraint;
  referência executável: [`ProcessadorIdempotenteExternoIT`](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java).
- Migration compatível com a versão anterior da aplicação (rollback de app funciona com o schema expandido).

## Gotchas

- `@Transactional` em chamada interna (`this.metodo()`) é ignorado em silêncio — extraia para outro bean.
- `try/catch` em volta de `save(...)` não pega `OptimisticLockingFailureException`: o flush acontece no
  commit; use `saveAndFlush` ou trate no handler central (409).
- `existsBy...` seguido de `save(...)` é check-then-act: quem arbitra é a unique constraint criada na migration.
- `@Data` em entidade, `FetchType.EAGER` em coleção e `findAll()` sem `Pageable` são os três vilões clássicos.
- `JOIN FETCH` de coleção com `Pageable` pagina em memória.
- Leitura de réplica não serve para decisão de negócio nem para read-your-writes.
- Pool de conexões e timeouts de aquisição: fonte única em
  [orcamento-conexoes](../banco-de-dados-performance/references/orcamento-conexoes.md).

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [references/n-mais-um.md](references/n-mais-um.md) | N+1, `JOIN FETCH`/`@EntityGraph`/projeção, `EAGER`, `findAll()` sem paginação, `open-in-view` |
| [references/transacoes.md](references/transacoes.md) | Onde fica `@Transactional`, `readOnly`, auto-invocação, transação curta sem I/O |
| [references/entidades-projecoes.md](references/entidades-projecoes.md) | Convenções de entidade/repository, DTO record, idempotência por unique constraint, `ddl-auto` |
| [references/locking.md](references/locking.md) | `@Version`, lock pessimista, deadlock, UPDATE condicional, isolamento e timeouts |
| [references/migrations-expand-contract.md](references/migrations-expand-contract.md) | Mudança de schema em rolling deploy, rollback |
| [references/replica-leitura.md](references/replica-leitura.md) | Réplica com atraso, read-your-writes, roteamento |
| [../banco-de-dados-performance/references/orcamento-conexoes.md](../banco-de-dados-performance/references/orcamento-conexoes.md) | Fonte única de pool/HikariCP/proxy de conexões (não duplicada aqui) |
| [../banco-de-dados-performance/assets/diagnostico-postgresql.sql](../banco-de-dados-performance/assets/diagnostico-postgresql.sql) | Locks bloqueantes, lag de réplica, dead tuples em produção |

Código executável relacionado (em `examples/java`):
[`ProcessadorIdempotente`](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java)
(unique constraint + transação do efeito),
[`ProcessadorIdempotenteExternoIT`](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java)
(concorrência em PostgreSQL real). Padrão completo de outbox e replay:
[idempotência, outbox e replay](../mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md).

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Diagnosticar N+1, lock, transação | agent `especialista-banco-dados` ou sessão principal | esta skill |
| Gerar repository/entidade novos | agent `java-construtor` | esta skill + `arquitetura-limpa-java` |
| Revisar código que toca `Repository`/`@Entity` | agent `java-revisor` | esta skill + `revisao-de-codigo-java` |
| Índice, `EXPLAIN ANALYZE`, tuning do SGBD | agent `especialista-banco-dados` | `banco-de-dados-performance` |
