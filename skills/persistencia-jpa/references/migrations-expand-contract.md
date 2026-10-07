Leia este arquivo quando for mudar o schema com a aplicação rodando em várias versões ao mesmo tempo (rolling deploy), planejar rollback ou criar índice em tabela grande dentro de uma migration.

## Migrations: expand/contract e rollback

Mudança de schema com aplicação rodando em várias versões ao mesmo tempo (rolling deploy):

1. **Expand** — adicionar coluna/tabela **compatível** (nullable ou com default), sem remover nada.
2. **Migrar** — aplicação nova escreve nos dois formatos; backfill em lotes pequenos, com pausa e medição de lag.
3. **Contract** — remover o formato antigo só depois que nenhuma versão antiga roda (e após janela de rollback).

Rollback de aplicação precisa funcionar com o schema já expandido; rollback de schema destrutivo (drop) não
existe na prática — por isso o drop vem por último. Índices em tabelas grandes seguem
`banco-de-dados-performance` (`CONCURRENTLY` no PostgreSQL, Online DDL no MySQL; migration não transacional).


## Antes/depois: renomear coluna sem derrubar a versão antiga

Renomear `nome` para `nome_completo` num único passo quebra as réplicas ainda na versão anterior durante o
rolling deploy. Em três migrations (Flyway), cada uma compatível com a aplicação em execução:

```sql
-- ANTES - quebra a versão antiga da aplicação no instante em que roda
ALTER TABLE clientes RENAME COLUMN nome TO nome_completo;
```

```sql
-- V10__expand_nome_completo.sql  (EXPAND: aditiva, nullable, nada removido)
ALTER TABLE clientes ADD COLUMN nome_completo VARCHAR(200);

-- V11__backfill_nome_completo.sql  (MIGRAR: em lotes pequenos; repita até 0 linhas)
UPDATE clientes SET nome_completo = nome
WHERE id IN (SELECT id FROM clientes WHERE nome_completo IS NULL ORDER BY id LIMIT 5000);

-- V12__contract_nome.sql  (CONTRACT: só depois que nenhuma versão antiga roda e a janela de rollback passou)
ALTER TABLE clientes DROP COLUMN nome;
```

Entre V10 e V12 a aplicação nova escreve nos **dois** campos (e lê do novo, com fallback no antigo):

```java
// Java 25 - fase "migrar": escreve nos dois formatos para que o rollback da aplicação continue funcionando
public void atualizarNome(ClienteEntity cliente, String nome) {
    cliente.setNome(nome);          // formato antigo (será removido no contract)
    cliente.setNomeCompleto(nome);  // formato novo
}
```

Índices em tabela grande dentro de uma migration: `CREATE INDEX CONCURRENTLY` não roda em transação, então
a migration precisa ser marcada como não transacional (veja
[indices](../../banco-de-dados-performance/references/indices.md)).
