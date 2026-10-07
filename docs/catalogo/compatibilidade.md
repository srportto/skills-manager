# Compatibilidade e versões

Versões efetivamente resolvidas pelos módulos executáveis e o resultado da última execução registrada. Ao mudar
JDK, Spring Boot, clientes ou brokers: atualize as versões, rode os comandos abaixo e substitua a tabela de
execuções — sem resultado novo, a linha antiga continua valendo só para a versão antiga.

## Ambiente da última execução (2026-10-06)

| Item | Versão |
|---|---|
| Sistema | Windows 11 Pro 10.0.26100, 24 CPUs lógicas |
| JDK | Temurin 25.0.2-beta+8 (sem `--enable-preview`) |
| Maven | 3.8.4 |
| Docker | 29.7.2 |

## Bibliotecas (resolvidas por `mvn dependency:list`)

| Biblioteca | Versão | Origem da versão |
|---|---|---|
| Spring Boot / Spring Framework (webmvc) | 4.0.7 / 7.0.8 | BOM `spring-boot-dependencies` |
| JUnit Jupiter | 6.0.3 | fixada no POM pai |
| Reactor Core | 3.8.6 | BOM do Boot |
| Resilience4j (circuitbreaker, bulkhead) | 2.3.0 | fixada (sem starter Spring; API Java pura) |
| Micrometer | 1.16.6 | BOM do Boot |
| kafka-clients | 4.1.2 | BOM do Boot |
| AWS SDK v2 (sqs) | 2.31.54 | fixada |
| Jedis | 7.0.0 | BOM do Boot |
| H2 | 2.4.240 | BOM do Boot |
| PostgreSQL JDBC | 42.7.11 | BOM do Boot |
| Testcontainers (core, postgresql, kafka, localstack, toxiproxy) | 2.0.5 | fixada |
| toxiproxy-java | 2.1.11 | transitiva do Testcontainers |
| SnakeYAML (validação do catálogo) | 2.5 | fixada |

## Imagens de container (perfil `integracao`)

| Serviço | Imagem | Observação |
|---|---|---|
| PostgreSQL | `postgres:18-alpine` | |
| Kafka | `confluentinc/cp-kafka:7.7.1` | `ConfluentKafkaContainer` |
| SQS | `localstack/localstack:4.4` | `latest` (2026.7) exige licença: falha com "License activation failed" (exit 55) |
| Valkey | `valkey/valkey:8` | |
| Toxiproxy | `ghcr.io/shopify/toxiproxy:2.12.0` | |

## Execuções

| Comando | Resultado (2026-10-06) |
|---|---|
| `mvn -f validation/java/pom.xml verify` | 20 testes, 0 falhas (inclui 6 casos do detector de recursos externos) |
| `mvn -f examples/java/pom.xml verify` | 96 testes (fundamentos 36, reativo 5, integração 55), 0 falhas |
| `mvn -f examples/java/pom.xml -Pintegracao verify` | 96 unitários + 7 `ExternoIT` (Kafka+PostgreSQL, SQS/LocalStack, Valkey ×2, PostgreSQL, Toxiproxy), 0 falhas |
| `mvn -f examples/java/pom.xml -Pcarga verify` | 1 `CargaIT`, 0 falhas — tabela abaixo |

### Ensaio de carga do checkout (laboratório)

Parâmetros: capacidade de admissão 8 simultâneas, latência simulada do pagamento 20 ms (capacidade teórica
≈ 400 req/s), gerador em malha aberta com limite de 300 em voo, H2 em memória, mesma máquina para gerador e alvo.

| Fase | Oferta | Oferecidas | Aceitas | Rejeitadas (503) | Erros | p50 aceitas | p99 aceitas |
|---|---|---|---|---|---|---|---|
| baseline | 50 rps × 2 s | 100 | 100 | 0 | 0 | 23 ms | 141 ms (aquecimento da JVM) |
| rampa | 150 rps × 2 s | 300 | 300 | 0 | 0 | 22 ms | 24 ms |
| pico | 1.000 rps × 3 s | 3.000 | 1.115 (~372/s) | 1.885 | 0 | 22 ms | 24 ms |
| retorno | 50 rps × 2 s | 100 | 100 | 0 | 0 | 21 ms | 23 ms |

Pedidos gravados = aceitas (nenhum efeito duplicado). **Não é SLO de produção**: prova os invariantes (rejeição
cedo e medida, latência limitada dos aceitos, recuperação), não a capacidade de um ambiente real.

## Pendências conhecidas

- Avaliação comportamental: A01, A03, A05, A07 e A08 executadas duas vezes cada; A02, A04, A06 e A09–A12
  pendentes. A amostra obteve 70% e pede correções em A05, A07 e A08 — ver [avaliações](avaliacoes-agents.md).
- Referências `chaos-tools`, `game-days`, `infrastructure-chaos` e `kubernetes-chaos` agora tratam ferramentas
  como conceitos e apontam para o exemplo Java `ExperimentoCoordenadorLentoExternoIT`.
- Workflow `.github/workflows/validar-catalogo.yml` ainda sem execução no GitHub Actions; o ambiente desta
  sessão não disponibiliza `gh` para abrir PR e inspecionar os jobs remotos.
