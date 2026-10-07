package br.com.srportto.exemplos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quota global em Valkey real: atomicidade sob concorrência e ausência de quota "eterna". */
class LimiteDistribuidoExternoIT {
    static final GenericContainer<?> VALKEY = RedisExternoSuporte.container();
    static JedisPooled redis;

    @BeforeAll
    static void iniciar() {
        VALKEY.start();
        redis = RedisExternoSuporte.cliente(VALKEY);
    }

    @AfterAll
    static void parar() {
        redis.close();
        VALKEY.stop();
    }

    private static LimiteDistribuido limite(int maximo, Duration janela) {
        return new LimiteDistribuido(LimiteDistribuido.redis(redis), maximo, janela, 1, 1.0, 1_000, System::nanoTime);
    }

    @DisplayName("LimiteDistribuidoExterno: Requisicoes concorrentes de varias replicas nao devem ultrapassar a quota")
    @Test
    void requisicoesConcorrentesDeVariasReplicasNaoDevemUltrapassarAQuota() throws Exception {
        int replicas = 4;
        int chamadasPorReplica = 25;
        var largada = new CyclicBarrier(replicas * chamadasPorReplica);
        int permitidas = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var resultados = new ArrayList<Future<LimiteDistribuido.Decisao>>();
            for (int r = 0; r < replicas; r++) {
                // Cada "réplica" tem sua instância local, mas todas compartilham o coordenador.
                var replica = limite(10, Duration.ofSeconds(30));
                for (int i = 0; i < chamadasPorReplica; i++) {
                    resultados.add(executor.submit(() -> {
                        largada.await(10, TimeUnit.SECONDS);
                        return replica.avaliar("tenant-concorrente");
                    }));
                }
            }
            for (var resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS) == LimiteDistribuido.Decisao.PERMITIDO) permitidas++;
            }
        }
        assertEquals(10, permitidas);
        assertTrue(redis.pttl(LimiteDistribuido.chave("tenant-concorrente")) > 0, "a janela precisa ter expiração");
    }

    @DisplayName("LimiteDistribuidoExterno: Chave sem expiracao deixada por falha parcial deve ser corrigida na proxima chamada")
    @Test
    void chaveSemExpiracaoDeixadaPorFalhaParcialDeveSerCorrigidaNaProximaChamada() throws Exception {
        // Simula o defeito do INCR + EXPIRE em duas chamadas: o processo caiu entre elas e a chave ficou sem TTL.
        String chave = LimiteDistribuido.chave("tenant-orfao");
        redis.set(chave, "99");
        assertEquals(-1, redis.pttl(chave));

        var limite = limite(5, Duration.ofMillis(500));
        assertEquals(LimiteDistribuido.Decisao.NEGADO_QUOTA, limite.avaliar("tenant-orfao"));
        assertTrue(redis.pttl(chave) > 0, "o script deve restaurar a expiração");

        Thread.sleep(700);
        // Sem quota permanente: após a janela, a identidade volta a ser atendida.
        assertEquals(LimiteDistribuido.Decisao.PERMITIDO, limite.avaliar("tenant-orfao"));
    }
}
