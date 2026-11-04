Leia este arquivo quando for definir envelope de resposta, mapeamento de status HTTP, regras de sobrecarga/quotas, convenções de URL ou versionamento nativo do Boot 4.

## Convenções REST (aplicadas a este catálogo)

### Response Envelope

Todos os endpoints retornam um envelope consistente — opcional, mas útil quando a API é consumida por
múltiplos clientes que precisam de um ponto único de metadados (timestamp, errorCode). Em sucesso,
`data` é preenchido e `error` é `null`; em falha, o inverso — `error` traz `code`/`message`/`details`.

```json
{
  "success": false,
  "data": null,
  "error": { "code": "ORDER_NOT_FOUND", "message": "Order with id 123 not found", "details": [] },
  "timestamp": "2026-04-13T10:00:00Z"
}
```

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, List.of()), Instant.now());
    }
}

public record ApiError(String code, String message, List<String> details) {}
```

> **Alternativa:** Problem Details (RFC 9457) via `ProblemDetail` nativo do Boot 4.x — ver seção
> dedicada abaixo. Escolha **um** dos dois; misturar os dois causa inconsistência.

### HTTP Status Mapping

| Cenário | Status |
|---------|--------|
| GET — encontrado | 200 |
| POST — recurso criado | 201 |
| PUT/PATCH — atualizado | 200 |
| DELETE — deletado | 204 (sem corpo) |
| Falha de validação (formato) | 400 |
| Não autenticado | 401 |
| Não autorizado | 403 |
| Não encontrado | 404 |
| Conflito (duplicado, otimista) | 409 |
| Payload acima do limite | 413 |
| Regra de negócio violada | 422 |
| Quota/limite **do cliente** excedido | 429 + `Retry-After` |
| Erro técnico inesperado | 500 |
| Dependência respondeu erro/inválido (gateway) | 502 |
| Serviço **saturado** ou indisponível (load shedding, breaker aberto, manutenção) | 503 + `Retry-After` quando houver estimativa |
| Dependência não respondeu no prazo (gateway/proxy) | 504 |

### Sobrecarga, quotas e repetição

Distinga **quem** está em excesso — a resposta orienta o cliente de forma diferente:

| Situação | Status | O que o cliente deve fazer | Métrica |
|---|---|---|---|
| Este cliente/tenant excedeu a quota | 429 | Esperar `Retry-After`; não afeta outros clientes | rejeições por quota, por cliente (agregado) |
| O serviço está saturado (todos) | 503 | Backoff exponencial com jitter; respeitar `Retry-After` | rejeições por saturação; conta contra o SLO |
| Requisição repetida com mesma `Idempotency-Key` | mesmo status/corpo da 1ª | Nada: repetição segura | repetições detectadas |
| Mesma chave, payload diferente | 409 (ou 422, conforme convenção do projeto) | Corrigir o cliente | conflitos de chave |

- **Rejeite cedo**: 429/503 devem sair antes de alocar recursos caros (conexão de banco, chamada remota); a
  resposta de rejeição tem que ser barata (< poucos ms). Detalhes: `resiliencia-controle-fluxo-java`.
- **`Retry-After`** (segundos ou data HTTP) só quando houver estimativa útil; clientes do catálogo honram o
  header **dentro do deadline** e com jitter, nunca em loop imediato.
- **Identidade da quota** vem da autenticação (cliente/tenant do token), não de header livre como
  `X-Forwarded-For` — esse só é confiável quando definido pelo proxy de borda conhecido. Quota de aplicação não
  substitui proteção de borda contra DDoS.
- **Idempotência em POST** sujeito a repetição (pagamento, pedido): header `Idempotency-Key` obrigatório;
  persistir chave + escopo + hash do payload + resposta; a repetição devolve a resposta original. Implementação:
  `mensageria-sqs-kafka` → idempotência, outbox e replay.
- **Limites de custo da consulta**: tamanho máximo de página, filtros indexados, profundidade/complexidade
  (GraphQL), tamanho de payload (413) e tempo máximo de execução no banco.
- **Deadline**: aceite `Request-Timeout`/deadline propagado quando o contrato prever; não processe trabalho
  cujo prazo já venceu.

Exemplo de 503 com Problem Details (RFC 9457):

```java
// infrastructure/web — handler central; a rejeição por saturação vira 503 barato e observável.
@ExceptionHandler(AdmissaoPorPrioridade.Rejeitada.class)
ResponseEntity<ProblemDetail> saturado(AdmissaoPorPrioridade.Rejeitada rejeicao) {
    var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "Serviço temporariamente sem capacidade; tente novamente.");
    problema.setType(URI.create("https://api.exemplo.com/problemas/capacidade-esgotada"));
    problema.setProperty("motivo", rejeicao.motivo().name());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "2")
            .body(problema);
}
```

> **Validação vs regra de negócio:** 400 é **formato** errado (campo vazio, `email` mal-formado),
> sempre via Bean Validation (`@Valid`, ver `arquitetura-limpa-java` mapa de erros). 422 é
> **regra de negócio** violada (`BusinessException`) — formato ok, valor não faz sentido no domínio.

### Convenções de URL

- **Plural para recursos**: `/orders`, `/users`, `/products`.
- **Kebab-case** para multi-palavra: `/order-items`, nunca `/orderItems`.
- **Versionamento em path**: `/api/v1/orders` — preferir route nativo com API versioning (Spring
  Boot 4) a duplicar controllers por versão.
- **Recursos aninhados no máximo 2 níveis**: `/orders/{id}/items` ✅,
  `/orders/{id}/items/{itemId}/notes` ❌.
- **IDs como UUID** na URL, nunca inteiros auto-incremento (vazam volume e permitem enumeração).

### Versionamento de API (nativo no Boot 4)

Spring Boot 4 / Framework 7 roteia por versão nativamente — sem `@RequestMapping` com prefixo manual
por controller:

```yaml
spring:
  mvc:
    apiversion:
      use:
        path-segment: 1
      supported: [1.0, 1.1, 2.0]
      default: 1.0
```


## Exemplo antes/depois — status e URL

Antes: URL em camelCase, verbo no caminho e status sempre 200 com erro no corpo.

```java
// ERRADO: verbo na URL, camelCase, 200 mesmo quando falha
@PostMapping("/api/orderItems/createItem")
public ResponseEntity<Map<String, Object>> criar(@RequestBody Map<String, Object> corpo) {
    try {
        return ResponseEntity.ok(Map.of("success", true, "data", service.criar(corpo)));
    } catch (Exception e) {
        return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
    }
}
```

Depois: recurso no plural em kebab-case, `201` + `Location`, falhas tratadas pelo handler global
(400 formato, 422 regra de negócio).

```java
// CERTO: substantivo no plural, status semântico e Location do recurso criado
@PostMapping("/api/v1/order-items")
public ResponseEntity<ItemResponse> criar(@RequestBody @Valid CriarItemRequest request) {
    var criado = service.criar(request.paraComando());
    var local = URI.create("/api/v1/order-items/" + criado.id());
    return ResponseEntity.created(local).body(ItemResponse.de(criado));
}
```

Rejeição por saturação e quotas (429/503 com `Retry-After`, `Idempotency-Key`): ver
[idempotencia-quotas-http.md](idempotencia-quotas-http.md). Esqueleto de contrato com esses status:
`assets/openapi-base.yaml`. Código executável do mecanismo de admissão que gera o 503:
[`AdmissaoPorPrioridade`](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/AdmissaoPorPrioridade.java)
e seu [teste](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/AdmissaoPorPrioridadeTest.java).
