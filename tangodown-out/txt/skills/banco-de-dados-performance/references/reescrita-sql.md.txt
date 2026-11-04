Leia este arquivo quando for reescrever uma query lenta (CTE, window function, `EXISTS` vs `COUNT`, subquery correlata para JOIN) ou revisar SQL da aplicação (prepared statements, `SELECT *`).

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

## Antes/depois: SQL concatenado vs parametrizado (Java 25)

```java
// ANTES - concatenação: abre SQL injection e impede o banco de reaproveitar o plano
String sql = "SELECT id, email FROM users WHERE email = '" + email + "'";
try (var stmt = connection.createStatement(); var rs = stmt.executeQuery(sql)) {
    // ...
}
```

```java
// DEPOIS - parâmetro posicional, colunas explícitas e text block para SQL maior
String sql = """
        SELECT id, status, total_amount
        FROM orders
        WHERE customer_id = ? AND status = ?
        ORDER BY created_at DESC
        LIMIT 50
        """;
try (var ps = connection.prepareStatement(sql)) {
    ps.setLong(1, customerId);
    ps.setString(2, "completed");
    try (var rs = ps.executeQuery()) {
        while (rs.next()) {
            // lê só as 3 colunas projetadas (permite index-only scan com o covering index)
        }
    }
}
```

## Antes/depois: paginação por offset vs keyset

```sql
-- ANTES - OFFSET grande lê e descarta 100.000 linhas a cada página (custo cresce com a página)
SELECT id, status, total_amount FROM orders ORDER BY id LIMIT 50 OFFSET 100000;

-- DEPOIS - keyset: continua a partir da última chave vista, custo constante com índice em id
SELECT id, status, total_amount FROM orders WHERE id > :ultimo_id ORDER BY id LIMIT 50;
```

Para o contrato HTTP de cursor, veja a skill `api-rest-design`
([paginacao](../../api-rest-design/references/paginacao.md)); o lado JPA está em
[n-mais-um](../../persistencia-jpa/references/n-mais-um.md).
