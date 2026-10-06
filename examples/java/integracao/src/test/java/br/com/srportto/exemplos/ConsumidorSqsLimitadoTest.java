package br.com.srportto.exemplos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConsumidorSqsLimitadoTest {
    /** Fila SQS em memória: visibilidade, receipt handle por entrega e contagem de recebimentos. */
    static final class FilaFalsa implements SqsClient {
        static final class Mensagem {
            final String corpo;
            long visivelEm;
            String recibo;
            int recebimentos;
            Mensagem(String corpo) { this.corpo = corpo; }
        }

        final List<Mensagem> mensagens = new ArrayList<>();
        final List<Integer> maximosPedidos = new CopyOnWriteArrayList<>();
        final List<Integer> extensoes = new CopyOnWriteArrayList<>();
        final List<String> apagadas = new CopyOnWriteArrayList<>();

        synchronized void enviar(String corpo) { mensagens.add(new Mensagem(corpo)); }

        @Override
        public synchronized ReceiveMessageResponse receiveMessage(ReceiveMessageRequest pedido) {
            // Mesma validação do serviço real: MaxNumberOfMessages entre 1 e 10.
            if (pedido.maxNumberOfMessages() < 1 || pedido.maxNumberOfMessages() > 10) {
                throw new IllegalArgumentException("MaxNumberOfMessages fora de 1..10: " + pedido.maxNumberOfMessages());
            }
            maximosPedidos.add(pedido.maxNumberOfMessages());
            long agora = System.nanoTime();
            var entregues = new ArrayList<Message>();
            for (Mensagem m : mensagens) {
                if (entregues.size() == pedido.maxNumberOfMessages()) break;
                if (m.visivelEm > agora) continue;
                m.recibo = UUID.randomUUID().toString();
                m.recebimentos++;
                m.visivelEm = agora + TimeUnit.SECONDS.toNanos(pedido.visibilityTimeout());
                entregues.add(Message.builder().body(m.corpo).receiptHandle(m.recibo).messageId(m.corpo).build());
            }
            return ReceiveMessageResponse.builder().messages(entregues).build();
        }

        @Override
        public synchronized DeleteMessageResponse deleteMessage(DeleteMessageRequest pedido) {
            mensagens.removeIf(m -> {
                boolean alvo = pedido.receiptHandle().equals(m.recibo);
                if (alvo) apagadas.add(m.corpo);
                return alvo;
            });
            return DeleteMessageResponse.builder().build();
        }

        @Override
        public synchronized ChangeMessageVisibilityResponse changeMessageVisibility(ChangeMessageVisibilityRequest pedido) {
            extensoes.add(pedido.visibilityTimeout());
            for (Mensagem m : mensagens) {
                if (pedido.receiptHandle().equals(m.recibo)) {
                    m.visivelEm = System.nanoTime() + TimeUnit.SECONDS.toNanos(pedido.visibilityTimeout());
                }
            }
            return ChangeMessageVisibilityResponse.builder().build();
        }

        synchronized int recebimentos(String corpo) {
            return mensagens.stream().filter(m -> m.corpo.equals(corpo)).mapToInt(m -> m.recebimentos).sum();
        }

        @Override public String serviceName() { return "sqs-falso"; }
        @Override public void close() { }
    }

    private final FilaFalsa fila = new FilaFalsa();
    private ConsumidorSqsLimitado consumidor;

    private void iniciar(ConsumoControlado.Efeito<Message> efeito, int limite, Duration visibilidade) {
        var controle = new ConsumoControlado<Message>(efeito, erro -> false, (m, causa) -> fail("sem quarentena"));
        consumidor = new ConsumidorSqsLimitado(fila, "url-fila", controle, limite, visibilidade, Duration.ZERO);
    }

    private void cicloAte(BooleanSupplier condicao) {
        long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) fail("Condição não atingida em 5 s");
            consumidor.ciclo();
            Thread.onSpinWait();
        }
    }

    @AfterEach
    void fechar() throws Exception {
        if (consumidor != null) consumidor.close();
    }

    @Test
    void deveLimitarMensagensEmVooEApagarSoDepoisDoEfeito() throws Exception {
        var liberar = new CountDownLatch(1);
        var ativas = new AtomicInteger();
        var maximo = new AtomicInteger();
        iniciar(m -> {
            maximo.accumulateAndGet(ativas.incrementAndGet(), Math::max);
            try {
                liberar.await(5, TimeUnit.SECONDS);
            } finally {
                ativas.decrementAndGet();
            }
        }, 2, Duration.ofSeconds(30));
        for (int i = 0; i < 5; i++) fila.enviar("m" + i);

        cicloAte(() -> consumidor.emVoo() == 2);
        for (int i = 0; i < 5; i++) consumidor.ciclo();
        // Nunca pede mais mensagens do que a capacidade livre; nada é apagado antes do efeito.
        assertTrue(fila.maximosPedidos.stream().allMatch(n -> n <= 2), fila.maximosPedidos::toString);
        assertTrue(fila.apagadas.isEmpty());

        liberar.countDown();
        cicloAte(() -> fila.apagadas.size() == 5);
        assertEquals(2, maximo.get());
    }

    @Test
    void falhaTransitoriaNaoDeveApagarEDeveVoltarAFicarVisivel() {
        var tentativas = new AtomicInteger();
        iniciar(m -> {
            if (tentativas.incrementAndGet() < 3) throw new IllegalStateException("dependência fora");
        }, 1, Duration.ofSeconds(30));
        fila.enviar("m1");

        cicloAte(() -> fila.apagadas.contains("m1"));
        assertEquals(3, tentativas.get());
        // Cada reentrega torna a mensagem visível de novo (timeout 0 neste teste), sem esperar os 30 s.
        assertTrue(fila.extensoes.contains(0), fila.extensoes::toString);
    }

    @Test
    void processamentoLongoDeveEstenderAVisibilidadeEEvitarEntregaDuplicada() {
        var processamentos = Collections.synchronizedList(new ArrayList<String>());
        // Processamento de 1,5 s com visibilidade de 1 s: sem renovação, a mensagem reapareceria no meio.
        iniciar(m -> {
            processamentos.add(m.body());
            Thread.sleep(1_500);
        }, 2, Duration.ofSeconds(1));
        fila.enviar("longa");

        cicloAte(() -> fila.apagadas.contains("longa"));
        assertTrue(fila.extensoes.contains(1), fila.extensoes::toString);
        // Capacidade livre existia (limite 2), então só a renovação explica a ausência de reentrega.
        assertEquals(List.of("longa"), processamentos);
    }
}
