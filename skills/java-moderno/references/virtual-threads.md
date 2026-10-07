# Virtual threads

Leia este arquivo quando for habilitar virtual threads no Spring Boot, avaliar se um serviço ganha com elas (I/O-bound vs CPU-bound), investigar pinning, limitar concorrência sobre recurso finito (conexões de banco) ou escolher entre `ThreadLocal` e `ScopedValue`.

## 6. Virtual threads

Threads leves gerenciadas pela JVM (não mapeadas 1:1 com thread do SO), permitindo dezenas de
milhares de threads concorrentes com baixo custo. Ativação em aplicações Spring Boot:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Quando ajudam:** cargas **I/O-bound** com muitas requisições concorrentes — chamadas HTTP a outros
serviços, queries JDBC, leitura de arquivo. Cada requisição ocupa uma virtual thread barata enquanto
espera o I/O, sem esgotar um pool fixo de threads do SO.

**Quando NÃO ajudam / atenção:**
- **CPU-bound:** processamento pesado (cálculo, criptografia, serialização grande) não ganha nada —
  o gargalo é a CPU, não a espera por I/O; o número de núcleos continua sendo o limite real.
- **Pinning no Java 25:** desde o JDK 24 ([JEP 491](https://openjdk.org/jeps/491)) bloquear dentro de
  `synchronized` ou em `Object.wait()` **não** prende mais a carrier thread — a recomendação antiga de
  trocar todo `synchronized` por `ReentrantLock` para evitar pinning não vale para Java 25. Pinning ainda
  ocorre em código nativo/FFM (JNI, upcalls) e em casos raros de inicialização de classe; detecte com o
  evento JFR `jdk.VirtualThreadPinned` em vez de presumir.
- **Virtual threads não aumentam capacidade de recursos:** 10 conexões no banco continuam 10 conexões.
  Milhares de virtual threads esperando o pool só deslocam a fila para dentro da JVM. Limite a admissão
  (`Semaphore`/bulkhead por recurso), dê timeout à aquisição de conexão e rejeite o excedente — ver
  `resiliencia-controle-fluxo-java` ([isolamento e degradação](../../resiliencia-controle-fluxo-java/references/isolamento-degradacao-java.md)).
- **Não faça pool de virtual threads:** crie uma por tarefa (`Executors.newVirtualThreadPerTaskExecutor()`);
  para limitar concorrência use semáforo, não um pool de tamanho fixo.
- **`ThreadLocal` com muitas threads:** caches por thread (ex.: buffers grandes) multiplicam memória quando
  há centenas de milhares de virtual threads; prefira objetos com escopo explícito.

```java
// Limite real de concorrência com virtual threads: o semáforo, não o executor.
var conexoesDisponiveis = new Semaphore(10);
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var pedido : pedidos) {
        executor.submit(() -> {
            // Espera limitada: excedente falha de forma visível em vez de enfileirar sem fim.
            if (!conexoesDisponiveis.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                throw new RejectedExecutionException("Banco saturado");
            }
            try {
                return repositorio.salvar(pedido);
            } finally {
                conexoesDisponiveis.release();
            }
        });
    }
}
```

## `ScopedValue`: contexto imutável por escopo

`ScopedValue` (JEP 506) é a alternativa a `ThreadLocal` para passar contexto (usuário, `traceId`) a quem é chamado
dentro de um escopo: o valor é imutável, visível só durante `run`/`call` e liberado ao sair, sem risco de vazar
entre tarefas nem de multiplicar memória por thread. A API foi finalizada no JDK 25; se a versão do projeto for
outra, consulte a JEP da versão (em releases anteriores era preview). Structured concurrency (`StructuredTaskScope`)
segue preview no Java 25 e não faz parte do catálogo.

```java
// Antes: ThreadLocal mutável; esquecer o remove() vaza contexto para a próxima tarefa
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
TRACE_ID.set(traceId);
try { atender(); } finally { TRACE_ID.remove(); }

// Depois: ScopedValue - o vínculo acaba sozinho ao sair do escopo
private static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
ScopedValue.where(TRACE_ID, traceId).run(() -> atender()); // TRACE_ID.get() dentro de atender()
```

Em app Spring, o MDC do SLF4J continua sendo o mecanismo de log do catálogo
([logs-mdc-correlacao](../../monitoramento-java/references/logs-mdc-correlacao.md)); `ScopedValue` serve ao código de
aplicação que precisa propagar contexto sem `ThreadLocal`.

## Prova executável

Os limites de concorrência com `Semaphore` e virtual threads (máximo ativo, liberação após falha) são provados em
[ControleConcorrenciaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ControleConcorrenciaTest.java).
Para modelar o resultado de uma tarefa remota com tipo selado, veja
[ResultadoCobranca](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/ResultadoCobranca.java).
