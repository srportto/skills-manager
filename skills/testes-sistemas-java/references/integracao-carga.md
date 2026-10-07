# Integração, falhas e carga

Leia este arquivo quando precisar de testes com serviços reais (Postgres, Kafka, Floci/AWS, Toxiproxy) ou de ensaio de carga com invariantes.

Duas trilhas: testes locais determinísticos com dependências embutidas e integração com serviços reais efêmeros. H2 ajuda a exercitar transações, mas não comprova todos os comportamentos PostgreSQL. Broker em memória não comprova ack/offset; execute cliente real contra container.

Perfil integracao falha se Docker não estiver disponível; não usar disabledWithoutDocker para produzir verde enganoso. Declare imagens fixas, timeout de startup e limpeza automática. Injeção de falha pode usar Toxiproxy via API Java/Testcontainers ou um servidor HTTP Java que controla resposta/lentidão. Serviços AWS (SQS, S3...) rodam no Floci via `FlociContainer`.

Carga em Java deve limitar o próprio gerador: taxa oferecida, fila de agendamento, número de tarefas e tempo total. Registre carga oferecida, aceita, rejeitada, concluída, percentis, máximo ativo e recuperação. Separar fila do gerador e fila do alvo; evitar coordinated omission ao concluir capacidade com clientes de loop fechado.

Ensaio sintético determinístico prova invariantes; ensaio aberto contra HTTP real mede comportamento no ambiente. Nenhum deles certifica SLO de produção sem distribuição de carga representativa.

Perfis e comandos: [examples/java/README.md](../../../examples/java/README.md). Reporte hardware/JDK, parâmetros, limitações, falhas e testes não executados.


## Exemplos em Java 25

### AWS local com `FlociContainer` (SQS + DLQ)

Dependência: `io.floci:testcontainers-floci` (escopo `test`). A imagem é fixada numa constante e o teste **falha**
sem Docker (nada de `@EnabledIf`/`disabledWithoutDocker` produzindo verde enganoso).

```java
// ERRADO: mock do SqsClient - não prova RedrivePolicy, visibility timeout nem entrega real
var sqs = mock(SqsClient.class);

// CERTO: SQS de verdade no Floci; a fila nasce com DLQ + RedrivePolicy (regra do catálogo)
class MensagemVenenoExternoIT {
    static final FlociContainer FLOCI = new FlociContainer(DockerImageName.parse("floci/floci:2.2.0"));
    static SqsClient sqs;

    @BeforeAll
    static void iniciar() {
        FLOCI.start();
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(FLOCI.getEndpoint()))
                .region(Region.of(FLOCI.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
                .build();
    }

    @AfterAll
    static void parar() {
        if (sqs != null) sqs.close();
        FLOCI.stop();                       // limpeza garantida, mesmo com asserção falha
    }
}
```

Fonte completa (DLQ, `maxReceiveCount=3`, mensagem que sempre falha chega à DLQ):
[ConsumidorSqsLimitadoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ConsumidorSqsLimitadoExternoIT.java);
imagens fixas em [ServicosExternos](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ServicosExternos.java).
Convenção "AWS local" em `docs/catalogo/convencoes.md`.

### PostgreSQL real em vez de H2 para comportamento de transação

```java

class IdempotenciaPostgresExternoIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(ServicosExternos.POSTGRES); // start() em @BeforeAll, stop() em @AfterAll

    @Test
    void dezesseisConexoesComAMesmaChaveGeramUmUnicoPedido() throws Exception {
        var dataSource = ServicosExternos.dataSource(POSTGRES);
        // ... 16 threads disputam a mesma chave; asserte COUNT(*) == 1 no banco real
    }
}
```

Fonte: [ProcessadorIdempotenteExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java)
(contra o teste determinístico H2 [ProcessadorIdempotenteTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteTest.java)).

### Falha injetada: Toxiproxy

Latência e corte de conexão entre aplicação e dependência, com recuperação verificada:
[ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java).
Desenho de experimento e blast radius: `chaos-engineer`.

### Carga: gerador limitado e invariantes

```java
// ERRADO: loop fechado que só dispara a próxima requisição quando a anterior termina
// (coordinated omission: o alvo lento "esconde" a carga que deveria ter recebido)
for (int i = 0; i < 10_000; i++) cliente.send(requisicao, BodyHandlers.discarding());

// CERTO: taxa OFERECIDA por fase (req/s) com gerador limitado; registra oferecida, aceita,
// rejeitada, concluída, percentis, máximo ativo e a recuperação na fase seguinte
var simulacao = new CheckoutSobCargaSimulation(URI.create("http://localhost:" + porta + "/pedidos"), 300);
var resultados = simulacao.executar(List.of(
        new Fase("baseline", 50, Duration.ofSeconds(2)),
        new Fase("pico-acima-da-capacidade", 1_000, Duration.ofSeconds(3)),
        new Fase("retorno", 50, Duration.ofSeconds(2))));
```

Fonte: [CheckoutSobCargaSimulationCargaIT](../../../examples/java/carga/src/test/java/br/com/srportto/exemplos/CheckoutSobCargaSimulationCargaIT.java)
(perfil `-Pcarga`). Os números são de laboratório: provam invariantes, não SLO de produção.
