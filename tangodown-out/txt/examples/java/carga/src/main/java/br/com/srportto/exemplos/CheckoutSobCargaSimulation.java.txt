package br.com.srportto.exemplos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Gerador de carga Java em malha aberta: dispara no ritmo planejado independentemente das respostas e mede a
 * latência a partir do instante <b>planejado</b> (evita coordinated omission). O próprio gerador é limitado
 * (requisições em voo); quando ele satura, a requisição é contada como descartada pelo gerador — nunca escondida.
 */
public final class CheckoutSobCargaSimulation {
    public record Fase(String nome, int requisicoesPorSegundo, Duration duracao) {
        public Fase {
            if (requisicoesPorSegundo <= 0 || duracao.isZero() || duracao.isNegative()) throw new IllegalArgumentException("Fase inválida");
        }
    }

    public record ResultadoFase(String nome, int oferecidas, int aceitas, int rejeitadas, int erros,
                                int descartadasPeloGerador, long p50AceitasMs, long p99AceitasMs) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI alvo;
    private final int maxEmVoo;

    public CheckoutSobCargaSimulation(URI alvo, int maxEmVoo) {
        if (maxEmVoo <= 0) throw new IllegalArgumentException("Limite do gerador deve ser positivo");
        this.alvo = Objects.requireNonNull(alvo);
        this.maxEmVoo = maxEmVoo;
    }

    public List<ResultadoFase> executar(List<Fase> fases) throws InterruptedException {
        var resultados = new ArrayList<ResultadoFase>();
        for (Fase fase : fases) resultados.add(executar(fase));
        return resultados;
    }

    private ResultadoFase executar(Fase fase) throws InterruptedException {
        var emVoo = new Semaphore(maxEmVoo);
        var aceitas = new AtomicInteger();
        var rejeitadas = new AtomicInteger();
        var erros = new AtomicInteger();
        int descartadas = 0;
        List<Long> latenciasAceitas = Collections.synchronizedList(new ArrayList<>());

        long intervalo = TimeUnit.SECONDS.toNanos(1) / fase.requisicoesPorSegundo();
        int total = (int) (fase.duracao().toNanos() / intervalo);
        long inicio = System.nanoTime();
        for (int i = 0; i < total; i++) {
            long planejado = inicio + i * intervalo;
            long espera = planejado - System.nanoTime();
            if (espera > 0) LockSupport.parkNanos(espera);
            if (!emVoo.tryAcquire()) {
                descartadas++;
                continue;
            }
            var pedido = HttpRequest.newBuilder(alvo)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Tenant", "carga")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"centavos\":1000}"))
                    .build();
            http.sendAsync(pedido, HttpResponse.BodyHandlers.discarding()).whenComplete((resposta, falha) -> {
                try {
                    long latenciaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - planejado);
                    if (falha != null) erros.incrementAndGet();
                    else if (resposta.statusCode() == 201) {
                        aceitas.incrementAndGet();
                        latenciasAceitas.add(latenciaMs);
                    } else if (resposta.statusCode() == 503) rejeitadas.incrementAndGet();
                    else erros.incrementAndGet();
                } finally {
                    emVoo.release();
                }
            });
        }
        // Espera limitada pelas respostas da fase antes de medir a próxima.
        if (!emVoo.tryAcquire(maxEmVoo, 10, TimeUnit.SECONDS)) erros.addAndGet(maxEmVoo - emVoo.availablePermits());
        else emVoo.release(maxEmVoo);

        List<Long> ordenadas;
        synchronized (latenciasAceitas) {
            ordenadas = latenciasAceitas.stream().sorted().toList();
        }
        return new ResultadoFase(fase.nome(), total, aceitas.get(), rejeitadas.get(), erros.get(), descartadas,
                percentil(ordenadas, 0.50), percentil(ordenadas, 0.99));
    }

    static long percentil(List<Long> ordenadas, double p) {
        if (ordenadas.isEmpty()) return 0;
        int indice = (int) Math.ceil(p * ordenadas.size()) - 1;
        return ordenadas.get(Math.max(0, Math.min(indice, ordenadas.size() - 1)));
    }
}
