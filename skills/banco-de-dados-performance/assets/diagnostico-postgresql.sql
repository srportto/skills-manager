-- =====================================================================
-- Diagnóstico de performance para PostgreSQL 13+ (somente leitura)
--
-- Como usar: rode as seções no psql ou no cliente SQL, uma por vez, e
-- registre o resultado ANTES de qualquer mudança (baseline). Nenhuma
-- consulta aqui altera dados. Em produção, rode numa janela de baixa
-- carga ou numa réplica de leitura quando a consulta varrer catálogos.
--
-- Pré-requisito da seção 1: extensão pg_stat_statements habilitada
--   postgresql.conf: shared_preload_libraries = 'pg_stat_statements'
--   (reinício necessário) e, uma vez por banco:
--   CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Top queries lentas (pg_stat_statements; colunas válidas no PG 13+)
--    No PG 12 ou anterior as colunas se chamam total_time / mean_time.
-- ---------------------------------------------------------------------

-- 1a. Maior tempo médio por chamada (queries individualmente lentas)
SELECT
    queryid,
    calls,
    round(mean_exec_time::numeric, 2)  AS media_ms,
    round(max_exec_time::numeric, 2)   AS maximo_ms,
    round(total_exec_time::numeric, 2) AS total_ms,
    rows,
    left(regexp_replace(query, '\s+', ' ', 'g'), 120) AS query
FROM pg_stat_statements
WHERE calls > 10                       -- ignora ruído de execuções raras
ORDER BY mean_exec_time DESC
LIMIT 20;

-- 1b. Maior tempo total acumulado (onde o banco gasta mais tempo no agregado)
SELECT
    queryid,
    calls,
    round(total_exec_time::numeric, 2) AS total_ms,
    round(mean_exec_time::numeric, 2)  AS media_ms,
    round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 2) AS pct_do_total,
    left(regexp_replace(query, '\s+', ' ', 'g'), 120) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;

-- 1c. Zerar as estatísticas depois de uma mudança, para medir limpo (use com critério)
-- SELECT pg_stat_statements_reset();


-- ---------------------------------------------------------------------
-- 2. Bloat: tuplas mortas e última limpeza (pg_stat_user_tables)
--    Estimativa barata, sem extensão. n_dead_tup alto + last_autovacuum
--    antigo = autovacuum não está acompanhando o churn da tabela.
-- ---------------------------------------------------------------------

SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    round(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS pct_mortas,
    last_autovacuum,
    last_autoanalyze,
    pg_size_pretty(pg_total_relation_size(relid)) AS tamanho_total
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC
LIMIT 20;

-- Remédio pontual numa tabela com churn alto (mede antes e depois):
-- VACUUM (ANALYZE, VERBOSE) nome_da_tabela;


-- ---------------------------------------------------------------------
-- 3. Locks bloqueantes: quem está esperando e quem está segurando
--    pg_blocking_pids() devolve os PIDs que bloqueiam a sessão.
-- ---------------------------------------------------------------------

SELECT
    bloqueada.pid                       AS pid_bloqueada,
    bloqueada.usename                   AS usuario_bloqueada,
    now() - bloqueada.query_start       AS esperando_ha,
    left(bloqueada.query, 100)          AS query_bloqueada,
    bloqueadora.pid                     AS pid_bloqueadora,
    bloqueadora.usename                 AS usuario_bloqueadora,
    bloqueadora.state                   AS estado_bloqueadora,
    now() - bloqueadora.xact_start      AS transacao_aberta_ha,
    left(bloqueadora.query, 100)        AS query_bloqueadora
FROM pg_stat_activity AS bloqueada
JOIN LATERAL unnest(pg_blocking_pids(bloqueada.pid)) AS b(pid) ON true
JOIN pg_stat_activity AS bloqueadora ON bloqueadora.pid = b.pid
ORDER BY esperando_ha DESC;

-- Locks ainda não concedidos, por tipo de objeto (visão de pg_locks)
SELECT locktype, mode, relation::regclass AS relacao, pid, granted
FROM pg_locks
WHERE NOT granted;

-- Transações abertas e ociosas (seguram lock e impedem vacuum):
SELECT pid, usename, state, now() - xact_start AS transacao_aberta_ha, left(query, 100) AS ultima_query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
ORDER BY xact_start;

-- Encerrar a sessão bloqueadora é último recurso (confirme o PID e avise o responsável):
-- SELECT pg_terminate_backend(<pid_bloqueadora>);


-- ---------------------------------------------------------------------
-- 4. Índices não usados (idx_scan = 0 desde o último reset das estatísticas)
--    Cuidados: confira quanto tempo as estatísticas acumulam
--    (pg_stat_database.stats_reset) e passe por todos os ambientes/réplicas
--    antes de remover. Índices de PK/UNIQUE são excluídos porque garantem
--    integridade mesmo sem leitura.
-- ---------------------------------------------------------------------

SELECT
    s.schemaname,
    s.relname                                        AS tabela,
    s.indexrelname                                   AS indice,
    s.idx_scan,
    pg_size_pretty(pg_relation_size(s.indexrelid))   AS tamanho
FROM pg_stat_user_indexes AS s
JOIN pg_index AS i ON i.indexrelid = s.indexrelid
WHERE s.idx_scan = 0
  AND NOT i.indisunique
  AND NOT i.indisprimary
ORDER BY pg_relation_size(s.indexrelid) DESC;

-- Remoção sem bloquear escrita (fora de transação):
-- DROP INDEX CONCURRENTLY nome_do_indice;

-- Índices INVALID deixados por um CREATE INDEX CONCURRENTLY que falhou:
SELECT c.relname AS indice, t.relname AS tabela
FROM pg_index AS i
JOIN pg_class AS c ON c.oid = i.indexrelid
JOIN pg_class AS t ON t.oid = i.indrelid
WHERE NOT i.indisvalid;


-- ---------------------------------------------------------------------
-- 5. Lag de replicação (executar no primário)
-- ---------------------------------------------------------------------

SELECT
    client_addr,
    state,
    pg_wal_lsn_diff(sent_lsn, replay_lsn) AS lag_bytes,
    replay_lag
FROM pg_stat_replication;
