Leia este arquivo quando for criar, validar ou remover índice (covering, parcial, `CREATE INDEX CONCURRENTLY`), ou comparar com as limitações do MySQL (Online DDL, sem índice parcial).

## Covering Index

```sql
-- Cobre o filtro E as colunas projetadas, eliminando o heap fetch
CREATE INDEX CONCURRENTLY idx_orders_status_created_covering
    ON orders (status, created_at)
    INCLUDE (customer_id, total_amount);
```

## Partial Index

```sql
-- Partial index para filtro seletivo — menor, mais rápido
CREATE INDEX CONCURRENTLY idx_orders_pending
    ON orders (customer_id, created_at)
    WHERE status = 'pending';
```

## Validar uso do índice

```sql
-- Confirmar que o índice é realmente usado
SELECT indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM   pg_stat_user_indexes
WHERE  relname = 'orders';
```

> **`CREATE INDEX CONCURRENTLY` (PostgreSQL):** não bloqueia escrita na tabela durante a criação
> (ao custo de levar mais tempo e fazer duas varreduras). Cuidados: não roda dentro de transação (migrations
> Flyway/Liquibase precisam marcá-la como não transacional); se falhar, deixa um índice **INVALID** que precisa
> ser removido (`DROP INDEX CONCURRENTLY`) e recriado; **não funciona na tabela-pai particionada** (crie em cada
> partição com `CONCURRENTLY` e depois no pai com `ON ONLY` + `ATTACH PARTITION`).

> **MySQL é diferente:** não tem `CONCURRENTLY`, índice parcial (`WHERE`) nem `INCLUDE`. Use Online DDL
> (`ALTER TABLE ... ADD INDEX ..., ALGORITHM=INPLACE, LOCK=NONE`) ou ferramentas como `gh-ost`/`pt-online-schema-change`
> em tabelas grandes; o índice "covering" no InnoDB é o índice secundário com as colunas consultadas (a PK já
> vem embutida em todo índice secundário).

## Antes/depois: índice redundante vs índice composto na ordem certa

```sql
-- ANTES - dois índices; o planner usa só um por vez e as escritas pagam os dois
CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_status   ON orders (status);
-- consulta: WHERE customer_id = 42 AND status = 'pending' ORDER BY created_at DESC
```

```sql
-- DEPOIS - um composto: igualdade primeiro (customer_id, status), depois a coluna do ORDER BY;
-- o parcial mantém o índice pequeno (só pedidos pendentes)
CREATE INDEX CONCURRENTLY idx_orders_customer_pending
    ON orders (customer_id, created_at DESC)
    WHERE status = 'pending';

DROP INDEX CONCURRENTLY idx_orders_status;   -- depois de confirmar idx_scan = 0 (veja abaixo)
```

Antes de remover um índice, confirme que ele não é usado e acompanhe o custo de escrita que ele impõe:
a seção 4 de [diagnostico-postgresql.sql](../assets/diagnostico-postgresql.sql) lista índices com
`idx_scan = 0` (exceto PK/UNIQUE) e índices `INVALID` deixados por `CREATE INDEX CONCURRENTLY` que falhou.

## Índice como regra de integridade

Um índice `UNIQUE` não é só performance: é a restrição que arbitra corridas (idempotência, duplicatas). O
teste contra PostgreSQL real em
[`ProcessadorIdempotenteExternoIT`](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java)
mostra 16 conexões concorrentes e uma única linha vencedora; a regra do lado JPA está em
[entidades-projecoes](../../persistencia-jpa/references/entidades-projecoes.md).
