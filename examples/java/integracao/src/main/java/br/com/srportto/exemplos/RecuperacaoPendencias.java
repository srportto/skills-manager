package br.com.srportto.exemplos;

import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.XAutoClaimParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fila de trabalho em Redis/Valkey Streams com consumer group. Distingue as três operações que costumam ser
 * confundidas: XPENDING só <b>inspeciona</b> pendências; XAUTOCLAIM <b>transfere</b> a posse das pendências
 * ociosas para outro consumidor; XACK <b>confirma</b> depois do efeito. O efeito precisa ser idempotente: a
 * mesma entrada pode ser entregue de novo após a reivindicação.
 */
public final class RecuperacaoPendencias {
    @FunctionalInterface
    public interface Efeito { void aplicar(Map<String, String> campos) throws Exception; }

    private final UnifiedJedis redis;
    private final String stream;
    private final String grupo;
    private final long ociosidadeMinimaMs;
    private final int lote;

    public RecuperacaoPendencias(UnifiedJedis redis, String stream, String grupo, Duration ociosidadeMinima, int lote) {
        if (lote <= 0) throw new IllegalArgumentException("Lote deve ser positivo");
        this.redis = Objects.requireNonNull(redis);
        this.stream = Objects.requireNonNull(stream);
        this.grupo = Objects.requireNonNull(grupo);
        this.ociosidadeMinimaMs = ociosidadeMinima.toMillis();
        this.lote = lote;
    }

    public void prepararGrupo() {
        try {
            redis.xgroupCreate(stream, grupo, new StreamEntryID(), true);
        } catch (JedisDataException erro) {
            // Grupo já existente é o estado esperado após o primeiro deploy.
            if (!String.valueOf(erro.getMessage()).startsWith("BUSYGROUP")) throw erro;
        }
    }

    /** Lê entradas nunca entregues; confirma somente as processadas com sucesso. */
    public int lerNovas(String consumidor, Efeito efeito) {
        List<Map.Entry<String, List<StreamEntry>>> lidas = redis.xreadGroup(grupo, consumidor,
                XReadGroupParams.xReadGroupParams().count(lote),
                Map.of(stream, StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY));
        if (lidas == null) return 0;
        return processar(lidas.stream().flatMap(e -> e.getValue().stream()).toList(), efeito);
    }

    /** Reivindica pendências ociosas há mais que o mínimo (consumidor morto ou travado) e as processa. */
    public int reivindicar(String consumidor, Efeito efeito) {
        Map.Entry<StreamEntryID, List<StreamEntry>> resultado = redis.xautoclaim(stream, grupo, consumidor,
                ociosidadeMinimaMs, new StreamEntryID(), XAutoClaimParams.xAutoClaimParams().count(lote));
        return processar(resultado.getValue(), efeito);
    }

    private int processar(List<StreamEntry> entradas, Efeito efeito) {
        int confirmadas = 0;
        for (StreamEntry entrada : entradas) {
            try {
                efeito.aplicar(entrada.getFields());
                redis.xack(stream, grupo, entrada.getID());
                confirmadas++;
            } catch (InterruptedException erro) {
                Thread.currentThread().interrupt();
                return confirmadas;
            } catch (Exception falha) {
                // Sem XACK: a entrada continua pendente e será reivindicada depois da ociosidade mínima.
            }
        }
        return confirmadas;
    }

    public long pendentes() {
        return redis.xpending(stream, grupo).getTotal();
    }
}
