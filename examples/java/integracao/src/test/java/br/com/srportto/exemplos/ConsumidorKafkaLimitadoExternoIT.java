package br.com.srportto.exemplos;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka e PostgreSQL reais: dois consumidores do mesmo grupo, rebalance no meio, falha simulada depois do efeito
 * e antes do commit, e reinício. Prova: nenhum evento perdido e nenhum efeito duplicado.
 */
class ConsumidorKafkaLimitadoExternoIT {
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(ServicosExternos.KAFKA);
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(ServicosExternos.POSTGRES);
    static final String TOPICO = "pedidos-criados";
    static final int EVENTOS = 200;
    static DataSource banco;

    @BeforeAll
    static void iniciar() throws Exception {
        KAFKA.start();
        POSTGRES.start();
        banco = ServicosExternos.dataSource(POSTGRES);
        new ProcessadorIdempotente(banco).preparar();
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPICO, 4, (short) 1))).all().get();
        }
        try (var produtor = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            for (int i = 0; i < EVENTOS; i++) produtor.send(new ProducerRecord<>(TOPICO, "evento-" + i, String.valueOf(100 + i)));
            produtor.flush();
        }
    }

    @AfterAll
    static void parar() {
        KAFKA.stop();
        POSTGRES.stop();
    }

    static KafkaConsumer<String, String> kafka() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "expedicao",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 20,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    /** Efeito idempotente por eventId; algumas mensagens falham DEPOIS do efeito e ANTES da confirmação. */
    static ConsumoControlado<ConsumerRecord<String, String>> controle(Set<String> falharUmaVez, AtomicInteger efeitos) {
        var processador = new ProcessadorIdempotente(banco);
        return new ConsumoControlado<>(registro -> {
            processador.processar("kafka", registro.key(), Long.parseLong(registro.value()));
            efeitos.incrementAndGet();
            if (falharUmaVez.remove(registro.key())) throw new IllegalStateException("queda antes do ack");
        }, erro -> false, (registro, causa) -> { throw new IllegalStateException("sem DLT neste teste", causa); });
    }

    static Thread laco(ConsumidorKafkaLimitado<String> consumidor, KafkaConsumer<String, String> kafka, AtomicBoolean ativo) {
        return Thread.ofPlatform().start(() -> {
            try (kafka; consumidor) {
                while (ativo.get()) consumidor.ciclo(Duration.ofMillis(100));
            } catch (InterruptedException erro) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @DisplayName("ConsumidorKafkaLimitadoExterno: Dois consumidores com rebalance e falha antes do ack nao perdem nem duplicam efeitos")
    @Test
    void doisConsumidoresComRebalanceEFalhaAntesDoAckNaoPerdemNemDuplicamEfeitos() throws Exception {
        var falharUmaVez = ConcurrentHashMap.<String>newKeySet();
        for (int i = 0; i < EVENTOS; i += 25) falharUmaVez.add("evento-" + i);
        var efeitos = new AtomicInteger();

        var ativoA = new AtomicBoolean(true);
        var kafkaA = kafka();
        var consumidorA = new ConsumidorKafkaLimitado<>(kafkaA, controle(falharUmaVez, efeitos), 4, 5, Duration.ofMillis(50), System::nanoTime);
        consumidorA.assinar(TOPICO);
        Thread lacoA = laco(consumidorA, kafkaA, ativoA);

        aguardar(() -> pedidos() >= EVENTOS / 4);
        // Segundo membro entra no grupo no meio do consumo: rebalance com trabalho em andamento.
        var ativoB = new AtomicBoolean(true);
        var kafkaB = kafka();
        var consumidorB = new ConsumidorKafkaLimitado<>(kafkaB, controle(falharUmaVez, efeitos), 4, 5, Duration.ofMillis(50), System::nanoTime);
        consumidorB.assinar(TOPICO);
        Thread lacoB = laco(consumidorB, kafkaB, ativoB);

        aguardar(() -> pedidos() == EVENTOS && falharUmaVez.isEmpty());
        ativoA.set(false);
        ativoB.set(false);
        lacoA.join(Duration.ofSeconds(30));
        lacoB.join(Duration.ofSeconds(30));

        assertEquals(EVENTOS, pedidos());
        // Houve reprocessamento (falhas antes do ack), mas o efeito de negócio continua único.
        assertTrue(efeitos.get() > EVENTOS, "efeitos aplicados: " + efeitos.get());

        // Reinício: um novo membro do grupo não reprocessa o que foi commitado.
        var reprocessados = new AtomicInteger();
        var kafkaC = kafka();
        // O consumidor limitado fecha antes do KafkaConsumer (ordem inversa da declaração).
        try (kafkaC; var consumidorC = new ConsumidorKafkaLimitado<>(kafkaC, new ConsumoControlado<ConsumerRecord<String, String>>(
                r -> reprocessados.incrementAndGet(), erro -> false, (r, c) -> {}), 4, 3, Duration.ZERO, System::nanoTime)) {
            consumidorC.assinar(TOPICO);
            long fim = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (System.nanoTime() < fim) consumidorC.ciclo(Duration.ofMillis(200));
        }
        assertEquals(0, reprocessados.get());
    }

    static long pedidos() {
        try {
            return new ProcessadorIdempotente(banco).quantidadePedidos();
        } catch (SQLException erro) {
            throw new IllegalStateException(erro);
        }
    }

    static void aguardar(java.util.function.BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) throw new AssertionError("Condição não atingida em 90 s; pedidos=" + pedidos());
            Thread.sleep(100);
        }
    }
}
