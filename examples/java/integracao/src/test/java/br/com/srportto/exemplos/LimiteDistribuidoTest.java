package br.com.srportto.exemplos;

import br.com.srportto.exemplos.LimiteDistribuido.Decisao;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimiteDistribuidoTest {
    private final AtomicLong tempo = new AtomicLong();
    private final AtomicBoolean coordenadorFora = new AtomicBoolean();
    private final Map<String, Long> contadores = new HashMap<>();

    /** Coordenador em memória com a mesma semântica do script: incremento e janela numa operação só. */
    private final LimiteDistribuido.Coordenador coordenador = (chave, janela) -> {
        if (coordenadorFora.get()) throw new IllegalStateException("Redis indisponível");
        return contadores.merge(chave, 1L, Long::sum);
    };

    private LimiteDistribuido limite() {
        // Global: 3 por janela. Degradado (coordenador fora): 1 por segundo por identidade, por instância.
        return new LimiteDistribuido(coordenador, 3, Duration.ofSeconds(1), 1, 1.0, 1_000, tempo::get);
    }

    @Test
    void deveAplicarQuotaGlobalPorIdentidade() {
        var limite = limite();
        assertEquals(Decisao.PERMITIDO, limite.avaliar("cliente-a"));
        assertEquals(Decisao.PERMITIDO, limite.avaliar("cliente-a"));
        assertEquals(Decisao.PERMITIDO, limite.avaliar("cliente-a"));
        assertEquals(Decisao.NEGADO_QUOTA, limite.avaliar("cliente-a"));
        // Outra identidade tem a própria quota.
        assertEquals(Decisao.PERMITIDO, limite.avaliar("cliente-b"));
    }

    @Test
    void coordenadorIndisponivelDeveCairParaLimiteLocalConservadorENaoLiberarTudo() {
        var limite = limite();
        coordenadorFora.set(true);
        assertEquals(Decisao.PERMITIDO_DEGRADADO, limite.avaliar("cliente-a"));
        assertEquals(Decisao.NEGADO_DEGRADADO, limite.avaliar("cliente-a"));
        tempo.set(Duration.ofSeconds(1).toNanos());
        assertEquals(Decisao.PERMITIDO_DEGRADADO, limite.avaliar("cliente-a"));
        assertEquals(3, limite.falhasCoordenador());
    }

    @Test
    void limitesLocaisDegradadosDevemTerCardinalidadeLimitada() {
        var limite = limite();
        coordenadorFora.set(true);
        for (int i = 0; i < 5_000; i++) limite.avaliar("cliente-" + i);
        // Identidades arbitrárias (ataque) não podem crescer a memória sem limite.
        assertEquals(1_000, limite.identidadesLocais());
    }
}
