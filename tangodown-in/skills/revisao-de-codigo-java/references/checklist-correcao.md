# Checklist 1 — Correção (null-safety, exceções, recursos)

Leia este arquivo ao revisar tratamento de `Optional`, exceções (causa e contexto) e fechamento de recursos. Itens deste grupo costumam ser **Crítico** quando causam bug real ou vazamento; o "como aplicar" detalhado está em [excecoes](../../qualidade-codigo-java/references/excecoes.md) e [imutabilidade, Optional e streams](../../qualidade-codigo-java/references/imutabilidade-optional-streams.md).

### 1. Correção

**Null-safety** — métodos `find*` retornam `Optional`; nunca chame `Optional.get()` sem verificar
presença:

**[❌ Código Não Aderente]:**
```java
// Optional.get() sem verificar presenca, risco de NoSuchElementException
public Produto buscarPorId(Long id) {
    Optional<Produto> produto = produtoRepository.findById(id);
    return produto.get();
}
```

**[🚨 Violação e Explicação]:** `Optional.get()` lança `NoSuchElementException` quando o valor
está ausente; sem `.orElse`/`.orElseThrow`/`.isPresent()`, a borda não tem como reagir.

**[✅ Exemplo de Refatoração]:**
```java
// metodos find* retornam Optional; a borda decide o que fazer na ausencia
public Produto buscarPorId(Long id) {
    return produtoRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Produto nao encontrado: " + id));
}
```

**Exceções com contexto** — preserve a causa original e diga o que estava sendo feito:

**[❌ Código Não Aderente]:**
```java
// perde a causa original (e) e nao diz o que estava sendo feito
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}
```

**[🚨 Violação e Explicação]:** perde a `Throwable cause` (stack trace original) e produz
mensagem genérica sem contexto da operação — investigação quase impossível depois.

**[✅ Exemplo de Refatoração]:**
```java
// preserva a causa (e) e adiciona contexto do que falhou
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}
```

**Recursos com try-with-resources** — `close()` manual não executa se o código anterior lançar:

**[❌ Código Não Aderente]:**
```java
// close() nao executa se ler() lancar excecao, vazando o recurso
InputStream in = new FileInputStream(arquivo);
String conteudo = ler(in);
in.close();
```

**[🚨 Violação e Explicação]:** se `ler(in)` lançar, `in.close()` na linha seguinte nunca é
chamado; o recurso vaza até o GC rodar (pode ser tarde para conexões/socket/arquivo).

**[✅ Exemplo de Refatoração]:**
```java
// try-with-resources garante o fechamento mesmo em caso de excecao
try (InputStream in = new FileInputStream(arquivo)) {
    return ler(in);
}
```

## Exemplo executável

Resultado tipado que obriga o consumidor a tratar o caso "desconhecido" (timeout) em vez de engolir a falha:
[ResultadoCobranca](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/ResultadoCobranca.java), provado por [ResultadoCobrancaTest](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/ResultadoCobrancaTest.java).
