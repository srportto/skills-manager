Leia este arquivo quando for ajustar parâmetros do servidor (PostgreSQL ou MySQL): memória, planner, timeouts e slow query log. Os valores são pontos de partida, não regras.

## PostgreSQL — parâmetros chave

Os valores abaixo são **pontos de partida comuns em servidor dedicado**, não regras: serviço gerenciado (RDS,
Cloud SQL) já ajusta vários deles, e a carga real decide. Mude um parâmetro por vez, com baseline e medição.

| Parâmetro | Default | Ponto de partida | Cuidado |
|-----------|---------|------------------|---------|
| `shared_buffers` | 128MB | ~25% da RAM | Acima de ~40% raramente ajuda (o SO também faz cache) |
| `work_mem` | 4MB | Calcular: RAM disponível ÷ (conexões ativas × operações de sort/hash por query) | É **por operação, por conexão**: 256MB × 100 conexões × 2 sorts = 50 GB → OOM. Prefira elevar por sessão/consulta (`SET LOCAL work_mem`) |
| `maintenance_work_mem` | 64MB | 256MB–1GB | Multiplicado por workers de autovacuum (`autovacuum_work_mem`) |
| `effective_cache_size` | 4GB | ~50–75% da RAM | Só uma dica ao planner; não aloca memória |
| `random_page_cost` | 4.0 | ~1.1 em SSD/NVMe | Mudar altera planos: compare antes/depois |
| `statement_timeout` | 0 (sem limite) | Por papel/aplicação (ex.: 5 s para OLTP) | Sem limite, query descontrolada segura conexão indefinidamente |
| `idle_in_transaction_session_timeout` | 0 | ex.: 30 s | Transação esquecida aberta segura locks e impede vacuum |
| `lock_timeout` | 0 | ex.: 2–5 s em migrations | DDL esperando lock bloqueia todas as queries atrás dela |

## MySQL — parâmetros chave

| Parâmetro | Default | Ponto de partida | Cuidado |
|-----------|---------|------------------|---------|
| `innodb_buffer_pool_size` | 128M | ~50–75% da RAM em servidor dedicado | Deixe memória para conexões e SO |
| `innodb_redo_log_capacity` (8.0.30+; substitui `innodb_log_file_size`) | 100M | Suficiente para ~1 h de escrita no pico | Muito pequeno força checkpoints frequentes |
| `max_connections` | 151 | Soma dos pools de todas as réplicas + reserva | Cada conexão consome memória por thread |
| `innodb_lock_wait_timeout` | 50 s | ex.: 5–10 s em OLTP | Espera de lock longa segura a conexão |
| `max_execution_time` | 0 | ex.: 5000 ms | Só vale para `SELECT` |
| `slow_query_log` + `long_query_time` | OFF / 10 s | ON / 0,5–1 s | Base para análise de slow query |

## Antes/depois: `work_mem` global alto vs elevado só na consulta pesada

```
# ANTES - postgresql.conf: 256MB por operação, por conexão. Com 100 conexões e 2 sorts cada: ~50 GB -> OOM
work_mem = 256MB
```

```sql
-- DEPOIS - padrão conservador no servidor (work_mem = 16MB) e elevação local, só nesta transação
BEGIN;
SET LOCAL work_mem = '256MB';          -- vale até o COMMIT/ROLLBACK
SELECT customer_id, sum(total_amount)
FROM orders
GROUP BY customer_id
ORDER BY sum(total_amount) DESC;
COMMIT;
```

## Antes/depois: timeouts por papel em vez de sem limite

```sql
-- ANTES - nenhum limite: query descontrolada ou transação esquecida segura conexão e locks indefinidamente
-- (statement_timeout = 0, idle_in_transaction_session_timeout = 0)

-- DEPOIS - limites por papel da aplicação (OLTP); migrations usam papel próprio com lock_timeout
ALTER ROLE app_oltp SET statement_timeout = '5s';
ALTER ROLE app_oltp SET idle_in_transaction_session_timeout = '30s';
ALTER ROLE app_migracao SET lock_timeout = '3s';
```

Meça sempre antes e depois de cada parâmetro (uma mudança por vez). O baseline de queries lentas e de
locks está nas seções 1 e 3 de [diagnostico-postgresql.sql](../assets/diagnostico-postgresql.sql).
