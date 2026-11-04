package br.com.srportto.exemplos;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Consumidor Kafka com trabalho em voo limitado, ordem preservada por partição e commit só do concluído.
 *
 * <ul>
 *   <li>O {@link Consumer} é usado apenas pela thread que chama {@link #ciclo(Duration)} (ele não é thread-safe);
 *       workers devolvem resultados por uma fila.</li>
 *   <li>Partição com trabalho pendente fica pausada; o poll continua, mantendo o membro vivo no grupo sem
 *       acumular registros sem limite.</li>
 *   <li>Falha transitória repete a mesma mensagem (a partição não avança); esgotadas as tentativas, a mensagem vai
 *       para a quarentena. Se a quarentena falhar, nada é commitado.</li>
 * </ul>
 */
public final class ConsumidorKafkaLimitado<V> implements AutoCloseable {
    private final Consumer<String, V> consumidor;
    private final ConsumoControlado<ConsumerRecord<String, V>> controle;
    private final int limiteEmVoo;
    private final int maxTentativas;
    private final long pausaEntreTentativasNanos;
    private final LongSupplier relogio;
    private final Duration prazoRevogacao;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final LinkedBlockingQueue<Conclusao<V>> conclusoes = new LinkedBlockingQueue<>();
    private final Map<TopicPartition, Estado<V>> estados = new HashMap<>();
    private final Map<TopicPartition, OffsetAndMetadata> aCommitar = new HashMap<>();
    private int emVoo;

    private static final class Estado<V> {
        final ArrayDeque<ConsumerRecord<String, V>> pendentes = new ArrayDeque<>();
        boolean ocupada;
        ConsumerRecord<String, V> emAndamento;
        int tentativas;
        long proximaTentativa;
        Exception ultimoErro;
    }

    private record Conclusao<V>(TopicPartition particao, ConsumerRecord<String, V> registro,
                                ConsumoControlado.Resultado resultado) {}

    public ConsumidorKafkaLimitado(Consumer<String, V> consumidor, ConsumoControlado<ConsumerRecord<String, V>> controle,
                                   int limiteEmVoo, int maxTentativas, Duration pausaEntreTentativas, LongSupplier relogio) {
        this(consumidor, controle, limiteEmVoo, maxTentativas, pausaEntreTentativas, relogio, Duration.ofSeconds(10));
    }

    /** prazoRevogacao: espera máxima pelo trabalho em andamento quando a partição é revogada (menor que o timeout do rebalance). */
    public ConsumidorKafkaLimitado(Consumer<String, V> consumidor, ConsumoControlado<ConsumerRecord<String, V>> controle,
                                   int limiteEmVoo, int maxTentativas, Duration pausaEntreTentativas, LongSupplier relogio,
                                   Duration prazoRevogacao) {
        if (limiteEmVoo <= 0 || maxTentativas <= 0) throw new IllegalArgumentException("Limites devem ser positivos");
        this.prazoRevogacao = Objects.requireNonNull(prazoRevogacao);
        this.consumidor = Objects.requireNonNull(consumidor);
        this.controle = Objects.requireNonNull(controle);
        this.limiteEmVoo = limiteEmVoo;
        this.maxTentativas = maxTentativas;
        this.pausaEntreTentativasNanos = pausaEntreTentativas.toNanos();
        this.relogio = Objects.requireNonNull(relogio);
    }

    public void assinar(String topico) {
        consumidor.subscribe(List.of(topico), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> particoes) { aoRevogar(particoes); }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> particoes) { }

            @Override
            public void onPartitionsLost(Collection<TopicPartition> particoes) {
                // Partição perdida não pode ser commitada por este membro: o novo dono reprocessa (idempotência).
                particoes.forEach(estados::remove);
            }
        });
    }

    /** Um ciclo do laço de consumo; chame repetidamente na mesma thread. */
    public void ciclo(Duration timeoutPoll) {
        drenarConclusoes();
        commitar();
        ajustarPausas();
        for (ConsumerRecord<String, V> registro : consumidor.poll(timeoutPoll)) {
            var particao = new TopicPartition(registro.topic(), registro.partition());
            estados.computeIfAbsent(particao, p -> new Estado<>()).pendentes.addLast(registro);
        }
        despachar();
        ajustarPausas();
    }

    private void drenarConclusoes() {
        Conclusao<V> conclusao;
        while ((conclusao = conclusoes.poll()) != null) aplicar(conclusao);
    }

    private void aplicar(Conclusao<V> conclusao) {
        emVoo--;
        Estado<V> estado = estados.get(conclusao.particao());
        // Conclusão de partição já revogada — ou de uma entrega anterior à reatribuição da partição a este membro —
        // é descartada: o offset não é commitado aqui e o registro atual da partição segue seu próprio fluxo.
        if (estado == null || estado.emAndamento != conclusao.registro()) return;
        estado.ocupada = false;
        estado.emAndamento = null;
        if (conclusao.resultado().decisao() == ConsumoControlado.Decisao.CONFIRMAR) {
            estado.pendentes.pollFirst();
            estado.tentativas = 0;
            estado.ultimoErro = null;
            aCommitar.put(conclusao.particao(), new OffsetAndMetadata(conclusao.registro().offset() + 1));
        } else {
            estado.tentativas++;
            estado.ultimoErro = conclusao.resultado().erro();
            estado.proximaTentativa = relogio.getAsLong() + pausaEntreTentativasNanos;
        }
    }

    private void commitar() {
        if (aCommitar.isEmpty()) return;
        consumidor.commitSync(Map.copyOf(aCommitar));
        aCommitar.clear();
    }

    private void despachar() {
        long agora = relogio.getAsLong();
        for (var entrada : estados.entrySet()) {
            if (emVoo >= limiteEmVoo) return;
            Estado<V> estado = entrada.getValue();
            if (estado.ocupada || estado.pendentes.isEmpty() || agora < estado.proximaTentativa) continue;
            ConsumerRecord<String, V> registro = estado.pendentes.peekFirst();
            boolean quarentenar = estado.tentativas >= maxTentativas;
            Exception causa = estado.ultimoErro;
            TopicPartition particao = entrada.getKey();
            estado.ocupada = true;
            estado.emAndamento = registro;
            emVoo++;
            workers.execute(() -> conclusoes.add(new Conclusao<>(particao, registro,
                    quarentenar ? controle.quarentenar(registro, causa) : controle.consumir(registro))));
        }
    }

    private void ajustarPausas() {
        Set<TopicPartition> atribuidas = consumidor.assignment();
        var pausar = new HashSet<TopicPartition>();
        var retomar = new HashSet<TopicPartition>();
        for (TopicPartition particao : atribuidas) {
            Estado<V> estado = estados.get(particao);
            boolean comTrabalho = estado != null && (estado.ocupada || !estado.pendentes.isEmpty());
            (comTrabalho ? pausar : retomar).add(particao);
        }
        consumidor.pause(pausar);
        retomar.retainAll(consumidor.paused());
        consumidor.resume(retomar);
    }

    /** Antes de perder a partição: espera (limitado) o trabalho em andamento e commita o que concluiu. */
    private void aoRevogar(Collection<TopicPartition> particoes) {
        long limite = System.nanoTime() + prazoRevogacao.toNanos();
        try {
            while (particoes.stream().map(estados::get).anyMatch(e -> e != null && e.ocupada)) {
                long restante = limite - System.nanoTime();
                if (restante <= 0) break;
                Conclusao<V> conclusao = conclusoes.poll(restante, TimeUnit.NANOSECONDS);
                if (conclusao != null) aplicar(conclusao);
            }
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
        }
        var revogados = new HashMap<TopicPartition, OffsetAndMetadata>();
        for (TopicPartition particao : particoes) {
            OffsetAndMetadata offset = aCommitar.remove(particao);
            if (offset != null) revogados.put(particao, offset);
            estados.remove(particao);
        }
        if (!revogados.isEmpty()) consumidor.commitSync(revogados);
    }

    public int emVoo() { return emVoo; }

    @Override
    public void close() throws InterruptedException {
        workers.shutdown();
        if (!workers.awaitTermination(10, TimeUnit.SECONDS)) workers.shutdownNow();
        drenarConclusoes();
        commitar();
    }
}
