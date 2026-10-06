package br.com.srportto.exemplos;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Experimento de chaos controlado (ver chaos-engineer → experiment-design):
 * <ol>
 *   <li><b>Hipótese:</b> se o coordenador de quotas (Valkey) ficar mais lento que o timeout do cliente, cada decisão
 *       continua saindo em menos de 300 ms, o serviço passa ao limite local degradado (nunca ilimitado) e volta ao
 *       coordenador quando a latência some.</li>
 *   <li><b>Baseline:</b> decisões pelo coordenador, rápidas.</li>
 *   <li><b>Injeção:</b> latência de 500 ms via Toxiproxy entre a aplicação e o Valkey.</li>
 *   <li><b>Abort:</b> qualquer decisão acima de 2 s interrompe o experimento; o toxic é removido no finally.</li>
 *   <li><b>Recuperação:</b> após remover a falha, as decisões voltam a ser do coordenador.</li>
 * </ol>
 */
class ExperimentoCoordenadorLentoExternoIT {
    static final Network REDE = Network.newNetwork();
    static final GenericContainer<?> VALKEY = RedisExternoSuporte.container().withNetwork(REDE).withNetworkAliases("valkey");
    static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.12.0"))
            .withNetwork(REDE);
    static Proxy proxy;
    static JedisPooled redis;

    @BeforeAll
    static void iniciar() throws Exception {
        VALKEY.start();
        TOXIPROXY.start();
        var controle = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
        proxy = controle.createProxy("valkey", "0.0.0.0:8666", "valkey:6379");
        // Timeout curto do cliente: a dependência lenta não segura a thread da requisição.
        var config = DefaultJedisClientConfig.builder().connectionTimeoutMillis(100).socketTimeoutMillis(100).build();
        redis = new JedisPooled(new HostAndPort(TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(8666)), config);
    }

    @AfterAll
    static void parar() {
        if (redis != null) redis.close();
        TOXIPROXY.stop();
        VALKEY.stop();
        REDE.close();
    }

    private static Map<LimiteDistribuido.Decisao, Integer> rodada(LimiteDistribuido limite, int chamadas) {
        var contagem = new EnumMap<LimiteDistribuido.Decisao, Integer>(LimiteDistribuido.Decisao.class);
        for (int i = 0; i < chamadas; i++) {
            long inicio = System.nanoTime();
            var decisao = limite.avaliar("tenant-experimento");
            long duracao = System.nanoTime() - inicio;
            // Condição de abort: o experimento não pode deixar requisições presas.
            if (duracao > Duration.ofSeconds(2).toNanos()) fail("ABORT: decisão levou " + duracao / 1_000_000 + " ms");
            assertTrue(duracao < Duration.ofMillis(300).toNanos(), "decisão lenta: " + duracao / 1_000_000 + " ms");
            contagem.merge(decisao, 1, Integer::sum);
        }
        return contagem;
    }

    @Test
    void coordenadorLentoDeveDegradarParaLimiteLocalLimitadoERecuperar() throws Exception {
        // Quota global folgada; limite local degradado de 5 de burst por instância.
        var limite = new LimiteDistribuido(LimiteDistribuido.redis(redis), 1_000, Duration.ofMinutes(1), 5, 1.0, 1_000, System::nanoTime);

        var baseline = rodada(limite, 20);
        assertEquals(Map.of(LimiteDistribuido.Decisao.PERMITIDO, 20), baseline);

        try {
            proxy.toxics().latency("valkey-lento", ToxicDirection.DOWNSTREAM, 500);
            var durante = rodada(limite, 20);
            // Nenhuma decisão "do coordenador" e, no máximo, o burst local permitido: degradação limitada.
            assertEquals(null, durante.get(LimiteDistribuido.Decisao.PERMITIDO));
            assertTrue(durante.getOrDefault(LimiteDistribuido.Decisao.PERMITIDO_DEGRADADO, 0) <= 6, durante::toString);
            assertTrue(durante.getOrDefault(LimiteDistribuido.Decisao.NEGADO_DEGRADADO, 0) >= 14, durante::toString);
            assertTrue(limite.falhasCoordenador() >= 20);
        } finally {
            // Rollback da falha sempre acontece, mesmo se uma asserção falhar.
            proxy.toxics().get("valkey-lento").remove();
        }

        long limiteRecuperacao = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (limite.avaliar("tenant-experimento") != LimiteDistribuido.Decisao.PERMITIDO) {
            if (System.nanoTime() > limiteRecuperacao) fail("Não recuperou em 10 s após remover a falha");
            Thread.sleep(100);
        }
        assertEquals(Map.of(LimiteDistribuido.Decisao.PERMITIDO, 10), rodada(limite, 10));
    }
}
