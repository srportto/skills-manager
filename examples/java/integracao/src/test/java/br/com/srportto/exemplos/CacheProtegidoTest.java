package br.com.srportto.exemplos;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheProtegidoTest {
    @Test
    void cacheIndisponivelDeveManterLimiteDoBanco() throws Exception {
        var chamadas = new AtomicInteger();
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var cache = new CacheProtegido<String>(
                chave -> { throw new IllegalStateException("Cache indisponível"); }, (chave, valor) -> {},
                chave -> {
                    chamadas.incrementAndGet();
                    entrou.countDown();
                    if (!liberar.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
                    return "valor";
                }, 1, 16);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> primeira = executor.submit(() -> cache.obter("a"));
            try {
                assertTrue(entrou.await(5, TimeUnit.SECONDS));
                // Com o cache fora, o banco continua limitado: o excedente é rejeitado, não enviado.
                assertThrows(RejectedExecutionException.class, () -> cache.obter("b"));
                assertEquals(1, chamadas.get());
            } finally {
                liberar.countDown();
            }
            assertEquals("valor", primeira.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void chaveQuenteDeveTerUmaUnicaRecomputacaoConcorrente() throws Exception {
        var armazenamento = new ConcurrentHashMap<String, String>();
        var chamadas = new AtomicInteger();
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var cache = new CacheProtegido<String>(armazenamento::get, armazenamento::put, chave -> {
            chamadas.incrementAndGet();
            entrou.countDown();
            liberar.await(5, TimeUnit.SECONDS);
            return "valor";
        }, 2, 1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> primeira = executor.submit(() -> cache.obter("quente"));
            try {
                assertTrue(entrou.await(5, TimeUnit.SECONDS));
                assertThrows(RejectedExecutionException.class, () -> cache.obter("quente"));
            } finally {
                liberar.countDown();
            }
            assertEquals("valor", primeira.get(5, TimeUnit.SECONDS));
            assertEquals("valor", cache.obter("quente"));
            assertEquals(1, chamadas.get());
        }
    }
}
