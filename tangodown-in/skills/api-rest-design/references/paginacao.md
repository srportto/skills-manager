Leia este arquivo quando for definir payload de paginação, limite de página ou escolher entre offset e cursor.

## Paginação

### Padrão de payload

> **Formato recomendado (alinhado a `assets/openapi-base.yaml`, schema `OrderPage`):** página **plana**, sem
> envelope, com erros em Problem Details: `{ "content": [...], "page": 0, "size": 20, "totalElements": 150, "totalPages": 8, "last": false }`.
> O formato com envelope `{success, data}` abaixo é a opção **customizada/legada** do tradeoff "Envelope de
> resposta" (SKILL.md); use-o só se o projeto já adotou o envelope em todas as respostas. Não misture os dois.

```json
{ "success": true, "data": { "content": [...], "page": 0, "size": 20, "totalElements": 150, "totalPages": 8, "last": false } }
```

Query params: `?page=0&size=20&sort=createdAt,desc`

### Limite o tamanho da página

```yaml
spring:
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
```

### Quando usar cursor em vez de offset

| Caso | Use |
|---|---|
| UI com "próxima página" e "página anterior", dataset pequeno-médio | Offset (`page`/`size`) — simples, suporta saltar para página N |
| Feed infinito, dataset grande, alta concorrência de inserts | Cursor (`?cursor=<opaco>`) — estável quando itens são inseridos no meio da lista; offset fica inconsistente |
| Export de relatórios | Cursor — não há "fim" previsível |


## Exemplo antes/depois — limite de página e cursor

Antes: `size` livre permite `?size=1000000` e derruba o banco.

```java
// ERRADO: o cliente decide o tamanho da página
@GetMapping("/api/v1/orders")
public Page<OrderResponse> listar(@RequestParam int page, @RequestParam int size) {
    return service.listar(PageRequest.of(page, size)).map(OrderResponse::de);
}
```

Depois: `Pageable` com teto configurado (`max-page-size: 100`) e ordenação padrão.

```java
// CERTO: Pageable resolvido pelo Spring respeita default-page-size e max-page-size
@GetMapping("/api/v1/orders")
public Page<OrderResponse> listar(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return service.listar(pageable).map(OrderResponse::de);
}
```

Cursor (feed/export) com cursor opaco e chave estável (keyset), sem `OFFSET`:

```java
// cursor = Base64 de "createdAt|id" do último item entregue
public record PaginaCursor<T>(List<T> itens, String proximoCursor) {}
```

```sql
-- consulta keyset por trás do cursor: custo constante, estável sob inserts concorrentes
SELECT * FROM orders
WHERE (created_at, id) < (:criadoEm, :id)
ORDER BY created_at DESC, id DESC
LIMIT :limite;
```

Parâmetros `page`/`size`/`sort`/`cursor` já definidos em `assets/openapi-base.yaml`.
