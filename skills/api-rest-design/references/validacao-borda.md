Leia este arquivo quando for validar entrada com Bean Validation e separar 400 (formato) de 422 (regra de negócio).

## Validação de borda — Bean Validation

Toda entrada do cliente passa por `@Valid` no DTO de request; o handler global traduz
`MethodArgumentNotValidException` em `ProblemDetail` 400. Anotações vão **no record de request**,
nunca na entidade JPA:

```java
public record CriarProdutoRequest(
    @NotBlank @Size(max = 200) String nome,
    @NotNull @DecimalMin(value = "0.01") BigDecimal preco,
    @NotNull @Min(0) Integer estoque
) {}

@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid CriarProdutoRequest request) {
    // 400 via @Valid se formato errado; 422 via BusinessException se regra falhar
    return ResponseEntity.status(201).body(mapper.paraResposta(service.criar(mapper.paraEntidade(request))));
}
```


## Exemplo antes/depois — validação no controller

Antes: validação manual espalhada e status errado.

```java
// ERRADO: if manual no controller, 500 quando o dado é inválido
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
    if (request.nome() == null || request.nome().isBlank()) {
        throw new IllegalStateException("nome vazio");
    }
    return ResponseEntity.status(201).body(service.criar(request));
}
```

Depois: o handler global traduz `MethodArgumentNotValidException` em `ProblemDetail` 400 com os campos.

```java
// CERTO: 400 com lista de erros por campo (RFC 9457 + extensão "errors")
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos");
    pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
            .toList());
    return ResponseEntity.badRequest().body(pd);
}
```
