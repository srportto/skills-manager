# Checklist 2 — Contrato HTTP (status por origem do erro, DTOs de borda)

Leia este arquivo ao revisar controller, handler de erros ou DTO de request/response. Para desenho completo de contrato (Problem Details, paginação, versionamento) use `api-rest-design`.

### 2. Contrato HTTP

Status correto por origem do erro (400 validação / 422 negócio / 500 técnico), e DTOs de borda
imutáveis:

**[❌ Código Não Aderente]:**
```java
// regra de negocio violada devolvendo excecao generica, que o handler mapeia como 500
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
    if (request.preco().signum() <= 0) {
        throw new RuntimeException("preco invalido"); // handler generico -> 500, deveria ser 422
    }
    Produto criado = service.criar(request);
    return ResponseEntity.ok(mapper.paraResposta(criado));
}
```

**[🚨 Violação e Explicação]:** validação de regra de negócio devolvendo `RuntimeException`
genérica faz o handler central mapear como 500 (erro técnico), quando o correto é 422
(recurso não processável por regra de negócio). Cliente recebe diagnóstico errado.

**[✅ Exemplo de Refatoração]:**
```java
// 400 (formato) via @Valid no record de request, 422 (negocio) via BusinessException
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid CriarProdutoRequest request) {
    // @NotNull/@DecimalMin no record cobrem o 400 (falha de validacao de formato)
    Produto criado = service.criar(request); // service delega a produto.validar(), que lanca
                                              // BusinessException (422) se a regra de negocio falhar
    return ResponseEntity.created(URI.create("/produtos/" + criado.getId()))
            .body(mapper.paraResposta(criado));
}
```

**[❌ Código Não Aderente]:**
```java
// DTO mutavel com setters: o contrato de borda pode ser alterado apos a criacao
public class ProdutoResponse {
    private Long id;
    private String nome;
    public void setId(Long id) { this.id = id; }
    public void setNome(String nome) { this.nome = nome; }
}
```

**[🚨 Violação e Explicação]:** DTO de borda com setters públicos permite que o chamador altere
o contrato de saída após a construção; o JSON serializado deixa de refletir o estado do recurso.

**[✅ Exemplo de Refatoração]:**
```java
// record imutavel: contrato de borda fixado na construcao, sem setters
public record ProdutoResponse(Long id, String nome, BigDecimal preco) {}
```


## Exemplo adicional — erro de negócio como 422 (antes/depois)

**[❌ Código Não Aderente]:**
```java
// handler generico transforma qualquer excecao em 500 e vaza a mensagem interna
@ExceptionHandler(Exception.class)
public ResponseEntity<String> tratar(Exception e) {
    return ResponseEntity.status(500).body(e.getMessage());
}
```

**[✅ Exemplo de Refatoração]:**
```java
// negocio -> 422, formato -> 400, tecnico -> 500, sempre com ProblemDetail (RFC 9457)
@ExceptionHandler(BusinessException.class)
public ProblemDetail tratarNegocio(BusinessException e) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    problema.setTitle("Regra de negocio violada");
    return problema;
}
```
