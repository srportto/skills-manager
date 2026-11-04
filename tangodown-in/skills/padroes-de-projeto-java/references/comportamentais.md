# Padrões comportamentais em Java

Cada entrada: problema, exemplo resumido (Java 25) e quando usar/evitar.

## Chain of Responsibility

**Problema:** sequência de validações/tratadores em que cada um decide se trata ou repassa.

```java
public interface Validacao { Optional<String> violacao(Pedido pedido); }

// A cadeia é uma lista injetada; para na primeira violação.
static Optional<String> validar(List<Validacao> cadeia, Pedido pedido) {
    return cadeia.stream().map(v -> v.violacao(pedido)).flatMap(Optional::stream).findFirst();
}
```

**Use** quando a ordem e o conjunto de regras variam. **Evite** quando a ordem implícita de beans esconde
dependência — declare `@Order` ou monte a lista explicitamente.

## Command

**Problema:** representar uma ação como dado (fila, auditoria, undo, retry).

```java
public sealed interface Comando permits Reservar, Cancelar {}
public record Reservar(String pedido, int quantidade) implements Comando {}
public record Cancelar(String pedido) implements Comando {}
```

**Use** para mensagens/outbox e reprocessamento. Comandos com efeito externo precisam de chave idempotente.

## Iterator

**Problema:** percorrer coleção sem expor estrutura interna.

```java
// Em Java, exponha Stream/Iterable somente leitura em vez de devolver a lista mutável.
public Stream<ItemPedido> itens() { return itens.stream(); }
```

**Use** `Iterable`/`Stream` ou paginação por cursor para coleções grandes. **Evite** carregar tudo em
memória para iterar.

## Mediator

**Problema:** muitos componentes conversando entre si (acoplamento N×N).

```java
// Componentes publicam eventos; o mediador (ex.: ApplicationEventPublisher) entrega aos interessados.
publisher.publishEvent(new PedidoConfirmado(pedidoId));
```

**Use** para desacoplar módulos de um monólito modular. **Evite** como substituto de transação: eventos
síncronos em memória não são entrega durável — use outbox quando o efeito precisa sobreviver a falhas.

## Memento

**Problema:** salvar e restaurar estado sem violar encapsulamento.

```java
public final class Rascunho {
    private String texto = "";
    public record Snapshot(String texto) {}
    public Snapshot salvar() { return new Snapshot(texto); }
    public void restaurar(Snapshot snapshot) { texto = snapshot.texto(); }
}
```

**Use** em undo/histórico com limite de snapshots. **Evite** histórico ilimitado em memória.

## Observer

**Problema:** notificar dependentes quando algo muda.

```java
@EventListener
void aoConfirmar(PedidoConfirmado evento) { /* reage sem acoplar o emissor */ }
```

**Use** para reações locais. Ouvinte lento bloqueia o emissor síncrono; ouvinte assíncrono precisa de
executor limitado e política de rejeição.

## State

**Problema:** comportamento muda com o estado e `if/switch` sobre status se espalha.

```java
public sealed interface StatusPedido permits Pendente, Pago, Cancelado {
    StatusPedido pagar();
}
public record Pendente() implements StatusPedido { public StatusPedido pagar() { return new Pago(); } }
public record Pago() implements StatusPedido {
    public StatusPedido pagar() { throw new IllegalStateException("Pedido já pago"); }
}
public record Cancelado() implements StatusPedido {
    public StatusPedido pagar() { throw new IllegalStateException("Pedido cancelado"); }
}
```

**Use** quando transições têm regra. **Evite** para dois estados triviais.

## Strategy

**Problema:** algoritmos intercambiáveis escolhidos em runtime. Forma preferida no catálogo: lista
injetada com predicado de suporte (ver SKILL).

```java
public interface Frete { boolean suporta(Envio envio); long centavos(Envio envio); }
```

**Use** quando surge a segunda variação real. **Evite** interface com uma implementação especulativa.

## Template Method

**Problema:** algoritmo com passos fixos e alguns variáveis.

```java
public abstract class Importacao {
    public final Resultado executar(Arquivo arquivo) {
        var linhas = ler(arquivo);          // passo fixo
        var validas = validar(linhas);      // passo variável
        return gravar(validas);             // passo fixo
    }
    protected abstract List<Linha> validar(List<Linha> linhas);
    private List<Linha> ler(Arquivo arquivo) { return List.of(); }
    private Resultado gravar(List<Linha> linhas) { return new Resultado(linhas.size()); }
}
```

**Use** com poucas variações estáveis. Prefira composição (Strategy) quando as variações crescem.
