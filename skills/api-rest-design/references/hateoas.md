Leia este arquivo quando precisar decidir se a API deve expor links hypermedia e como modelá-los.

## HATEOAS (hypermedia)

Use quando a API precisa ser descobrível — clientes navegam pelos relacionamentos via links
incluídos nas respostas, sem hardcode de URL.

```json
{
  "id": "550e8400-...",
  "status": "PENDING",
  "customerId": "abc-123",
  "_links": {
    "self":    { "href": "/api/v1/orders/550e8400-..." },
    "approve": { "href": "/api/v1/orders/550e8400-.../approve" },
    "items":   { "href": "/api/v1/orders/550e8400-.../items" }
  }
}
```

**Quando usar:** APIs públicas com clientes de longa duração (mobile, parceiros B2B). **Quando
evitar:** APIs internas entre microsserviços, CRUD simples, integrações máquina-a-máquina — preferem
contrato explícito e estável.


## Exemplo antes/depois — links em Java 25

Antes: o cliente monta URLs por conta própria (acoplamento).

```java
// ERRADO: cliente precisa conhecer o padrão de URL de cada transição
public record PedidoResponse(UUID id, String status) {}
```

Depois: o record carrega os links relevantes ao estado atual (só expõe `approve` se `PENDING`).

```java
// CERTO: links calculados a partir do estado; record puro, sem preview
public record PedidoResponse(UUID id, String status, Map<String, Link> _links) {
    public record Link(String href) {}

    public static PedidoResponse de(UUID id, String status) {
        var base = "/api/v1/orders/" + id;
        var links = new LinkedHashMap<String, Link>();
        links.put("self", new Link(base));
        links.put("items", new Link(base + "/items"));
        if ("PENDING".equals(status)) {
            links.put("approve", new Link(base + "/approve"));
        }
        return new PedidoResponse(id, status, Map.copyOf(links));
    }
}
```
