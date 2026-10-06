# Exemplos Java executáveis

Fonte testada dos trechos citados pelas skills. Pacote-base `br.com.srportto.exemplos`; Java 25 sem preview;
Maven multi-módulo. Cada classe de `src/main/java` tem prova de mesmo nome (`<Classe>Test`, `<Classe>IT`,
`<Classe>ExternoIT` ou `<Classe>CargaIT`), verificada por `validation/java`.

## Módulos

| Módulo | Classes | Dependências |
|---|---|---|
| `fundamentos` | `FilaLimitada`, `ControleConcorrencia`, `OrcamentoTempo`, `PoliticaRetry`, `TokenBucket`, `LeakyBucket`, `AdmissaoPorPrioridade`, `ChamadaComDeadline`, `FallbackDegradado`, `EncerramentoControlado` | somente JDK |
| `reativo` | `FluxoSobDemanda` (+ `ProtecoesTest` com Resilience4j) | Reactor, Resilience4j |
| `integracao` | `ProcessadorIdempotente`, `PublicadorOutbox`, `ReplayControlado`, `ConsumoControlado`, `ConsumidorKafkaLimitado`, `ConsumidorSqsLimitado`, `CacheProtegido`, `LimiteDistribuido`, `RecuperacaoPendencias`, `MetricasProtecao`, `SaudeAplicacao`, `CheckoutApplication` | Spring Boot 4, JDBC/H2, kafka-clients, AWS SDK SQS, Jedis, Micrometer; Testcontainers nos `ExternoIT` |
| `carga` | `CheckoutSobCargaSimulation` | módulos acima |

## Comandos

```bash
mvn -f examples/java/pom.xml verify                    # testes determinísticos (sem Docker)
mvn -f examples/java/pom.xml -Pintegracao verify       # + *ExternoIT com containers (Docker obrigatório)
mvn -f examples/java/pom.xml -Pcarga verify            # + *CargaIT (ensaio sintético; relatório em carga/target)
```

O perfil `integracao` **falha** sem Docker: ausência de ambiente é pendência, não aprovação.

| Prova externa (`-Pintegracao`) | Serviço real | O que demonstra |
|---|---|---|
| `ConsumidorKafkaLimitadoExternoIT` | Kafka + PostgreSQL | Dois consumidores, rebalance, falha antes do commit e reinício sem perda nem duplicata |
| `ConsumidorSqsLimitadoExternoIT` | LocalStack (SQS) | Mensagem que sempre falha chega à DLQ pela RedrivePolicy; demais apagadas só após o efeito |
| `ProcessadorIdempotenteExternoIT` | PostgreSQL | 16 conexões concorrentes com a mesma chave geram um único pedido |
| `LimiteDistribuidoExternoIT` | Valkey | Quota exata entre réplicas; chave órfã sem TTL corrigida |
| `RecuperacaoPendenciasExternoIT` | Valkey | XAUTOCLAIM recupera pendências de consumidor morto, sem roubar de consumidor vivo |
| `ExperimentoCoordenadorLentoExternoIT` | Valkey + Toxiproxy | Experimento de chaos: degradação limitada e recuperação |

Números produzidos por estes exemplos são de laboratório: provam invariantes (limite respeitado, rejeição
visível, efeito único, recuperação), não SLO de produção. Versões, imagens e resultados registrados em
[compatibilidade](../../docs/catalogo/compatibilidade.md).
