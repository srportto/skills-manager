package br.com.srportto.exemplos;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConsumidorKafkaLimitadoTest {
    private static final String TOPICO = "pedidos";
    private static final TopicPartition P0 = new TopicPartition(TOPICO, 0);
    private static final TopicPartition P1 = new TopicPartition(TOPICO, 1);

    private final MockConsumer<String, String> kafka = new MockConsumer<>("earliest");
    private final List<String> quarentenadas = Collections.synchronizedList(new ArrayList<>());
    private ConsumidorKafkaLimitado<String> consumidor;

    private void iniciar(ConsumoControlado.Efeito<ConsumerRecord<String, String>> efeito, int limiteEmVoo,
                         BooleanSupplier quarentenaDisponivel) {
        var controle = new ConsumoControlado<ConsumerRecord<String, String>>(efeito,
                erro -> erro instanceof IllegalArgumentException, (registro, causa) -> {
            if (!quarentenaDisponivel.getAsBoolean()) throw new IllegalStateException("DLT indisponível");
            quarentenadas.add(registro.value());
        });
        consumidor = new ConsumidorKafkaLimitado<>(kafka, controle, limiteEmVoo, 3, Duration.ZERO, System::nanoTime);
        consumidor.assinar(TOPICO);
        kafka.rebalance(List.of(P0, P1));
        kafka.updateBeginningOffsets(Map.of(P0, 0L, P1, 0L));
    }

    private void registro(TopicPartition particao, long offset, String valor) {
        kafka.addRecord(new ConsumerRecord<>(TOPICO, particao.partition(), offset, "chave-" + particao.partition(), valor));
    }

    private Long confirmado(TopicPartition particao) {
        OffsetAndMetadata offset = kafka.committed(Set.of(particao)).get(particao);
        return offset == null ? null : offset.offset();
    }

    private void cicloAte(BooleanSupplier condicao) {
        long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) fail("Condição não atingida em 5 s");
            consumidor.ciclo(Duration.ofMillis(10));
        }
    }

    @AfterEach
    void fechar() throws Exception {
        if (consumidor != null) consumidor.close();
    }

    @DisplayName("ConsumidorKafkaLimitado: Deve limitar trabalho em voo pausar particoes ocupadas e commitar so o concluido")
    @Test
    void deveLimitarTrabalhoEmVooPausarParticoesOcupadasECommitarSoOConcluido() throws Exception {
        var liberar = new CountDownLatch(1);
        var ativas = new AtomicInteger();
        var maximo = new AtomicInteger();
        var processadosP0 = Collections.synchronizedList(new ArrayList<String>());
        iniciar(registro -> {
            maximo.accumulateAndGet(ativas.incrementAndGet(), Math::max);
            try {
                if (registro.value().equals("p0-1")) liberar.await(5, TimeUnit.SECONDS);
                if (registro.partition() == 0) processadosP0.add(registro.value());
            } finally {
                ativas.decrementAndGet();
            }
        }, 1, () -> true);
        registro(P0, 0, "p0-0");
        registro(P0, 1, "p0-1");
        registro(P1, 0, "p1-0");

        // p0-0 conclui e é confirmado; p0-1 fica preso: o offset 2 não pode ser commitado.
        cicloAte(() -> Long.valueOf(1).equals(confirmado(P0)));
        for (int i = 0; i < 5; i++) consumidor.ciclo(Duration.ofMillis(10));
        assertEquals(1L, confirmado(P0));
        assertEquals(1, consumidor.emVoo());
        // Partições com trabalho pendente ficam pausadas, mas o poll continua (membro do grupo segue vivo).
        assertTrue(kafka.paused().containsAll(Set.of(P0, P1)), kafka.paused()::toString);
        assertNull(confirmado(P1));

        liberar.countDown();
        cicloAte(() -> Long.valueOf(2).equals(confirmado(P0)) && Long.valueOf(1).equals(confirmado(P1)));
        assertEquals(1, maximo.get());
        assertEquals(List.of("p0-0", "p0-1"), processadosP0);
        cicloAte(() -> kafka.paused().isEmpty());
    }

    @DisplayName("ConsumidorKafkaLimitado: Falha transitoria deve repetir a mesma mensagem em ordem e depois ir para quarentena")
    @Test
    void falhaTransitoriaDeveRepetirAMesmaMensagemEmOrdemEDepoisIrParaQuarentena() {
        var tentativasRuim = new AtomicInteger();
        var ordem = Collections.synchronizedList(new ArrayList<String>());
        iniciar(registro -> {
            if (registro.value().equals("ruim")) {
                tentativasRuim.incrementAndGet();
                throw new IllegalStateException("dependência fora");
            }
            ordem.add(registro.value());
        }, 2, () -> true);
        registro(P0, 0, "ruim");
        registro(P0, 1, "bom");

        cicloAte(() -> Long.valueOf(2).equals(confirmado(P0)));
        assertEquals(3, tentativasRuim.get());
        assertEquals(List.of("ruim"), quarentenadas);
        // A mensagem seguinte da partição só roda depois de resolvida a anterior (ordem preservada).
        assertEquals(List.of("bom"), ordem);
    }

    @DisplayName("ConsumidorKafkaLimitado: Conclusao atrasada depois da revogacao nao deve pular mensagem quando a particao volta")
    @Test
    void conclusaoAtrasadaDepoisDaRevogacaoNaoDevePularMensagemQuandoAParticaoVolta() throws Exception {
        var liberar = new CountDownLatch(1);
        var processados = Collections.synchronizedList(new ArrayList<String>());
        var controle = new ConsumoControlado<ConsumerRecord<String, String>>(registro -> {
            if (registro.value().equals("p0-0")) liberar.await(5, TimeUnit.SECONDS);
            processados.add(registro.value());
        }, erro -> false, (registro, causa) -> fail("sem quarentena"));
        // Revogação espera no máximo 50 ms pelo trabalho em andamento.
        consumidor = new ConsumidorKafkaLimitado<>(kafka, controle, 1, 3, Duration.ZERO, System::nanoTime, Duration.ofMillis(50));
        consumidor.assinar(TOPICO);
        kafka.rebalance(List.of(P0, P1));
        kafka.updateBeginningOffsets(Map.of(P0, 0L, P1, 0L));
        registro(P0, 0, "p0-0");
        cicloAte(() -> consumidor.emVoo() == 1);

        // P0 é revogada com p0-0 ainda em andamento (nada commitado) e volta para este mesmo membro.
        kafka.rebalance(List.of(P1));
        kafka.rebalance(List.of(P0, P1));
        // O novo dono relê a partição desde o último commit (nenhum): p0-0 é entregue de novo, seguido de p0-1.
        registro(P0, 0, "p0-0-reentrega");
        registro(P0, 1, "p0-1");
        // Os registros reentregues chegam (estado novo da partição) enquanto a entrega antiga ainda roda.
        cicloAte(() -> kafka.position(P0) == 2);
        liberar.countDown();

        cicloAte(() -> Long.valueOf(2).equals(confirmado(P0)));
        // A conclusão atrasada da entrega antiga não pode "consumir" a reentrega sem processá-la.
        assertTrue(processados.containsAll(List.of("p0-0-reentrega", "p0-1")), processados::toString);
    }

    @DisplayName("ConsumidorKafkaLimitado: Quarentena indisponivel nao deve commitar nem perder a mensagem")
    @Test
    void quarentenaIndisponivelNaoDeveCommitarNemPerderAMensagem() throws Exception {
        var dltDisponivel = new AtomicBoolean(false);
        iniciar(registro -> { throw new IllegalArgumentException("payload inválido"); }, 1, dltDisponivel::get);
        registro(P0, 0, "invalida");

        for (int i = 0; i < 20; i++) consumidor.ciclo(Duration.ofMillis(5));
        assertNull(confirmado(P0));
        assertTrue(quarentenadas.isEmpty());

        dltDisponivel.set(true);
        cicloAte(() -> Long.valueOf(1).equals(confirmado(P0)));
        assertEquals(List.of("invalida"), quarentenadas);
    }
}
