---
name: testes-sistemas-java
description: "Definir e executar evidências de comportamento em sistemas Java: concorrência, idempotência, contratos, arquitetura, falhas e carga. Use quando implementar ou revisar esses comportamentos; não exigir testes que apenas repitam texto ou estrutura de documentação. Uso: agents `java-construtor`/`java-revisor` ou `/testes-sistemas-java`; não carregar proativamente."
metadata:
  version: "1.1.0"
  related-skills: [arquitetura-limpa-java, resiliencia-controle-fluxo-java, revisao-de-codigo-java, mensageria-sqs-kafka]
---

# Testes de sistemas Java

Receba comportamento esperado, risco, fronteira e ambiente. Antes de escrever teste, diga qual **defeito
observável** ele detecta. Use Java 25/JUnit Jupiter; preserve a biblioteca de asserções do projeto quando suficiente.
Cada linha da tabela de decisão aponta para um teste que já roda em `examples/java`: copie o formato, não a prosa.

## Quando usar / quando NÃO usar

- **Usar:** implementar ou revisar concorrência, idempotência, retry, contrato HTTP, fronteira de arquitetura,
  integração com serviço real, falha injetada ou carga.
- **NÃO usar:** refatoração puramente editorial **não exige teste espelhando texto ou estrutura de documentação**;
  benchmark não substitui requisito; auditoria de segurança dedicada pertence a `seguranca-aplicacao-java`;
  desenho de experimento de falha em produção pertence a `chaos-engineer`.

## Entradas

Comportamento esperado (requisito observável), risco que se quer cobrir, fronteira (unidade, HTTP, banco, broker),
ambiente disponível (Docker? perfil `integracao`?) e a biblioteca de asserções já usada no projeto.

## Decisão: risco → tipo de teste → ferramenta → exemplo executável

| Risco | Tipo de teste | Ferramenta | Exemplo executável |
|---|---|---|---|
| Limite de concorrência violado, vazamento de permissão após falha | Unitário determinístico | `CountDownLatch`, `CyclicBarrier`, `AtomicInteger`, `@Timeout` | [ControleConcorrenciaTest](../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java) |
| Retry sem teto, sem deadline ou que repete falha não elegível | Unitário com relógio e espera injetados | `LongSupplier`/`Clock` falso, lambda de espera | [PoliticaRetryTest](../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/PoliticaRetryTest.java) |
| Chamada que passa do prazo total / trabalho órfão após timeout | Unitário com orçamento de tempo | `OrcamentoTempo`, latches | [ChamadaComDeadlineTest](../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ChamadaComDeadlineTest.java) |
| Crítico sem reserva sob saturação; encerramento perde trabalho | Unitário | latches, drenagem | [AdmissaoPorPrioridadeTest](../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/AdmissaoPorPrioridadeTest.java), [EncerramentoControladoTest](../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/EncerramentoControladoTest.java) |
| Fluxo reativo sem demanda/backpressure, buffer ilimitado | Reativo | Reactor `StepVerifier` (demanda zero, tempo virtual) | [FluxoSobDemandaTest](../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/FluxoSobDemandaTest.java) |
| Mesma chave processada duas vezes (idempotência) | Determinístico (H2) + integração (Postgres real) | JDBC, Testcontainers PostgreSQL | [ProcessadorIdempotenteTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteTest.java), [ProcessadorIdempotenteExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java) |
| Mensagem veneno em loop; ack antes do efeito (SQS) | Integração com AWS local | `FlociContainer` (`io.floci:testcontainers-floci`) | [ConsumidorSqsLimitadoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java) |
| Rebalance/commit de offset perde ou duplica (Kafka) | Integração | Testcontainers Kafka | [ConsumidorKafkaLimitadoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorKafkaLimitadoExternoIT.java) |
| Quota distribuída inexata; consumidor morto retém pendência (Redis/Valkey) | Integração | Testcontainers Valkey | [LimiteDistribuidoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java), [RecuperacaoPendenciasExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasExternoIT.java) |
| Dependência lenta derruba o serviço; recuperação não verificada | Integração com falha injetada | Toxiproxy (`testcontainers-toxiproxy`) | [ExperimentoCoordenadorLentoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java) |
| Contrato HTTP quebrado (códigos, 429/503, headers) | Contrato de borda | `HttpClient` contra servidor em porta efêmera | [CheckoutApplicationTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java) |
| Domínio dependendo de Spring/JPA/infraestrutura | Arquitetura | ArchUnit (regra de camadas) | [contratos-arquitetura](references/contratos-arquitetura.md) |
| Controller/repository com comportamento Spring específico | Slice Spring | `@WebMvcTest`, `@DataJpaTest` | [testes-slice-spring](references/testes-slice-spring.md) |
| Sobrecarga sem rejeição rápida; pico não recuperado | Carga (laboratório) | gerador de taxa oferecida, perfil `-Pcarga` | [CheckoutSobCargaSimulationCargaIT](../../examples/java/carga/src/test/java/br/com/srportto/exemplos/CheckoutSobCargaSimulationCargaIT.java) |
| Feature de linguagem (sealed/switch) com erro de compilação esperado | Unitário com compilação em memória | `javax.tools` | [PagamentoTest](../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java) |

