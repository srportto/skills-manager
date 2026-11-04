# Padrões estruturais em Java

Cada entrada: problema, exemplo resumido (Java 25) e quando usar/evitar.

## Adapter

**Problema:** um cliente externo expõe interface incompatível com a porta do domínio.

```java
// Porta do domínio (application) e adapter de saída (infrastructure)
public interface ConsultaCredito { Score consultar(Cpf cpf); }

final class ConsultaCreditoBureauAdapter implements ConsultaCredito {
    private final BureauClient client;
    ConsultaCreditoBureauAdapter(BureauClient client) { this.client = client; }

    @Override public Score consultar(Cpf cpf) {
        // Traduz o modelo externo; o domínio não conhece BureauResponse.
        return new Score(client.buscar(cpf.valor()).pontuacao());
    }
}
```

**Use** em toda fronteira com sistema externo (é o "adapter" da arquitetura hexagonal). **Evite** adaptar
interfaces que você controla — altere a interface.

## Bridge

**Problema:** duas dimensões que variam independentemente (ex.: tipo de relatório × formato de saída).

```java
public abstract class Relatorio {
    protected final Formato formato; // dimensão 2 injetada
    protected Relatorio(Formato formato) { this.formato = formato; }
    public abstract byte[] gerar();
}
```

**Use** quando a herança geraria produto cartesiano de subclasses. **Evite** com uma dimensão só.

## Composite

**Problema:** tratar item e grupo da mesma forma (árvore todo-parte).

```java
public sealed interface Componente permits Item, Grupo {
    long centavos();
}
public record Item(long centavos) implements Componente {}
public record Grupo(List<Componente> filhos) implements Componente {
    public long centavos() { return filhos.stream().mapToLong(Componente::centavos).sum(); }
}
```

**Use** para hierarquias recursivas. **Evite** quando os nós têm operações muito diferentes.

## Decorator

**Problema:** acrescentar comportamento (métrica, cache, limite) sem alterar a implementação.

```java
final class ConsultaCreditoComLimite implements ConsultaCredito {
    private final ConsultaCredito alvo;
    private final Semaphore permissoes; // limite de concorrência da dependência

    ConsultaCreditoComLimite(ConsultaCredito alvo, int limite) {
        this.alvo = alvo;
        this.permissoes = new Semaphore(limite);
    }

    @Override public Score consultar(Cpf cpf) {
        if (!permissoes.tryAcquire()) throw new RejectedExecutionException("Bureau saturado");
        try { return alvo.consultar(cpf); } finally { permissoes.release(); }
    }
}
```

**Use** para preocupações transversais compostas. A ordem dos decorators altera semântica (ver
`resiliencia-controle-fluxo-java`). **Evite** pilhas longas sem teste da composição.

## Facade

**Problema:** cliente precisa orquestrar muitos objetos de um subsistema.

```java
public final class CheckoutFacade {
    // Esconde estoque, pagamento e expedição atrás de uma operação de caso de uso.
    public Confirmacao finalizar(Carrinho carrinho) { /* ... */ return null; }
}
```

**Use** como caso de uso de `application`. **Evite** facade que vira "god class" com regra de domínio.

## Flyweight

**Problema:** milhões de objetos repetem o mesmo estado imutável.

```java
// Estado intrínseco compartilhado por cache limitado; extrínseco passado na chamada.
private static final Map<String, Moeda> MOEDAS = Map.of("BRL", new Moeda("BRL", 2), "USD", new Moeda("USD", 2));
```

**Use** quando medição de memória mostra a duplicação. **Evite** caches ilimitados por chave arbitrária.

## Proxy

**Problema:** controlar acesso (autorização, lazy loading, chamada remota) a um objeto.

```java
final class RelatorioAutorizado implements Relatorios {
    private final Relatorios alvo;
    private final Autorizacao autorizacao;
    RelatorioAutorizado(Relatorios alvo, Autorizacao autorizacao) { this.alvo = alvo; this.autorizacao = autorizacao; }

    @Override public byte[] exportar(Usuario usuario, long id) {
        autorizacao.exigir(usuario, "relatorio:exportar");
        return alvo.exportar(usuario, id);
    }
}
```

**Use** quando o acesso precisa de política. Lembre que proxies do Spring (`@Transactional`) não atuam em
chamadas internas da mesma classe.
