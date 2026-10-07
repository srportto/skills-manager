Leia este arquivo quando for dimensionar pool de conexões (HikariCP), somar réplicas contra o limite do banco, definir timeouts de aquisição/consulta, separar pools por carga (bulkhead) ou decidir sobre proxy de conexões. **Fonte única** do orçamento de conexões: outras skills (ex.: `persistencia-jpa`) apontam para cá.

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

## Antes/depois: pool dimensionado "pelo palpite" vs pelo orçamento do banco

```yaml
# ANTES - 50 conexões por réplica "para garantir": com HPA até 10 réplicas são 500 conexões num banco
# que serve bem ~180; o excedente vira fila e timeouts em cascata
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
```

```yaml
# DEPOIS - pool = orçamento do banco ÷ réplicas máximas, com espera limitada e falha visível
spring:
  datasource:
    hikari:
      maximum-pool-size: 15        # 150 de 180 conexões para o serviço (10 réplicas × 15)
      minimum-idle: 15
      connection-timeout: 2000     # 2 s; estourou = saturação (503), não erro aleatório
      leak-detection-threshold: 20000
```

## Antes/depois: relatório disputando o pool transacional (Java 25)

```java
// ANTES - um único DataSource: o relatório pesado esgota o pool e o checkout (fluxo transacional) enfileira
@Service
public class RelatorioService {
    private final JdbcClient jdbc; // mesmo pool do fluxo transacional
}
```

```java
// DEPOIS - bulkhead: pool próprio (ou réplica de leitura) para relatórios e jobs, com tamanho limitado
@Bean
@ConfigurationProperties("app.datasource.relatorios")
HikariDataSource relatoriosDataSource() {
    return DataSourceBuilder.create().type(HikariDataSource.class).build(); // maximum-pool-size: 3
}

@Bean
JdbcClient relatoriosJdbc(@Qualifier("relatoriosDataSource") DataSource ds) {
    return JdbcClient.create(ds);
}
```

O efeito de limitar a concorrência sobre recurso escasso é demonstrado em
[`ControleConcorrencia`](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ControleConcorrencia.java)
(teste: [`ControleConcorrenciaTest`](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java));
o lado de transação curta e sem I/O externo está em
[transacoes](../../persistencia-jpa/references/transacoes.md). Para ver quem segura conexão ou lock agora,
use as seções 3 de [diagnostico-postgresql.sql](../assets/diagnostico-postgresql.sql).
