# Abuso de recursos e limites (OWASP API4:2023 — Unrestricted Resource Consumption)

Leia este arquivo quando definir limites de payload, paginação, custo de consulta, rate limit ou quota por cliente/tenant — o foco aqui é identidade confiável e autorização; a implementação do limitador fica em outras skills (ver o fim do arquivo).

Disponibilidade também é segurança: um cliente (ou atacante) que consome recursos sem limite derruba o serviço
para todos.

| Vetor | Controle na aplicação Java |
|---|---|
| Payload gigante | Limite do **corpo** no servidor → 413: para JSON, filtro que recusa `Content-Length` acima do limite e conta os bytes do corpo sem tamanho declarado (o Tomcat do Spring Boot 4 não tem propriedade para isso); para upload, `spring.servlet.multipart.*`; listas/strings com Bean Validation (`@Size`) depois do parse. `server.tomcat.max-http-form-post-size` vale **só** para formulário |
| Paginação abusiva | Tamanho máximo de página no servidor (não confiar no `size` do cliente); cursor para datasets grandes |
| Consulta cara | Filtros só em colunas indexadas, timeout de consulta, limite de profundidade/complexidade (GraphQL), proibir `LIKE '%x%'` sem índice próprio em endpoint público |
| Quota por cliente/tenant | Identidade **autenticada** (sub/tenant do token) como chave; 429 + `Retry-After`; quota distribuída atômica entre réplicas (`spring-data-redis`) |
| Bypass por header | `X-Forwarded-For`/`X-Real-IP` só confiáveis quando escritos por proxy conhecido (`server.forward-headers-strategy` + lista de proxies confiáveis); senão o atacante troca de "IP" a cada requisição |
| Cardinalidade de chaves | Mapas de quota/locks por identidade com tamanho máximo (LRU), para não virarem vetor de exaustão de memória |
| Limitador indisponível | Política explícita (limite local conservador), nem fail-open total nem fail-closed total |
| Operações caras (exportação, relatório) | Bulkhead/pool separado e fila limitada; trabalho assíncrono com 202 |

Rate limit de aplicação **não substitui** proteção volumétrica de borda (WAF/CDN/provedor contra DDoS): quando
a requisição chega à JVM, a banda e as conexões já foram gastas. Mecanismos: `resiliencia-controle-fluxo-java`;
contrato 413/429/503: `api-rest-design`.

## Exemplo antes/depois — tamanho de página e chave de quota

```java
// ANTES - confia no size do cliente e na identidade vinda de header livre
@GetMapping("/pedidos")
Page<PedidoResponse> listar(@RequestParam int size,
                            @RequestHeader("X-Client-Id") String clienteId) {   // forjavel
    limitador.tentar(clienteId);
    return service.listar(PageRequest.of(0, size));    // size=1000000 derruba o banco
}

// DEPOIS - teto de pagina no servidor e chave de quota extraida do token autenticado
private static final int PAGINA_MAXIMA = 100;

@GetMapping("/pedidos")
Page<PedidoResponse> listar(@RequestParam(defaultValue = "20") @Min(1) int size,
                            @AuthenticationPrincipal Jwt jwt) {
    String chave = jwt.getSubject();                    // identidade confiavel (assinada)
    if (!limitador.tentar(chave)) {
        throw new LimiteExcedidoException(chave);       // mapeada para 429 + Retry-After
    }
    return service.listar(PageRequest.of(0, Math.min(size, PAGINA_MAXIMA)));
}
```

Limite de payload por tipo de corpo (Spring Boot 4, Tomcat):

| Corpo | Mecanismo que limita de fato | O que **não** limita |
|---|---|---|
| JSON/XML (`@RequestBody`) | Filtro `LimiteCorpoRequisicaoFilter` do esqueleto `criar-aplicacao-java`, configurado em `app.http.limite-corpo`. Recusa `Content-Length` acima do limite e conta o corpo chunked → 413, inclusive quando o limite estoura no meio de um valor JSON. Ou limite equivalente no gateway/ingress, **além** do filtro | `server.tomcat.max-http-form-post-size` (só `x-www-form-urlencoded`), `server.tomcat.max-swallow-size` (só o descarte de upload abortado), `@Size` (atua depois do parse) |
| Formulário `x-www-form-urlencoded` | `server.tomcat.max-http-form-post-size` | — |
| Multipart (upload) | `spring.servlet.multipart.max-file-size` / `max-request-size`. O filtro **ignora** `multipart/*`, então o upload pode ser maior que o limite de JSON | `app.http.limite-corpo` (não se aplica a multipart) |

```yaml
server:
  tomcat:
    max-http-form-post-size: 1MB   # só formulário
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB
app:
  http:
    limite-corpo: 1MB              # JSON: LimiteCorpoRequisicaoFilter → 413 (multipart usa spring.servlet.multipart.*)
```

Implementação e prova (fonte única):
[LimiteCorpoRequisicaoFilter](../../criar-aplicacao-java/assets/esqueleto/src/main/java/br/com/exemplo/esqueleto/infrastructure/web/LimiteCorpoRequisicaoFilter.java)
e [LimiteCorpoRequisicaoTest](../../criar-aplicacao-java/assets/esqueleto/src/test/java/br/com/exemplo/esqueleto/LimiteCorpoRequisicaoTest.java).
O teste cobre `Content-Length` acima do limite, corpo chunked acima do limite e corpo dentro do limite com e sem
tamanho declarado. Antes de afirmar que uma propriedade de servidor limita o corpo, confira a descrição dela nos
metadados do Spring Boot (`spring-configuration-metadata.json`).

## Onde implementar o limitador

Esta reference trata de **quem** é o cliente e **o que** ele pode consumir. A mecânica do
limitador vive em outras skills (uma fonte de verdade):

- Algoritmos, admissão, bulkhead, filas limitadas: [`resiliencia-controle-fluxo-java`](../../resiliencia-controle-fluxo-java/SKILL.md).
- Quota distribuída atômica entre réplicas (Redis/Valkey): [`spring-data-redis/references/cache-protecao-java.md`](../../spring-data-redis/references/cache-protecao-java.md).
- Código executável: `examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/TokenBucket.java` (teste `TokenBucketTest.java`) e `examples/java/integracao/src/main/java/br/com/srportto/exemplos/LimiteDistribuido.java` (teste `LimiteDistribuidoTest.java`).
