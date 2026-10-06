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

## Visão geral

Guia de otimização de banco de dados relacional (PostgreSQL e MySQL) focado em performance de query,
design de índice e tuning de configuração.

**Quando NÃO usar:** para problemas de JPA/Hibernate (N+1, `LazyInitializationException`, dirty
checking) ou a query JPA específica (JPQL, `@EntityGraph`), use `persistencia-jpa` — esta skill é
o lado SQL/SGBD. Para em qual camada mora o Repository, use `arquitetura-limpa-java`.

## Workflow

1. **Capture a baseline** — rode `EXPLAIN ANALYZE` **antes** de qualquer mudança; salve tempo real
   e custo estimado para comparar depois.
2. **Identifique gargalos** — query ineficiente, índice faltando, configuração errada, conexão
   saturada.
3. **Projete a solução** — estratégia de índice, rewrite de query, ajuste de schema.
4. **Aplique incrementalmente** — uma mudança por vez, com monitoramento.
5. **Valide o resultado** — re-rodar `EXPLAIN ANALYZE`, comparar custo, documentar a mudança.

> ⚠️ **Sempre teste em não-produção primeiro.** Reverta imediatamente se a performance de escrita
> regredir ou se a replicação aumentar lag.

---

# SQL Query Patterns

## CTE — Common Table Expressions

```sql
-- Isola lógica cara de subquery para reuso e legibilidade
WITH ranked_orders AS (
    SELECT
        customer_id,
        order_id,
        total_amount,
        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY order_date DESC) AS rn
    FROM orders
    WHERE status = 'completed'          -- filtra cedo, antes do join
)
SELECT customer_id, order_id, total_amount
FROM ranked_orders
WHERE rn = 1;                           -- último pedido completed por customer
```

## Window Functions

```sql
-- Running total e rank dentro de uma partição — sem self-join
SELECT department_id, employee_id, salary,
    SUM(salary) OVER (PARTITION BY department_id ORDER BY hire_date) AS running_payroll,
    RANK()      OVER (PARTITION BY department_id ORDER BY salary DESC) AS salary_rank
FROM employees;
```

## EXISTS vs COUNT

```sql
SELECT COUNT(*) FROM orders WHERE customer_id = 42;              -- RUIM: conta todas as linhas
SELECT EXISTS(SELECT 1 FROM orders WHERE customer_id = 42);      -- BOM: para no primeiro match
```

## Correlated Subquery → JOIN Rewrite

```sql
-- ANTES: subquery correlata, uma execução por linha (lento)
SELECT order_id,
       (SELECT SUM(quantity) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
FROM orders o;

-- DEPOIS: agregação em um único join (rápido)
SELECT o.order_id, COALESCE(agg.item_count, 0) AS item_count
FROM orders o
LEFT JOIN (
    SELECT order_id, SUM(quantity) AS item_count
    FROM order_items
    GROUP BY order_id
) agg ON agg.order_id = o.id;
```

> Crie um covering index de apoio para o `GROUP BY` — ver seção "Estratégias de Índice" abaixo.

---

# EXPLAIN ANALYZE

## PostgreSQL — capturando o plano

```sql
-- Sempre use ANALYZE para ver row count real vs estimado
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM orders o
JOIN customers c ON c.id = o.customer_id
WHERE o.created_at > NOW() - INTERVAL '30 days';
```

### Padrões a procurar

| Padrão | Sintoma | Remédio típico |
|--------|---------|----------------|
| `Seq Scan` em tabela grande | Row estimate alto, sem seletividade de filtro | Adicionar B-tree index na coluna do filtro |
| `Nested Loop` com outer set grande | Crescimento exponencial de rows no inner loop | Considerar Hash Join; index na chave do inner |
| `cost=... rows=1` mas `actual rows=50000` | Estatísticas desatualizadas | Rodar `ANALYZE <tabela>;` |
| `Buffers: hit=10 read=90000` | Baixo hit rate do buffer cache | Aumentar `shared_buffers`; adicionar covering index |
| `Sort Method: external merge` | Sort derramando para disco | Aumentar `work_mem` para a sessão |

