package br.com.srportto.exemplos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.StreamEntryID;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Streams em Valkey real: consumidor morto deixa pendências que outro consumidor reivindica e conclui. */
class RecuperacaoPendenciasExternoIT {
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

    @Test
    void pendenciasDeConsumidorMortoDevemSerReivindicadasProcessadasUmaVezEConfirmadas() throws Exception {
        String stream = "agenda:vencidos";
        var recuperacao = new RecuperacaoPendencias(redis, stream, "expiradores", Duration.ofMillis(200), 10);
        recuperacao.prepararGrupo();
        for (int i = 0; i < 3; i++) redis.xadd(stream, StreamEntryID.NEW_ENTRY, Map.of("id", "aut-" + i));

        // Consumidor A lê e "morre" sem confirmar.
        var lidasPorA = recuperacao.lerNovas("consumidor-a", mensagem -> { throw new IllegalStateException("queda"); });
        assertEquals(0, lidasPorA);
        assertEquals(3, recuperacao.pendentes());

        Set<String> efeitos = ConcurrentHashMap.newKeySet();
        // Antes de ficarem ociosas o suficiente, as pendências não são roubadas de um consumidor vivo.
        assertEquals(0, recuperacao.reivindicar("consumidor-b", mensagem -> efeitos.add(mensagem.get("id"))));

        Thread.sleep(300);
        assertEquals(3, recuperacao.reivindicar("consumidor-b", mensagem -> efeitos.add(mensagem.get("id"))));
        assertEquals(Set.of("aut-0", "aut-1", "aut-2"), efeitos);
        assertEquals(0, recuperacao.pendentes());
    }
}
