# Variante: base pura / REST

Leia este arquivo quando o pedido for uma aplicação **REST sem banco, fila ou broker** (ou a base antes de
escolher uma variante). É a variante mais barata: o `assets/esqueleto` já a entrega pronta, e aqui está o que
acrescentar quando houver endpoints de negócio.

## O que adicionar sobre `assets/esqueleto`

| Item | O que fazer |
|---|---|
| Dependências | Nenhuma obrigatória. Para validar corpo de requisição: `spring-boot-starter-validation`. |
| Pacotes | `domain/port/in/<Caso>UseCase`, `application/usecase/<Caso>Service`, `infrastructure/web/<Recurso>Controller` + DTOs (`record`) no mesmo pacote `web`. |
| Config | Bean do caso de uso em `infrastructure/config/BeansConfig` (o esqueleto já mostra o padrão). |
| Erros | `ApiExceptionHandler` do esqueleto já mapeia `BusinessException` → 422, `ApplicationException` → 500 e validação de bean → 400 (Problem Details). Contrato de erro/paginação: `../../api-rest-design/SKILL.md`. |
| Limites | **Corpo:** o esqueleto já limita o tamanho de todo corpo, JSON incluso, com `LimiteCorpoRequisicaoFilter` (`app.http.limite-corpo: 1MB`; ajuste ao maior payload legítimo da API). Acima do limite, responde 413 em Problem Details antes do controller, com ou sem `Content-Length`. `server.tomcat.max-http-form-post-size` **não** limita JSON (só `x-www-form-urlencoded`), e `max-swallow-size` só controla o descarte de upload abortado. Não use nenhum dos dois como limite de payload. **Campos:** `@Size` em strings e listas (limita depois do parse, não o tamanho da requisição). **Paginação** com tamanho máximo. **Timeout** explícito em todo cliente HTTP gerado. |

## Componentes (da definição da skill)

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **base pura** | Só a base hexagonal, sem infra externa. | — |

## Proteções e provas

| Variante | Proteções obrigatórias | Prova mínima gerada |
|---|---|---|
| base pura / REST | Limite de payload efetivo para JSON (`app.http.limite-corpo`, filtro do esqueleto), paginação com tamanho máximo, timeouts em clientes | Teste de contrato (status/erro); 413 com e sem `Content-Length` ([LimiteCorpoRequisicaoTest](../assets/esqueleto/src/test/java/br/com/exemplo/esqueleto/LimiteCorpoRequisicaoTest.java)) |

## Antes / depois: controller fino atrás da porta de entrada

```java
// ANTES: controller conversa com JPA e devolve a entidade (acopla borda, banco e contrato)
@RestController
class PedidoController {
    private final PedidoJpaRepository repo;

    PedidoController(PedidoJpaRepository repo) { this.repo = repo; }

    @PostMapping("/pedidos")
    PedidoEntity criar(@RequestBody PedidoEntity pedido) { return repo.save(pedido); }
}
```

```java
// DEPOIS: DTO validado na borda, regra no caso de uso, resposta própria
public record CriarPedidoRequest(@NotBlank @Size(max = 80) String cliente, @Positive int quantidade) {}

public record PedidoResponse(String id, String cliente) {}

@RestController
class PedidoController {
    private final CriarPedidoUseCase useCase;   // port/in: o controller nunca vê o service concreto

    PedidoController(CriarPedidoUseCase useCase) { this.useCase = useCase; }

    @PostMapping("/pedidos")
    ResponseEntity<PedidoResponse> criar(@Valid @RequestBody CriarPedidoRequest req) {
        var pedido = useCase.criar(new CriarPedido(req.cliente(), req.quantidade()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new PedidoResponse(pedido.id(), pedido.cliente()));
    }
}
```

`@Valid` falho vira 400 em Problem Details pelo `ApiExceptionHandler` (herda de `ResponseEntityExceptionHandler`);
`BusinessException` lançada pelo caso de uso vira 422.

## Fontes únicas e exemplos executáveis

- Esqueleto buildável: [`assets/esqueleto`](../assets/esqueleto/pom.xml); teste de contexto, `/disponibilidade` e probes em
  [EsqueletoApplicationTest](../assets/esqueleto/src/test/java/br/com/exemplo/esqueleto/EsqueletoApplicationTest.java);
  contrato de erro em [ApiExceptionHandlerTest](../assets/esqueleto/src/test/java/br/com/exemplo/esqueleto/ApiExceptionHandlerTest.java);
  limite do corpo em [LimiteCorpoRequisicaoFilter](../assets/esqueleto/src/main/java/br/com/exemplo/esqueleto/infrastructure/web/LimiteCorpoRequisicaoFilter.java),
  provado por [LimiteCorpoRequisicaoTest](../assets/esqueleto/src/test/java/br/com/exemplo/esqueleto/LimiteCorpoRequisicaoTest.java).
- REST com admissão limitada (503 + `Retry-After`), Problem Details e probes em
  [CheckoutApplication](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java), provado por
  [CheckoutApplicationTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java).
- Camadas e fronteiras: [arquitetura-limpa-java](../../arquitetura-limpa-java/SKILL.md). Resiliência de borda:
  [resiliencia-controle-fluxo-java](../../resiliencia-controle-fluxo-java/SKILL.md).
