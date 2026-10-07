# Idempotência, quotas e sobrecarga no contrato HTTP

Leia este arquivo quando o contrato precisar de repetição segura (`Idempotency-Key`), limite por cliente
(429 + `Retry-After`), rejeição por saturação (503) ou propagação de deadline. Aqui fica a **forma HTTP**
(headers, status, corpo); a **implementação** do limiter, do breaker e do cache não é copiada: veja
[timeouts-retries-java](../../resiliencia-controle-fluxo-java/references/timeouts-retries-java.md),
[capacidade-e-limites](../../resiliencia-controle-fluxo-java/references/capacidade-e-limites.md) e
[cache-protecao-java](../../spring-data-redis/references/cache-protecao-java.md) (rate limiting em Redis).

## Idempotency-Key

Use em `POST` (e `PATCH` não idempotente) sujeito a repetição: pagamento, pedido, transferência.
`PUT` e `DELETE` já são idempotentes pelo método.

1. O cliente gera um UUID por **intenção** de negócio e envia `Idempotency-Key: <uuid>`; repete a mesma chave em retry.
2. O servidor persiste, na mesma transação do efeito: `chave + escopo (cliente/tenant) + hash do payload + status + corpo da resposta`.
3. Repetição com mesma chave e mesmo hash devolve **a resposta original** (mesmo status e corpo), sem reexecutar.
4. Chave ainda em processamento (1ª requisição rodando) devolve `409` com `Retry-After` curto; nunca executa duas vezes.
5. TTL da chave maior que a janela máxima de retry do cliente (ex.: 24 h).

### Mesma chave, payload diferente: 422 ou 409?

| Opção | Quando escolher | Justificativa |
|---|---|---|
| `422 Unprocessable Content` | Padrão sugerido: a requisição é bem formada, mas a chave já pertence a outro payload | É erro do cliente (reuso indevido da chave), não estado do recurso; alinhado ao rascunho IETF do header `Idempotency-Key` |
| `409 Conflict` | O projeto já usa 409 para "chave em uso" e quer um único status de conflito | Conflito com o estado atual do servidor; aceitável se documentado no OpenAPI |

Escolha **uma** das duas e documente no contrato. O que nunca pode acontecer: reexecutar ou devolver a resposta
antiga para um payload diferente. Reserve `409` (com `Retry-After`) para "mesma chave ainda em processamento".

## 429 e Retry-After

- `429 Too Many Requests` quando **este** cliente/tenant excedeu a quota; a identidade vem do token autenticado, não de header livre.
- `Retry-After` em segundos (inteiro) ou data HTTP; só quando houver estimativa útil.
- Headers opcionais de transparência: `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` (rascunho IETF).
- Rejeite **antes** de alocar conexão de banco ou chamada remota; a resposta de rejeição deve ser barata.

## 503 por sobrecarga ou admissão rejeitada

- `503 Service Unavailable` quando o **serviço** está saturado (load shedding, bulkhead cheio, breaker aberto): afeta todos, não é culpa do cliente.
- Inclua `Retry-After` quando houver estimativa; o cliente faz backoff exponencial com jitter.
- Conta contra o SLO de disponibilidade; 429 não conta (é comportamento esperado do contrato).

## Deadline propagado

- Aceite o prazo do chamador quando o contrato prever (ex.: header `Request-Timeout`) e propague o **restante** do orçamento às chamadas a jusante.
- Se o prazo já venceu ao chegar, não processe: responda `504` (gateway/proxy) ou `408` (servidor) conforme o papel.
- O cliente que recebe `Retry-After` só retenta **dentro** do deadline restante.

## Exemplo Java 25 / Spring Boot 4

Antes: sem chave, sem `Retry-After`, status genérico.

```java
// ERRADO: reexecuta o pagamento a cada retry e devolve 500 quando o limite estoura
@PostMapping("/api/v1/payments")
public PagamentoResponse pagar(@RequestBody @Valid PagarRequest request) {
    return PagamentoResponse.de(service.pagar(request));
}
```

Depois: chave obrigatória, repetição segura, 429/503 com `Retry-After` e `ProblemDetail`.

```java
@RestController
@RequestMapping("/api/v1/payments")
class PagamentoController {

    private final PagamentoService service;
    private final RegistroIdempotencia registro;   // porta ilustrativa: persiste chave+hash+resposta

    PagamentoController(PagamentoService service, RegistroIdempotencia registro) {
        this.service = service;
        this.registro = registro;
    }

    @PostMapping
    ResponseEntity<?> pagar(@RequestHeader("Idempotency-Key") String chave,
                            @RequestBody @Valid PagarRequest request) {
        return switch (registro.executarUmaVez(chave, request.hash(), () -> service.pagar(request))) {
            case Resultado.Novo n ->
                ResponseEntity.status(HttpStatus.CREATED).body(n.resposta());
            case Resultado.Repetido r ->
                ResponseEntity.status(r.statusOriginal()).body(r.resposta());   // mesma resposta da 1ª
            case Resultado.PayloadDiferente d ->
                ResponseEntity.unprocessableEntity().body(problema(HttpStatus.UNPROCESSABLE_ENTITY,
                    "idempotency-key-reutilizada", "A chave já foi usada com outro payload."));
            case Resultado.EmProcessamento p ->
                ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, "1")
                    .body(problema(HttpStatus.CONFLICT, "requisicao-em-andamento", "Aguarde e repita."));
        };
    }

    // 429: a quota do cliente estourou (o limiter em si vem de resiliencia-controle-fluxo-java)
    @ExceptionHandler(QuotaExcedida.class)
    ResponseEntity<ProblemDetail> quota(QuotaExcedida e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.segundosAteLiberar()))
            .body(problema(HttpStatus.TOO_MANY_REQUESTS, "quota-excedida", "Limite de requisições excedido."));
    }

    // 503: serviço saturado; rejeição barata antes de tocar banco ou dependência
    @ExceptionHandler(AdmissaoRejeitada.class)
    ResponseEntity<ProblemDetail> saturado(AdmissaoRejeitada e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "2")
            .body(problema(HttpStatus.SERVICE_UNAVAILABLE, "capacidade-esgotada", "Sem capacidade; tente novamente."));
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String detalhe) {
        var pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setType(URI.create("https://api.example.com/errors/" + tipo));
        return pd;
    }
}

// Resultado selado devolvido pela porta (ilustrativo; adapte aos tipos do seu projeto)
sealed interface Resultado {
    record Novo(PagamentoResponse resposta) implements Resultado {}
    record Repetido(int statusOriginal, PagamentoResponse resposta) implements Resultado {}
    record PayloadDiferente() implements Resultado {}
    record EmProcessamento() implements Resultado {}
}
```

`RegistroIdempotencia`, `QuotaExcedida` e `AdmissaoRejeitada` são portas/exceções ilustrativas do seu projeto
(a persistência da chave segue a idempotência de `mensageria-sqs-kafka`). Mecanismos executáveis:
[`ProcessadorIdempotente`](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java),
[`TokenBucket`](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/TokenBucket.java),
[`AdmissaoPorPrioridade`](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/AdmissaoPorPrioridade.java) e
[`ChamadaComDeadline`](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/ChamadaComDeadline.java).

Contrato correspondente (respostas 429/503 com `Retry-After`, parâmetro `Idempotency-Key`):
`assets/openapi-base.yaml`; corpo de erro de exemplo: `assets/problem-details.json`.
