package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {
    @Test
    void deveLimitarBurstEReporSemExcederTeto() {
        var tempo = new AtomicLong();
        var quota = new TokenBucket(2, 1, tempo::get);
        assertTrue(quota.tentar());
        assertTrue(quota.tentar());
        assertFalse(quota.tentar());

        tempo.set(500_000_000);
        assertFalse(quota.tentar());
        tempo.set(1_000_000_000);
        assertTrue(quota.tentar());
        assertFalse(quota.tentar());

        // Muito tempo ocioso não acumula além da capacidade (burst limitado).
        tempo.set(100_000_000_000L);
        assertTrue(quota.tentar());
        assertTrue(quota.tentar());
        assertFalse(quota.tentar());
    }

    @Test
    void tenantsComBaldesSeparadosNaoDevemInterferir() {
        var tempo = new AtomicLong();
        var tenantRuidoso = new TokenBucket(1, 1, tempo::get);
        var tenantComportado = new TokenBucket(1, 1, tempo::get);
        assertTrue(tenantRuidoso.tentar());
        assertFalse(tenantRuidoso.tentar());
        assertTrue(tenantComportado.tentar());
    }

    @Test
    void deveRecusarRelogioQueRegride() {
        var tempo = new AtomicLong(10);
        var quota = new TokenBucket(1, 1, tempo::get);
        tempo.set(5);
        assertThrows(IllegalStateException.class, quota::tentar);
    }
}
