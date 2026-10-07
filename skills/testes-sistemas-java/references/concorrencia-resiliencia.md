# Concorrência e resiliência

Leia este arquivo quando precisar provar limites de concorrência, retry, idempotência, cancelamento ou demanda reativa com testes determinísticos (latch/barreira, relógio injetável).

Use CountDownLatch/CyclicBarrier e timeout de segurança para prender trabalhos ativos antes de lançar excedentes. Meça máximo ativo com AtomicInteger; não infira limite só pelo tamanho de Semaphore. Garanta liberação de latches/executors em finally, inclusive quando a asserção falhar.

Para Reactor, StepVerifier começa com demanda zero, thenRequest em lotes e thenCancel. Use tempo virtual para temporizadores; construir publisher temporizado dentro do supplier do teste virtual. Teste fonte não regulável e política de overflow separadamente.

Retry: relógio monotônico e sleeper injetados; valores literais esperados para tentativas/esperas. Testar interrupção, falha não elegível, orçamento vazio e deadline. Não verificar apenas que um mock foi chamado.

Idempotência: duas conexões/consumidores disputam a mesma chave; reinício usa o mesmo datastore; mesmo payload retorna mesmo resultado, diferente payload conflita. Efeito e registro devem participar da mesma transação quando possível. Falha depois do commit antes do ack precisa preservar efeito único.

Cancelamento: não liberar capacidade de trabalho ainda ativo porque o chamador deixou de esperar. Concluir ou interromper efetivamente a operação e só então devolver recursos.

Veja [testes fundamentos](../../../examples/java/fundamentos/pom.xml) e [integração](../../../examples/java/integracao/pom.xml).


## Provas de referência

| Comportamento | Teste |
|---|---|
| Máximo ativo com virtual threads; liberação após falha; cancelamento não libera trabalho ativo | [ControleConcorrenciaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java) |
| Backoff exponencial com teto e jitter, deadline, quota agregada, interrupção | [PoliticaRetryTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/PoliticaRetryTest.java) |
| Demanda zero, lotes, cancelamento; buffer limitado com tempo virtual | [FluxoSobDemandaTest](../../../examples/java/reativo/src/test/java/br/com/srportto/exemplos/FluxoSobDemandaTest.java) |
| Reserva crítica e rejeição por deadline | [AdmissaoPorPrioridadeTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/AdmissaoPorPrioridadeTest.java) |
| Drenagem no encerramento | [EncerramentoControladoTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/EncerramentoControladoTest.java) |

**Mutação como prova do teste:** quando o teste for escrito depois do código (código herdado), quebre a
linha que implementa o invariante (remova o `release()`, o teto do backoff, o limite de bytes) e confirme
que o teste falha antes de confiar nele.

## Exemplos em Java 25

### Latch e barreira: provar o limite de concorrência sem `sleep`

```java
// ERRADO: sleep como sincronização - passa ou falha conforme a máquina e não prova o limite
Thread.sleep(200);
assertTrue(controle.ativos() <= 4);

// CERTO: a barreira solta todas as tarefas juntas, o latch prende as ativas até a asserção,
// e o máximo é medido por AtomicInteger (não inferido do tamanho do Semaphore)
@Timeout(10) // rede de segurança: teste travado nunca derruba o build por timeout global
@Test
void naoDeveExecutarMaisQueOLimiteEmParalelo() throws Exception {
    int limite = 4, tarefas = 12;
    var semaforo = new Semaphore(limite);
    var ativas = new AtomicInteger();
    var maximo = new AtomicInteger();
    var rejeitadas = new AtomicInteger();
    var largada = new CyclicBarrier(tarefas);   // todas competem ao mesmo tempo
    var liberar = new CountDownLatch(1);        // segura as ativas dentro da seção crítica

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < tarefas; i++) {
            executor.submit(() -> {
                largada.await(5, TimeUnit.SECONDS);
                if (!semaforo.tryAcquire()) { rejeitadas.incrementAndGet(); return null; }
                try {
                    maximo.accumulateAndGet(ativas.incrementAndGet(), Math::max);
                    liberar.await(5, TimeUnit.SECONDS);
                    return null;
                } finally {
                    ativas.decrementAndGet();
                    semaforo.release();         // liberação em finally, mesmo se a asserção falhar
                }
            });
        }
        // espera por condição observável (todas as excedentes rejeitadas), depois solta as ativas
        while (rejeitadas.get() < tarefas - limite) Thread.onSpinWait();
        liberar.countDown();
    }
    assertEquals(limite, maximo.get());
    assertEquals(tarefas - limite, rejeitadas.get());
}
```

Fonte executável da mesma técnica (latches `iniciadas`, `liberarLenta`):
[ControleConcorrenciaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java)
e [ChamadaComDeadlineTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ChamadaComDeadlineTest.java).

### Relógio injetável: tempo controlado pelo teste

```java
// ERRADO: o código chama Instant.now() / Thread.sleep - teste lento e não determinístico
boolean expirou() { return Instant.now().isAfter(criadoEm.plus(ttl)); }

// CERTO: o Clock entra pelo construtor; o teste avança o tempo sem dormir
record Reserva(Instant criadoEm, Duration ttl) {
    boolean expirada(Clock relogio) { return relogio.instant().isAfter(criadoEm.plus(ttl)); }
}

@Test
void reservaDeveExpirarSoDepoisDoTtl() {
    var inicio = Instant.parse("2026-01-01T00:00:00Z");
    var reserva = new Reserva(inicio, Duration.ofMinutes(5));
    assertFalse(reserva.expirada(Clock.fixed(inicio.plus(Duration.ofMinutes(5)), ZoneOffset.UTC)));
    assertTrue(reserva.expirada(Clock.fixed(inicio.plus(Duration.ofMinutes(5)).plusMillis(1), ZoneOffset.UTC)));
}
```

Para prazos e backoff o catálogo usa relógio monotônico (`LongSupplier`) e uma "espera" injetada que só registra a
duração pedida e avança o relógio falso:

```java
private final AtomicLong tempo = new AtomicLong();
private final List<Duration> esperas = new ArrayList<>();

var politica = new PoliticaRetry(3, Duration.ofMillis(10), Duration.ofMillis(100), () -> 0.5,
        espera -> { esperas.add(espera); tempo.addAndGet(espera.toNanos()); },   // nenhum sleep real
        () -> true);
// ... executar(...) com new OrcamentoTempo(Duration.ofSeconds(1), tempo::get)
assertEquals(List.of(Duration.ofMillis(5), Duration.ofMillis(10)), esperas);     // literais, sem recalcular a fórmula
```

Fonte: [PoliticaRetryTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/PoliticaRetryTest.java),
[PoliticaRetry](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/PoliticaRetry.java) e
[OrcamentoTempoTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/OrcamentoTempoTest.java).
Nunca acrescente `Thread.sleep` ao código de produção só para o teste esperar.