## Passo a passo

- [ ] Declare o defeito observável que o teste detecta e escolha a linha da tabela.
- [ ] Separe unidade, contrato/arquitetura, integração e carga; declare dependências externas e perfil Maven.
- [ ] Escreva a prova que **falha** pelo comportamento ausente/incorreto; implemente; repita a suíte afetada.
- [ ] Cubra feliz, borda e falha conforme o risco; injete relógio e aleatoriedade; use latch/barreira em concorrência.
- [ ] Código escrito antes do teste: quebre a linha do invariante (mutação) e confirme que o teste falha.
- [ ] Execute o comando e **leia o relatório**; teste pulado não é aprovação.
- [ ] Relate cenário, parâmetros, resultado, limites e pendências.

## Saída

Arquivos de teste, comandos reproduzíveis, contagens de sucesso/falha/skip, ambiente (JDK, Docker, imagens) e o
vínculo requisito → prova. Compare o resultado real com o requisito, não apenas cobertura de linhas.

## Validação

```bash
mvn -f examples/java/pom.xml verify                 # determinísticos, sem Docker
mvn -f examples/java/pom.xml -Pintegracao verify    # + *ExternoIT (Docker obrigatório; falha sem ele)
mvn -f examples/java/pom.xml -Pcarga verify         # + *CargaIT (laboratório; relatório em carga/target)
mvn -f validation/java/pom.xml verify               # catálogo (frontmatter, links, inventário)
```

## Gotchas

- `Thread.sleep` como sincronização torna o teste dependente da máquina; use latch/barreira e condição observável.
- Meça o máximo ativo com `AtomicInteger`; não infira o limite do tamanho do `Semaphore`.
- Libere latches/executors em `finally`, inclusive quando a asserção falha.
- H2 não comprova comportamento PostgreSQL; broker em memória não comprova ack/offset.
- Perfil `integracao` sem Docker deve **falhar**; `disabledWithoutDocker` produz verde enganoso.
- Carga em loop fechado causa coordinated omission; registre taxa oferecida, aceita, rejeitada e percentis.
- Número de laboratório prova invariante, não SLO de produção.
- Não assert por frase exata de documentação nem teste que apenas repete estrutura de texto.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [concorrencia-resiliencia.md](references/concorrencia-resiliencia.md) | Limites, cancelamento, retry, idempotência, Reactor; latch/barreira e relógio injetável |
| [contratos-arquitetura.md](references/contratos-arquitetura.md) | Contrato HTTP, compatibilidade, regra de camadas com ArchUnit, validação do catálogo |
| [integracao-carga.md](references/integracao-carga.md) | Containers, `FlociContainer`, Postgres, Toxiproxy, ensaio de carga |
| [testes-slice-spring.md](references/testes-slice-spring.md) | Escolher entre `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`; Testcontainers; AWS local usa Floci |

## Quem aplica o quê

- **`java-construtor`:** escreve as provas junto do código, escolhendo a linha da tabela.
- **`java-revisor`:** verifica relevância (o teste falharia sem o comportamento?) e lê os resultados reais.
- **`engenheiro-chaos` / `chaos-engineer`:** mede falhas controladas em experimento desenhado.
- **`engenheiro-devops`:** aplica os comandos acima como gates de pipeline.
