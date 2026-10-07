Leia este arquivo quando a aplicação lê de réplica de leitura, quando o usuário não vê o que acabou de gravar (read-your-writes) ou ao decidir o que nunca pode ser lido de réplica.

## Leitura em réplica com atraso

Réplicas de leitura têm atraso (segundos, às vezes minutos sob carga). Consequências:

- **Read-your-writes** quebra: o usuário cria o pedido (primário) e a listagem (réplica) não mostra. Leia do
  primário logo após escrever (por sessão/tempo), ou devolva o recurso criado na própria resposta.
- Decisão de negócio (saldo, estoque, idempotência) **sempre** no primário.
- Roteamento: `AbstractRoutingDataSource` com `@Transactional(readOnly = true)` → réplica é comum; meça o lag
  (`pg_stat_replication`, `Seconds_Behind_Source`) e tire a réplica do roteamento quando passar do tolerado.


## Antes/depois: roteamento primário/réplica sem quebrar read-your-writes

```java
// ANTES - tudo no primário: a réplica fica ociosa e relatórios disputam conexão com o fluxo transacional
@Transactional(readOnly = true)
public List<PedidoResumo> relatorio() { /* vai ao primário */ }
```

```java
// DEPOIS - leitura somente-leitura vai à réplica; escrita e decisão de negócio ficam no primário
public class RoteadorLeituraEscrita extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "replica" : "primario";
    }
}

@Configuration
class DataSourceConfig {

    @Bean
    DataSource dataSource(@Qualifier("primario") DataSource primario,
                          @Qualifier("replica") DataSource replica) {
        var roteador = new RoteadorLeituraEscrita();
        roteador.setTargetDataSources(Map.of("primario", primario, "replica", replica));
        roteador.setDefaultTargetDataSource(primario);
        roteador.afterPropertiesSet();
        // Lazy: a conexão só é escolhida depois que o Spring definiu readOnly na transação
        return new LazyConnectionDataSourceProxy(roteador);
    }
}
```

Regras que o roteamento **não** resolve sozinho: logo após escrever, leia do primário (ou devolva o recurso
criado na resposta); saldo, estoque e idempotência nunca leem de réplica. Para medir o atraso e tirar a
réplica do roteamento, use a consulta de lag de
[diagnostico-postgresql.sql](../../banco-de-dados-performance/assets/diagnostico-postgresql.sql) e o
detalhe em [jsonb-vacuum-replicacao](../../banco-de-dados-performance/references/jsonb-vacuum-replicacao.md).
