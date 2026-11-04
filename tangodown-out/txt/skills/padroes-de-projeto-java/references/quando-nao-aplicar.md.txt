# Quando NÃO aplicar pattern

Leia este arquivo quando estiver prestes a introduzir um pattern e quiser confirmar que há problema real a resolver, ou ao revisar código com indireção suspeita (interface de uma implementação, factory de `new`, Singleton manual).

## Quando NÃO aplicar pattern

Nem todo código "rígido" precisa de um pattern. Aplicar pattern sem necessidade real é
over-engineering — adiciona indireção, classes e complexidade cognitiva sem reduzir problema algum.
Três armadilhas comuns:

1. **Interface com 1 implementação, sem variação prevista** — criar `interface PagamentoService` só
   porque "no futuro pode ter outro jeito de pagar" quando hoje só existe `PagamentoServiceImpl` é
   abstração especulativa. Espere a segunda implementação aparecer de verdade.
2. **Factory para `new` simples** — sem lógica condicional na criação, `PedidoFactory.criar()` que só
   faz `return new Pedido(...)` é indireção sem ganho. Chame `new Pedido(...)` direto.
3. **Singleton onde injeção resolve** — em Spring, um bean `@Service`/`@Component` já é singleton por
   padrão (gerenciado pelo container). `getInstance()` estático manual duplica essa responsabilidade
   e piora testabilidade (não dá para injetar mock/instância isolada por teste).


## Antes → depois (Java 25)

**Antes (indireção sem ganho):**

```java
public interface PedidoFactory { Pedido criar(String cliente, long centavos); }

public class PedidoFactoryImpl implements PedidoFactory {
    @Override
    public Pedido criar(String cliente, long centavos) { return new Pedido(cliente, centavos); }
}
```

**Depois:**

```java
// Record valida no construtor compacto; sem factory, sem interface com uma única implementação
public record Pedido(String cliente, long centavos) {
    public Pedido {
        if (centavos <= 0) throw new IllegalArgumentException("Valor deve ser positivo");
    }
}
// Uso: new Pedido("loja-42", 15000)
```

**Singleton manual → injeção:**

```java
// Antes: estado global, impossível trocar por mock em teste
public final class Config { private static final Config I = new Config(); public static Config getInstance() { return I; } }

// Depois: o container já garante uma instância; o teste injeta outra
@Component
public class Config { }
```
