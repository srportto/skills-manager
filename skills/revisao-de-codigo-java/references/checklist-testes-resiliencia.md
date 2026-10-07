# Checklist 8 — Testes, evidência executada e resiliência

Leia este arquivo ao revisar testes (nomes, bordas, independência), ao montar a tabela de evidência de um veredicto, ou ao varrer fila/espera/retry/ack sem limite. Mecanismos de resiliência: `resiliencia-controle-fluxo-java`; provas: `testes-sistemas-java`.

### 8. Testes

Caso feliz + bordas + erro; nomes descritivos; sem dependência de ordem.

**[❌ Código Não Aderente]:**
```java
// nome nao descreve cenario, sem cobertura de borda/erro, so caminho feliz
@Test
void test1() {
    Produto produto = service.criar(request);
    assertEquals(produto.nome(), "Mouse");
}
```

**[🚨 Violação e Explicação]:** nome `test1` não diz o que está sendo testado; não há cobertura
de borda ou erro — quando o build quebrar, o agente tem que abrir o teste para entender o que
deveria estar passando.

**[✅ Exemplo de Refatoração]:**
```java
// nomes descritivos (deveXQuandoY), casos feliz + erro independentes
// (exemplo com AssertJ fluente; JUnit 5 puro tambem e valido, ver observacao abaixo)
@Test
void deveCriarProdutoQuandoDadosValidos() {
    Produto produto = service.criar(requestValido());

    assertThat(produto.nome()).isEqualTo("Mouse");
}

@Test
void deveLancarBusinessExceptionQuandoPrecoForZero() {
    assertThatThrownBy(() -> service.criar(requestComPrecoZero()))
            .isInstanceOf(BusinessException.class);
}
```

Use JUnit 5. AssertJ (`assertThat(...)`) é bem-vindo quando já está disponível no projeto — confira o
`pom.xml` antes de sugeri-lo em uma revisão — mas JUnit 5 puro (`assertEquals`, `assertThrows`,
`assertDoesNotThrow`) também é válido e é o padrão usado nos assets deste catálogo (`ProdutoTest`,
`PedidoTest`, `PublicarEventoServiceTest`, nenhum dos quais depende de AssertJ). Não marque
`assertEquals`/`assertThrows` como achado de revisão só por não ser AssertJ. Independente da
biblioteca de asserção, cada teste deve poder rodar sozinho (sem depender de estado deixado por um
teste anterior) e cobrir pelo menos um caso de borda (lista vazia, valor limite) além do feliz e do
erro.

**Evidência executada (vale para todo veredicto):** relate separadamente **compilação**, **testes unitários**,
**integração** (serviços reais) e **carga**, cada um com comando e resultado (passou/falhou/pulado e
contagem). Teste não executado é **pendência**, nunca aprovação; build com `-DskipTests` não sustenta
aprovação de regra de negócio nem de resiliência. Se o código toca concorrência, mensageria ou limites, exija a
prova correspondente (ver `testes-sistemas-java`) — "compila e o caminho feliz passa" não prova limite.

### 8.1. Resiliência, efeitos e limites

| Achado | Severidade | Por quê |
|---|---|---|
| Fila/buffer/executor **sem limite** (`new LinkedBlockingQueue<>()`, `Executors.newFixedThreadPool` com fila ilimitada, `onBackpressureBuffer()` sem capacidade) | **Crítico** em fluxo com carga externa | Pico vira `OutOfMemoryError` e latência ilimitada |
| Espera sem limite (`acquire()`, `get()`, `join()`, cliente HTTP/JDBC sem timeout, pool sem `connection-timeout`) | **Crítico** em chamada remota | Dependência lenta prende threads/conexões até esgotar |
| Retry infinito, sem backoff/jitter, de erro permanente, ou em várias camadas | **Crítico** | Amplifica a carga sobre a dependência que já está falhando |
| Ack/commit **antes** do efeito durável; descarte de mensagem de negócio sem quarentena | **Crítico** | Perda silenciosa de dados |
| Efeito sujeito a repetição sem idempotência persistida (cobrança, pedido, evento) | **Crítico** | Retry/reentrega duplica efeito financeiro |
| Fallback que inventa sucesso (pagamento "aprovado", estoque "reservado") | **Crítico** | Mentira de negócio |
| Permissão/recurso liberado antes do fim do trabalho assíncrono | **Crítico** | Limite de concorrência deixa de valer |
| Cache indisponível enviando todo o tráfego ao banco | **Importante** | Cascata sob falha |
| Limite sem unidade/escopo/motivo; rejeição sem métrica | **Importante** | Proteção não verificável nem operável |
| Liveness dependente de serviço externo | **Importante** | Reinício em massa na queda da dependência |
| `traceId`/id de negócio como label de métrica | **Importante** | Explosão de cardinalidade |

Referência dos mecanismos: `resiliencia-controle-fluxo-java`; ack/offset/DLQ: `mensageria-sqs-kafka`. Não
exija breaker, reatividade ou broker onde não há dependência/carga que justifique (proporcionalidade).


## Exemplo antes/depois — fila sem limite (Crítico)

**[❌ Código Não Aderente]:**
```java
// fila ilimitada: pico de carga vira OutOfMemoryError e latencia sem teto
private final BlockingQueue<Pedido> fila = new LinkedBlockingQueue<>();

public void receber(Pedido pedido) {
    fila.add(pedido); // nunca rejeita
}
```

**[✅ Exemplo de Refatoração]:**
```java
// capacidade explicita e rejeicao observavel quando cheia (o chamador decide: 429/503, DLQ, etc.)
private final BlockingQueue<Pedido> fila = new ArrayBlockingQueue<>(1_000);

public boolean receber(Pedido pedido) {
    boolean aceito = fila.offer(pedido); // false = cheia, sem bloquear
    if (!aceito) {
        metricas.contarRejeicao("fila-pedidos"); // rejeicao sempre com metrica
    }
    return aceito;
}
```

Fonte executável: [FilaLimitada](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/FilaLimitada.java), provada por [FilaLimitadaTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/FilaLimitadaTest.java).
