---

name: banco-de-dados-performance
description: "Otimização de banco relacional (PostgreSQL/MySQL) no nível SQL/SGBD — EXPLAIN ANALYZE, estratégia de índices, tuning de configuração, vacuum/bloat, replicação, JSONB/GIN, connection pooling, identificação de slow query. Use ao investigar query lenta, criar índice ou tunar configuração. Uso: agent `especialista-banco-dados` ou `/banco-de-dados-performance`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.1.0"
  domain: database
  triggers: query lenta, EXPLAIN, índice, tuning de banco, N+1, plano de execução, pg_stat_statements, slow query, vacuum, JSONB
  role: specialist
  scope: database
  output-format: document
  related-skills: persistencia-jpa, arquitetura-limpa-java, design-system-architecture
---

# Banco de Dados — Performance e Tuning

Guia de otimização de banco de dados relacional (PostgreSQL e MySQL) focado em performance de query,
design de índice e tuning de configuração.

## Quando usar / Quando NÃO usar

**Use** ao investigar query lenta, criar índice, tunar configuração, dimensionar pool de conexões ou
diagnosticar vacuum/bloat e replicação.

**Quando NÃO usar:** para problemas de JPA/Hibernate (N+1, `LazyInitializationException`, dirty
checking) ou a query JPA específica (JPQL, `@EntityGraph`), use `persistencia-jpa` — esta skill é
o lado SQL/SGBD. Para em qual camada mora o Repository, use `arquitetura-limpa-java`.

## Entradas

- A query (ou o sintoma: latência, CPU, locks) e o SGBD/versão (PostgreSQL 13+ ou MySQL 8.x).
- Volume aproximado das tabelas e acesso a `EXPLAIN ANALYZE` em ambiente que não seja produção.
- Para pool: número máximo de réplicas da aplicação, jobs e o teto de conexões do banco.

## Decisão

| Sintoma / pergunta | Onde ver |
|---|---|
| Query lenta, não sei qual | [planos-e-slow-queries](references/planos-e-slow-queries.md) + seção 1 de [diagnostico-postgresql.sql](assets/diagnostico-postgresql.sql) |
| Tenho a query, preciso entender o plano | [planos-e-slow-queries](references/planos-e-slow-queries.md) |
| `Seq Scan` / falta de índice / índice não usado | [indices](references/indices.md) + seção 4 do SQL de diagnóstico |
| Query mal escrita (subquery correlata, `COUNT`, `SELECT *`) | [reescrita-sql](references/reescrita-sql.md) |
| Dead tuples, bloat, JSONB, lag de réplica | [jsonb-vacuum-replicacao](references/jsonb-vacuum-replicacao.md) + seções 2 e 5 |
| Sessão travada / lock bloqueante | Seção 3 do SQL de diagnóstico; [locking](../persistencia-jpa/references/locking.md) para o lado JPA |
| Memória, planner, timeouts, slow query log | [tuning-postgresql-mysql](references/tuning-postgresql-mysql.md) |
| Pool saturado, quantas conexões por réplica | [orcamento-conexoes](references/orcamento-conexoes.md) |

## Passo a passo (checklist)

1. **Capture a baseline** — rode `EXPLAIN ANALYZE` **antes** de qualquer mudança; salve tempo real
   e custo estimado para comparar depois.
2. **Identifique gargalos** — query ineficiente, índice faltando, configuração errada, conexão
   saturada.
3. **Projete a solução** — estratégia de índice, rewrite de query, ajuste de schema.
4. **Aplique incrementalmente** — uma mudança por vez, com monitoramento.
5. **Valide o resultado** — re-rodar `EXPLAIN ANALYZE`, comparar custo, documentar a mudança.

> ⚠️ **Sempre teste em não-produção primeiro.** Reverta imediatamente se a performance de escrita
> regredir ou se a replicação aumentar lag.

## Saída

Parecer com baseline e resultado (planos `EXPLAIN ANALYZE` antes/depois, tempo e custo), a mudança aplicada
(índice, reescrita ou parâmetro) com o script de rollback, e o impacto observado em escrita e replicação.

## Validação

