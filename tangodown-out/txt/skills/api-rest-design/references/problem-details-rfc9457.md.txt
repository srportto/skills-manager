Leia este arquivo quando for padronizar o payload de erro com RFC 9457 e o handler global de ProblemDetail.

## Problem Details — RFC 9457

Spring Boot 4.x tem suporte nativo a RFC 9457 via `ProblemDetail` — é o padrão IETF recomendado
para o payload de erro, em vez de um envelope customizado. Ative com `spring.mvc.problemdetails.enabled: true`
no `application.yaml`. Shape da resposta:

```json
{
  "type": "https://api.example.com/errors/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "No order found with id: 550e8400-e29b-41d4-a716-446655440000",
  "instance": "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000",
  "errorCode": "ORDER_NOT_FOUND",
  "timestamp": "2026-04-13T10:00:00Z"
}
```

Handler global:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://api.example.com/errors/" + ex.getCode()));
        pd.setTitle("Business rule violation");
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("errorCode", ex.getCode());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
```

`BusinessException`, `ApplicationException` e `@Valid` (Bean Validation) são todos tratados no mesmo
handler central (`ApiExceptionHandler`, em `infrastructure/web/`). Veja o mapa completo em
`arquitetura-limpa-java` (seção "Mapa de
erros e onde lançar").


## Exemplo antes/depois — erro padronizado

Antes: cada handler inventa um formato.

```java
// ERRADO: formato próprio, sem type/instance, status 500 para regra de negócio
@ExceptionHandler(BusinessException.class)
public ResponseEntity<String> tratar(BusinessException ex) {
    return ResponseEntity.status(500).body("erro: " + ex.getMessage());
}
```

Depois: `ProblemDetail` com `type`, `title`, `status`, `detail`, `instance` e extensões. Corpo de exemplo
pronto em `assets/problem-details.json`; o schema `ProblemDetail` está em `assets/openapi-base.yaml`.

```java
// CERTO: 422 para regra de negócio; o media type application/problem+json é aplicado pelo Spring
@ExceptionHandler(BusinessException.class)
public ProblemDetail tratar(BusinessException ex, HttpServletRequest req) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    pd.setType(URI.create("https://api.example.com/errors/" + ex.getCode()));
    pd.setTitle("Business rule violation");
    pd.setInstance(URI.create(req.getRequestURI()));
    pd.setProperty("errorCode", ex.getCode());
    return pd;
}
```

Para 429/503 com `Retry-After`, ver [idempotencia-quotas-http.md](idempotencia-quotas-http.md).
