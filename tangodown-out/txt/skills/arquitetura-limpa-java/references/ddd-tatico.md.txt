Leia este arquivo quando a dúvida deixar de ser "em qual camada" e passar a ser "como modelar o domínio": aggregate, value object, domain event, specification, anti-corruption layer e as armadilhas de modelagem.

## DDD tático — blocos de construção do domain/model

Quando o problema deixa de ser "em qual camada" e passa a ser **"como modelar o domínio"**, use os
blocos táticos do DDD dentro de `domain/model/` (Java puro, sem framework).

### Aggregate (agregado)

- Um repositório por **aggregate root**.
- Código externo só acessa o agregado pela **raiz** — nunca entidade filha diretamente.
- Agregados referenciam outros agregados **por ID**, não por referência de objeto.
- Mantenha agregados pequenos — mais de 3-4 entidades filhas, divida.

```java
// ✅ A raiz controla todo o acesso aos filhos
pedido.adicionarItem(produtoId, quantidade);
pedido.removerItem(itemId);

// ❌ Acesso direto ao filho de fora — viola invariantes
pedido.getItens().add(new ItemPedido(...));
```

### Value Object

Imutável, sem identidade, igualdade por valor. Use `record` (Java 16+).

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Valor não pode ser negativo");
        Objects.requireNonNull(currency);
    }

    public Money add(Money outro) {
        if (!currency.equals(outro.currency))
            throw new CurrencyMismatchException(currency, outro.currency);
        return new Money(amount.add(outro.amount), currency);
    }
}

public record Email(String valor) {
    public Email {
        if (!valor.matches("^[\\w.-]+@[\\w.-]+\\.[a-z]{2,}$"))
            throw new InvalidEmailException(valor);
    }
}
```

### Domain Events

Eventos são **records imutáveis** coletados no agregado e publicados **após o commit**.

```java
public record PedidoRealizado(PedidoId id, ClienteId clienteId, Money total, Instant ocorridoEm) {
    public static PedidoRealizado de(Pedido pedido) {
        return new PedidoRealizado(pedido.getId(), pedido.getClienteId(), pedido.getTotal(), Instant.now());
    }
}

// No agregado
@Entity
public class Pedido {
    @Transient
    private final List<Object> eventos = new ArrayList<>();

    public void realizar() {
        this.status = StatusPedido.REALIZADO;
        eventos.add(PedidoRealizado.de(this));
    }

    public List<Object> pullEventos() {
        var evts = List.copyOf(eventos);
        eventos.clear();
        return evts;
    }
}

// No use case — publica depois de salvar
@Transactional
public Pedido realizar(RealizarPedidoCommand cmd) {
    Pedido pedido = repository.findById(cmd.pedidoId()).orElseThrow();
    pedido.realizar();
    Pedido salvo = repository.save(pedido);
    salvo.pullEventos().forEach(publisher::publishEvent); // após commit
    return salvo;
}
```

> **Prefira `@DomainEvents` e `@AfterDomainEventPublication`** do Spring Data: exponha os métodos
> no agregado e o repositório publica automaticamente a cada `save()` — sem wiring manual.

### Specifications (queries complexas)

```java
public class PedidoSpecifications {
    public static Specification<Pedido> porStatus(StatusPedido status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Pedido> porCliente(UUID clienteId) {
        return (root, query, cb) -> cb.equal(root.get("clienteId"), clienteId);
    }
}

Specification<Pedido> spec = PedidoSpecifications.porStatus(REALIZADO)
    .and(PedidoSpecifications.porCliente(clienteId));
repository.findAll(spec, pageable);
```

### Anti-Corruption Layer (ACL)

Quando integrar com sistema externo ou legado, **não deixe o modelo dele vazar para o seu domínio**.
O ACL é o adapter que traduz o modelo alheio para o seu `domain/model`.

```java
@Component
@RequiredArgsConstructor
public class PagamentoGatewayAdapter implements PagamentoPort {

    private final PagamentoExternoClient client;  // SDK de terceiro

    @Override
    public ConfirmacaoPagamento cobrar(PedidoId pedidoId, Money valor) {
        // Traduz domínio → externo
        var request = new PagamentoApiRequest(
            pedidoId.valor().toString(),
            valor.amount().doubleValue(),
            valor.currency().getCurrencyCode());

        var response = client.cobrar(request);

        // Traduz externo → domínio
        return new ConfirmacaoPagamento(
            PagamentoId.de(response.getTransactionId()),
            response.isSucesso() ? StatusPagamento.CONFIRMADO : StatusPagamento.RECUSADO);
    }
}
```

### Armadilhas de modelagem

| # | Armadilha | Correção |
|---|---|---|
| 1 | Modelo anêmico (só getters/setters) | Comportamento vive no próprio objeto (`Pedido.adicionarItem()`) |
| 2 | `Long`/`String` para ID de entidade | Use value objects tipados (`PedidoId`, `ClienteId`) |
| 3 | Regra de negócio em service | Service orquestra; regra decide no agregado |
| 4 | Acesso a filho fora da raiz | Sempre pela aggregate root |
| 5 | Publicar evento antes do save | Publique após save/commit |
| 6 | Modelo externo vazando para o domínio | Use ACL para traduzir |
