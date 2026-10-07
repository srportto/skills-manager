package br.com.srportto.exemplos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQS real (LocalStack): mensagem processada é apagada; mensagem que sempre falha vai para a DLQ pela
 * RedrivePolicy (maxReceiveCount=3), sem descarte silencioso e sem loop infinito.
 */
class ConsumidorSqsLimitadoExternoIT {
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(ServicosExternos.LOCALSTACK).withServices("sqs");
    static SqsClient sqs;
    static String fila;
    static String dlq;

    @BeforeAll
    static void iniciar() {
        LOCALSTACK.start();
        sqs = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();
        // Regra do catálogo: a fila nasce com DLQ e RedrivePolicy.
        dlq = sqs.createQueue(r -> r.queueName("pedidos-dlq")).queueUrl();
        String dlqArn = sqs.getQueueAttributes(r -> r.queueUrl(dlq).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        fila = sqs.createQueue(r -> r.queueName("pedidos").attributes(Map.of(QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}"))).queueUrl();
    }

    @AfterAll
    static void parar() {
        if (sqs != null) sqs.close();
        LOCALSTACK.stop();
    }

    @DisplayName("ConsumidorSqsLimitadoExterno: Mensagem que sempre falha deve ir para dlq e as demais sao apagadas")
    @Test
    void mensagemQueSempreFalhaDeveIrParaDlqEAsDemaisSaoApagadas() throws Exception {
        sqs.sendMessage(r -> r.queueUrl(fila).messageBody("ok-1"));
        sqs.sendMessage(r -> r.queueUrl(fila).messageBody("veneno"));
        sqs.sendMessage(r -> r.queueUrl(fila).messageBody("ok-2"));
        var tentativasVeneno = new AtomicInteger();
        var processadas = new AtomicInteger();
        var controle = new ConsumoControlado<Message>(m -> {
            if (m.body().equals("veneno")) {
                tentativasVeneno.incrementAndGet();
                throw new IllegalStateException("falha transitória persistente");
            }
            processadas.incrementAndGet();
        }, erro -> false, (m, causa) -> { throw new IllegalStateException("não usado: DLQ via RedrivePolicy"); });

        try (var consumidor = new ConsumidorSqsLimitado(sqs, fila, controle, 2, Duration.ofSeconds(5), Duration.ofSeconds(1))) {
            long limite = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (System.nanoTime() < limite && (processadas.get() < 2 || mensagensNaDlq() == 0)) consumidor.ciclo();
        }

        assertEquals(2, processadas.get());
        assertEquals(1, mensagensNaDlq());
        assertEquals(3, tentativasVeneno.get());
        assertTrue(sqs.receiveMessage(r -> r.queueUrl(fila).waitTimeSeconds(1)).messages().isEmpty());
    }

    static int mensagensNaDlq() {
        return Integer.parseInt(sqs.getQueueAttributes(r -> r.queueUrl(dlq)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }
}