- Plano re-executado após a mudança com custo/tempo menores e o índice efetivamente usado (`idx_scan` > 0).
- Escrita e lag de replicação não pioraram; `ANALYZE` rodado após mudanças em massa.
- Soma dos pools de **todas** as réplicas cabe no orçamento do banco (ver [orcamento-conexoes](references/orcamento-conexoes.md)).

## Gotchas

- `CREATE INDEX CONCURRENTLY` não roda em transação, deixa índice `INVALID` se falhar e não funciona na tabela-pai particionada.
- MySQL não tem `CONCURRENTLY`, índice parcial nem `INCLUDE`: não aplique sintaxe de um SGBD ao outro.
- `work_mem` é por operação e por conexão: eleve por sessão (`SET LOCAL`), não globalmente.
- Estatísticas de `idx_scan = 0` só valem desde o último reset e por instância: confira réplicas antes de remover índice.
- Virtual threads não criam conexões: milhares delas só aumentam a fila do pool.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [references/reescrita-sql.md](references/reescrita-sql.md) | CTE, window functions, `EXISTS` vs `COUNT`, subquery correlata → JOIN, prepared statements, `SELECT *` |
| [references/planos-e-slow-queries.md](references/planos-e-slow-queries.md) | `EXPLAIN ANALYZE`, padrões de plano, top slow queries |
| [references/indices.md](references/indices.md) | Covering/parcial, `CONCURRENTLY`, validação de uso, diferenças no MySQL |
| [references/jsonb-vacuum-replicacao.md](references/jsonb-vacuum-replicacao.md) | JSONB/GIN, dead tuples e vacuum, lag de replicação |
| [references/tuning-postgresql-mysql.md](references/tuning-postgresql-mysql.md) | Parâmetros-chave de PostgreSQL e MySQL |
| [references/orcamento-conexoes.md](references/orcamento-conexoes.md) | **Fonte única** do orçamento de conexões e HikariCP |
| [assets/diagnostico-postgresql.sql](assets/diagnostico-postgresql.sql) | Consultas de diagnóstico: top slow queries, bloat, locks, índices não usados, lag |

Código executável relacionado (em `examples/java`):
[`ProcessadorIdempotenteExternoIT`](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java)
(restrição única arbitrando concorrência em PostgreSQL real),
[`ControleConcorrencia`](../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ControleConcorrencia.java)
(limite de concorrência sobre recurso escasso, como o pool).

## Regras obrigatórias (constraints)

## MUST DO
- Capture `EXPLAIN (ANALYZE, BUFFERS)` **antes** de otimizar — essa é a baseline.
- Meça performance antes e depois de cada mudança.
- Crie índices com `CONCURRENTLY` (PostgreSQL) para evitar table locks.
- Teste em não-produção; reverta se write performance ou replication lag piorar.
- Documente toda decisão de otimização com métricas antes/depois.
- Rode `ANALYZE` após mudanças em massa para atualizar estatísticas.
- Use connection pooling e some o pool de **todas** as réplicas contra o orçamento do banco.
- Defina timeouts de aquisição de conexão, consulta e transação ociosa.
- Separe orientações de PostgreSQL e MySQL; não aplique sintaxe/recurso de um ao outro.
- Use prepared statements para prevenir SQL injection.

## MUST NOT DO
- Aplique otimizações sem baseline medida.
- Crie índices redundantes ou não usados.
- Faça múltiplas mudanças simultâneas (impossível atribuir impacto).
- Ignore write amplification causado por índices novos.
- Negligence VACUUM / manutenção de estatísticas.
- Use `SELECT *` em produção.
- Use `cursor` quando set-based operations funcionam.
- Desabilite autovacuum globalmente.

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Investigar query lenta, criar índice | sessão principal | esta skill |
| Resolver N+1, `LazyInitializationException` em JPA | agent `java-revisor` | `persistencia-jpa` |
| Revisão de query gerada por SQL nativo (Hibernate `nativeQuery`) | agent `java-revisor` (modo `auditoria`) | esta skill + `persistencia-jpa` |
| Configurar banco novo (PostgreSQL tuning) | session principal | esta skill |
