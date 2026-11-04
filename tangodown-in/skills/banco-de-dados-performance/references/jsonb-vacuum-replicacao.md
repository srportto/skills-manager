Leia este arquivo quando houver consulta em JSONB (índice GIN), suspeita de bloat/dead tuples, decisão sobre VACUUM manual ou necessidade de medir o lag de replicação no PostgreSQL.

## JSONB — GIN index e query

```sql
CREATE INDEX idx_events_payload ON events USING GIN (payload);

-- Query de contenção eficiente (usa o GIN) e extração de valor aninhado
SELECT payload->>'user_id', payload->'meta'->>'ip'
FROM events WHERE payload @> '{"type": "login", "success": true}';
```

## VACUUM, bloat e replication lag

```sql
-- Tabelas com alta contagem de dead tuples
SELECT relname, n_dead_tup, n_live_tup, last_autovacuum
FROM pg_stat_user_tables ORDER BY n_dead_tup DESC LIMIT 20;

VACUUM (ANALYZE, VERBOSE) orders;   -- manual em tabela com churn alto

-- Na primary: lag dos standbys
SELECT client_addr, state, (sent_lsn - replay_lsn) AS replication_lag_bytes
FROM pg_stat_replication;
```

## Antes/depois: autovacuum padrão vs ajustado por tabela com churn alto

```sql
-- ANTES - autovacuum global dispara com 20% de tuplas mortas: numa tabela de 50 milhões de linhas
-- isso são 10 milhões de linhas mortas acumuladas antes da primeira limpeza
SELECT relname, n_dead_tup, n_live_tup, last_autovacuum
FROM pg_stat_user_tables WHERE relname = 'events';
```

```sql
-- DEPOIS - limiar proporcional menor só nessa tabela (não desabilite o autovacuum globalmente)
ALTER TABLE events SET (
    autovacuum_vacuum_scale_factor = 0.01,   -- 1% de tuplas mortas (padrão: 0.2)
    autovacuum_analyze_scale_factor = 0.02
);
```

## Antes/depois: JSONB sem e com GIN

```sql
-- ANTES - Seq Scan: o planner precisa abrir o JSONB de cada linha
EXPLAIN ANALYZE SELECT count(*) FROM events WHERE payload @> '{"type": "login"}';

-- DEPOIS - com GIN, o operador de contenção (@>) usa Bitmap Index Scan
CREATE INDEX CONCURRENTLY idx_events_payload ON events USING GIN (payload jsonb_path_ops);
EXPLAIN ANALYZE SELECT count(*) FROM events WHERE payload @> '{"type": "login"}';
```

`jsonb_path_ops` gera índice menor e mais rápido, mas só atende `@>`; use o `GIN` padrão se precisar de
`?`, `?|` e `?&`. Para medir bloat, tuplas mortas e lag de réplica com consultas prontas, use as seções 2 e 5
de [diagnostico-postgresql.sql](../assets/diagnostico-postgresql.sql). O impacto da réplica no código
(read-your-writes) está em [replica-leitura](../../persistencia-jpa/references/replica-leitura.md).
