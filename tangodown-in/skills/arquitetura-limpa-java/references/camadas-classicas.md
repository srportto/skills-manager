Leia este arquivo quando a aplicação **não** é hexagonal e usa camadas clássicas (`controller`/`service`/`repository`/`domain`/`dto`): workflow de arquitetura, responsabilidades por camada, DTOs, estrutura de pacotes e convenções de injeção de dependência.

## Workflow de arquitetura

1. **Análise** — revise estrutura do projeto, dependências, configuração Spring.
2. **Design de domínio** — modele seguindo DDD e Clean Architecture; resolva ambiguidades de
   fronteira **antes** de implementar.
3. **Implementação** — construa services com boas práticas de Spring Boot em camadas.
4. **Camada de dados** — otimize queries JPA, implemente repositories; rode `./mvnw verify -pl
   <modulo>`. Se falhar: reveja log SQL do Hibernate, ajuste queries/mappings, re-rodar.
5. **Segurança & config** — aplique Spring Security, externalize configuração; rode `./mvnw verify`
   para confirmar filter chain e JWT. Se falhar: cheque ordem do bean `SecurityFilterChain`.
6. **Quality assurance** — rode `./mvnw verify` (Maven) ou `./gradlew check` (Gradle), cobertura
   ≥ 85%. Se abaixo: identifique branches não testados no relatório JaCoCo
   (`target/site/jacoco/index.html`), adicione casos, re-rodar.

---

# Arquitetura em camadas (estilo "clássico" / não-hexagonal)

```
@RestController        ← HTTP only. Sem lógica de negócio. Sem entidades JPA na resposta.
      ↓ DTOs
@Service               ← Toda lógica de negócio vive aqui. Orquestra repositories.
      ↓ Domain objects / Entities
@Repository            ← Acesso a dados only. Sem lógica de negócio. Retorna entities/projections.
      ↓ JPA / JDBC
Database
```

> **Diferente do hexagonal:** este é o estilo em camadas (controller→service→repository), referência
> para times que não migraram para hexagonal. Para o padrão hexagonal clássico deste catálogo
> (`domain` com `port/in`/`port/out`, `application/usecase`, `infrastructure`), use
> `arquitetura-limpa-java`.

## Camada Controller

- Lida com HTTP: parsing, validação (`@Valid`), montagem de response.
- Chama **um** método de service por endpoint — sem orquestração no controller.
- **Nunca** retorna `@Entity` direto — sempre DTO de resposta.
- **Nunca** injeta `@Repository` — sempre via `@Service`.
- Tratamento de exceção via `@ControllerAdvice`, nunca `try/catch` no controller.

```java
// BOM
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    Order order = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
}

// RUIM - logica de negocio, acesso direto ao repo e entity na resposta
@PostMapping("/orders")
public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
    if (request.getItems().isEmpty()) throw new RuntimeException("No items");
    Order order = orderRepository.save(new Order(request));
    return ResponseEntity.ok(order);
}
```

## Camada Service e Repository

- **Service**: contém toda lógica de negócio, validação de regras e orquestração; `@Transactional`
  vive **aqui**. Injeção por construtor apenas — nunca `@Autowired` em field. Um service por
  aggregate root (`OrderService`, não `OrderAndPaymentService`). Retorna domain objects/DTOs —
  nunca `HttpServletRequest`/`HttpServletResponse`.
- **Repository**: estende `JpaRepository<Entity, ID>` ou `CrudRepository`; queries custom via
  `@Query` ou query derivation — sem SQL raw a menos que inevitável. Retorna entities ou
  projections — nunca `Object[]` raw. Sem lógica de negócio.

## DTOs

- **Request e Response separados** — nunca a mesma classe para os dois.
- Anotações de validação (`@NotNull`, `@Size`) **só nos Request DTOs**.
- Método factory estático `ResponseDto.from(Entity entity)` para mapeamento.
- **Use records** para DTOs imutáveis (Java 16+).

```java
// BOM
public record OrderResponse(UUID id, String status, List<LineItemResponse> items) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus().name(),
            order.getItems().stream().map(LineItemResponse::from).toList());
    }
}
```

---

# Estrutura de pacotes (estilo "clássico")

```
src/main/java/com/example/app/
  config/        ← @Configuration classes (beans, security, etc.)
  controller/    ← @RestController
  service/       ← @Service
  repository/    ← @Repository
  domain/        ← entidades, value objects, regras de domínio
  dto/           ← records de request/response
  util/          ← helpers, validators, mappers
src/main/resources/
  application.yml
src/test/java/... (espelha main)
```

> **Alternativa hexagonal clássica** (`domain` com `port/in`+`port/out` / `application/usecase` /
> `infrastructure`) é o padrão deste catálogo. Ver `arquitetura-limpa-java`.

---

# Injeção de dependência — convenções

Construtor com `final` (Lombok gera o construtor com todos os campos final), nunca `@Autowired`
em field — este último dificulta teste (exige reflection para mockar) e esconde dependências.

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
}
```
