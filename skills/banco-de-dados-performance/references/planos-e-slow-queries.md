Leia este arquivo quando precisar capturar e interpretar um plano com `EXPLAIN ANALYZE` ou descobrir quais são as queries mais lentas (`pg_stat_statements` no PostgreSQL, `performance_schema` no MySQL).

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

## Antes/depois: plano com `Seq Scan` e o plano após o índice

Exemplo ilustrativo (os números mudam com o seu dado, o que importa é o formato da leitura):

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT id, total_amount FROM orders WHERE customer_id = 42;
```

```
-- ANTES: varre a tabela inteira para devolver 12 linhas
Seq Scan on orders  (cost=0.00..18334.00 rows=12 width=16) (actual time=0.031..96.420 rows=12 loops=1)
  Filter: (customer_id = 42)
  Rows Removed by Filter: 999988
  Buffers: shared hit=2000 read=6334
Execution Time: 96.510 ms
```

```sql
CREATE INDEX CONCURRENTLY idx_orders_customer ON orders (customer_id) INCLUDE (total_amount);
VACUUM (ANALYZE) orders;  -- VACUUM atualiza o visibility map; só ANALYZE não zera os Heap Fetches
```

```
-- DEPOIS: Index Only Scan, poucas páginas lidas
Index Only Scan using idx_orders_customer on orders  (cost=0.42..8.52 rows=12 width=16) (actual time=0.018..0.026 rows=12 loops=1)
  Index Cond: (customer_id = 42)
  Heap Fetches: 0
  Buffers: shared hit=4
Execution Time: 0.051 ms
```

Registre os dois planos (baseline e resultado) no PR ou ADR. Para achar *quais* queries valem esse esforço,
use a seção 1 de [diagnostico-postgresql.sql](../assets/diagnostico-postgresql.sql) (top slow queries por
tempo médio e por tempo total).

## Do slow query ao plano: fluxo curto

1. Rode a seção 1 de `diagnostico-postgresql.sql` e escolha a query de maior `total_exec_time`.
2. Reproduza com parâmetros reais em `EXPLAIN (ANALYZE, BUFFERS)` num ambiente que não seja produção.
3. Leia a tabela de padrões acima; mude **uma** coisa (índice, reescrita ou estatística) e repita o passo 2.
4. Se a query vem do Hibernate (N+1 ou SQL gerado), a correção costuma estar no código:
   [n-mais-um](../../persistencia-jpa/references/n-mais-um.md).