## Top slow queries

```sql
-- PostgreSQL: requer extensão pg_stat_statements
SELECT query, calls, round(mean_exec_time::numeric, 2) AS mean_ms, rows
FROM   pg_stat_statements
ORDER  BY mean_exec_time DESC
LIMIT  20;

-- MySQL: candidatos do slow query log
SELECT * FROM performance_schema.events_statements_summary_by_digest
ORDER  BY SUM_TIMER_WAIT DESC
LIMIT  20;
```

---

# Estratégias de Índice

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

---

# Features específicas do PostgreSQL

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

---

# Tuning de configuração

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

---

# Padrões de uso na aplicação

## Orçamento de conexões (obrigatório em produção)

O pool é dimensionado pelo **banco**, não pela aplicação: o banco tem um número de conexões ativas que consegue
servir bem (a heurística do HikariCP parte de `núcleos do servidor de banco × 2 + discos`), e esse número é
**dividido** entre todas as réplicas, jobs e ferramentas.

```
Σ (réplicas máximas × pool por réplica) + jobs + admin + replicação ≤ orçamento seguro do banco
ex.: autoscaling até 10 réplicas × 15 = 150; jobs 10; admin 5 → 165 ≤ 180 aceitos  ✔
     se o HPA puder ir a 20 réplicas → 315  ✘ (teto do HPA ou pool menor, ou proxy de conexões)
```

```yaml
# Spring Boot / HikariCP — cada número tem motivo e unidade
spring:
  datasource:
    hikari:
      maximum-pool-size: 15        # orçamento do banco ÷ réplicas máximas
      minimum-idle: 15             # pool fixo evita picos de abertura de conexão
      connection-timeout: 2000     # ms esperando conexão do pool: falha visível em vez de fila infinita
      max-lifetime: 1500000        # ms; menor que timeouts de rede/proxy/banco
      leak-detection-threshold: 20000  # ms; alerta de conexão não devolvida
```

- **Espera pelo pool é uma fila**: limite-a (`connection-timeout`) e trate o timeout como saturação (503), não
  como erro aleatório. Virtual threads não criam conexões: milhares delas só aumentam a fila.
- **Timeout de consulta e de transação** (`statement_timeout`/`setQueryTimeout`, `@Transactional(timeout = ...)`)
  menores que o deadline da requisição; não segure conexão durante chamada HTTP externa nem durante backoff.
- **Isolamento de cargas (bulkhead):** relatórios e jobs em pool separado (ou réplica de leitura), com tamanho
  próprio, para não esgotar o pool do fluxo transacional.
- **Proxy de conexões:** pgBouncer (modo transaction) ou RDS Proxy multiplexa muitas conexões de cliente em
  poucas do servidor — útil com muitas réplicas/serverless. Em modo transaction, recursos de sessão
  (`SET` de sessão, advisory locks de sessão, `LISTEN`) não funcionam; prepared statements exigem pgBouncer
  ≥ 1.21 com `max_prepared_statements`. MySQL: ProxySQL.
- **Nunca** abra conexão por operação sem pool.

Métricas: conexões ativas/ociosas/pendentes (`hikaricp_connections_*`), tempo de aquisição (p99), timeouts de
aquisição, duração de transação. Pendentes > 0 de forma sustentada = pool ou banco saturado.

## Prepared statements e `SELECT *`

```java
// SEMPRE parametrizado — protege de SQL injection E permite o planner cachear o plano
PreparedStatement ps = connection.prepareStatement("SELECT id, email FROM users WHERE email = ?");
ps.setString(1, email);
```

```sql
SELECT * FROM orders WHERE customer_id = 42;                    -- RUIM: colunas desnecessárias
SELECT id, status, total_amount FROM orders WHERE customer_id = 42;  -- BOM: explícito, covering
```

---

# Constraints

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
