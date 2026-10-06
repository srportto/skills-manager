package br.com.srportto.exemplos;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Consumidor SQS com mensagens em voo limitadas, renovação de visibilidade durante processamento longo e
 * remoção ({@code DeleteMessage}) somente após a decisão CONFIRMAR. Mensagem que falha volta a ficar
 * visível após um atraso; o esgotamento de tentativas é tratado pela {@code RedrivePolicy} da fila (DLQ).
 */
public final class ConsumidorSqsLimitado implements AutoCloseable {
    private final SqsClient sqs;
    private final String filaUrl;
    private final ConsumoControlado<Message> controle;
    private final int limiteEmVoo;
    private final int visibilidadeSegundos;
    private final int esperaLongPollSegundos;
    private final IntFunction<Duration> atrasoReentrega;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService renovador = Executors.newSingleThreadScheduledExecutor();
    private final LinkedBlockingQueue<Conclusao> conclusoes = new LinkedBlockingQueue<>();
    private int emVoo;

    private record Conclusao(Message mensagem, ConsumoControlado.Resultado resultado) {}

    /** Reentrega imediata (atraso zero); prefira o construtor com atraso por número de recebimentos. */
    public ConsumidorSqsLimitado(SqsClient sqs, String filaUrl, ConsumoControlado<Message> controle,
                                 int limiteEmVoo, Duration visibilidade, Duration esperaLongPoll) {
        this(sqs, filaUrl, controle, limiteEmVoo, visibilidade, esperaLongPoll, recebimentos -> Duration.ZERO);
    }

    public ConsumidorSqsLimitado(SqsClient sqs, String filaUrl, ConsumoControlado<Message> controle,
                                 int limiteEmVoo, Duration visibilidade, Duration esperaLongPoll,
                                 IntFunction<Duration> atrasoReentrega) {
        if (limiteEmVoo <= 0 || visibilidade.toSeconds() < 1) throw new IllegalArgumentException("Limites inválidos");
        this.sqs = Objects.requireNonNull(sqs);
        this.filaUrl = Objects.requireNonNull(filaUrl);
        this.controle = Objects.requireNonNull(controle);
        this.limiteEmVoo = limiteEmVoo;
        this.visibilidadeSegundos = Math.toIntExact(visibilidade.toSeconds());
        this.esperaLongPollSegundos = Math.toIntExact(esperaLongPoll.toSeconds());
        this.atrasoReentrega = Objects.requireNonNull(atrasoReentrega);
    }

    public void ciclo() {
        Conclusao conclusao;
        while ((conclusao = conclusoes.poll()) != null) aplicar(conclusao);
        int livre = limiteEmVoo - emVoo;
        // Só pede o que cabe: o broker guarda o backlog, a JVM não.
        if (livre <= 0) return;
        var recebidas = sqs.receiveMessage(r -> r.queueUrl(filaUrl)
                .maxNumberOfMessages(Math.min(10, livre))
                .visibilityTimeout(visibilidadeSegundos)
                .waitTimeSeconds(esperaLongPollSegundos)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)).messages();
        for (Message mensagem : recebidas) {
            emVoo++;
            workers.execute(() -> processar(mensagem));
        }
    }

    private void processar(Message mensagem) {
        long periodoMs = visibilidadeSegundos * 1000L / 2;
        // Renova antes de expirar; para quando o processamento termina (com sucesso ou falha).
        ScheduledFuture<?> renovacao = renovador.scheduleAtFixedRate(() -> sqs.changeMessageVisibility(r -> r
                .queueUrl(filaUrl).receiptHandle(mensagem.receiptHandle()).visibilityTimeout(visibilidadeSegundos)),
                periodoMs, periodoMs, TimeUnit.MILLISECONDS);
        try {
            conclusoes.add(new Conclusao(mensagem, controle.consumir(mensagem)));
        } finally {
            renovacao.cancel(false);
        }
    }

    private void aplicar(Conclusao conclusao) {
        emVoo--;
        String recibo = conclusao.mensagem().receiptHandle();
        if (conclusao.resultado().decisao() == ConsumoControlado.Decisao.CONFIRMAR) {
            sqs.deleteMessage(r -> r.queueUrl(filaUrl).receiptHandle(recibo));
            return;
        }
        int recebimentos = Integer.parseInt(conclusao.mensagem().attributesAsStrings()
                .getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT.toString(), "1"));
        int atraso = Math.toIntExact(atrasoReentrega.apply(recebimentos).toSeconds());
        sqs.changeMessageVisibility(r -> r.queueUrl(filaUrl).receiptHandle(recibo).visibilityTimeout(atraso));
    }

    public int emVoo() { return emVoo; }

    @Override
    public void close() throws InterruptedException {
        workers.shutdown();
        if (!workers.awaitTermination(10, TimeUnit.SECONDS)) workers.shutdownNow();
        renovador.shutdownNow();
        Conclusao conclusao;
        while ((conclusao = conclusoes.poll()) != null) aplicar(conclusao);
    }
}
