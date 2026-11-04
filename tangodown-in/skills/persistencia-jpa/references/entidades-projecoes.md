Leia este arquivo quando for modelar ou revisar `@Entity`, `JpaRepository`, DTOs de borda, idempotência por unique constraint ou `ddl-auto`, e ao decidir entre devolver entidade ou projeção.

## Convenções do projeto

- **Entidade JPA em `infrastructure/persistence/`** — nunca no `domain/`, que permanece livre de
  `jakarta.persistence.*`. Use Lombok `@Getter @Setter @NoArgsConstructor`, nunca `@Data` em entidade
  JPA (`@Data` gera `equals`/`hashCode` a partir de todos os campos, o que quebra com proxies do
  Hibernate e coleções lazy).
- **`JpaRepository` em `infrastructure/persistence/`**, package-private, sem implementação manual —
  quem o expõe para fora é o adapter que implementa a `port/out` (`PedidoRepository` do `domain`).
  O use case injeta a porta, nunca o `JpaRepository`. Camadas descritas em detalhe na skill
  `arquitetura-limpa-java`.
- **Idempotência persistente via unique constraint, na transação do efeito**: a restrição única (criada
  pela migration, não só por `@Column(unique = true)`) é quem arbitra duplicatas. `existsByIdPedido(...)`
  seguido de `save(...)` é **check-then-act**: duas instâncias passam pela checagem ao mesmo tempo e uma
  delas falha (ou duplica, se não houver restrição). Grave o registro de idempotência e o efeito na mesma
  transação e trate a violação:

  ```java
  // application — o caso de uso abre a transação; a restrição única decide quem venceu.
  @Transactional
  public PedidoId criar(CriarPedido comando) {
      try {
          idempotencia.saveAndFlush(new IdempotenciaEntity(comando.tenant(), comando.chave(), comando.hashPayload()));
          var pedido = pedidos.save(PedidoEntity.de(comando));
          outbox.save(OutboxEntity.pedidoCriado(pedido));
          return pedido.id();
      } catch (DataIntegrityViolationException duplicata) {
          // A transação atual está marcada para rollback: leia o resultado anterior em transação nova
          // (outro bean/método REQUIRES_NEW) e compare o hash do payload — diferente é conflito (422/409).
          throw new RequisicaoRepetida(comando.tenant(), comando.chave());
      }
  }
  ```

  Padrão completo, outbox e provas executáveis: `mensageria-sqs-kafka`
  ([idempotência, outbox e replay](../../mensageria-sqs-kafka/references/idempotencia-outbox-replay-java.md)).
- **DTO record nas bordas via MapStruct**: a entidade JPA nunca atravessa `infrastructure/web/`;
  `ProdutoMapper`
  (`@Mapper(componentModel = "spring")`) converte `Produto` para os records `CriarProdutoRequest`/
  `ProdutoResponse` definidos no controller.
- **`ddl-auto`**: `update` só em desenvolvimento (`application-fragmento.yaml` do overlay
  `rest-crud-banco`); em produção use `validate` — o schema é gerenciado por migration (Flyway/Liquibase),
  não pelo Hibernate.


## Antes/depois: entidade com `@Data` vazando pela borda

```java
// ANTES - @Data gera equals/hashCode por todos os campos (quebra com proxy/lazy) e a entidade sai no JSON
@Data
@Entity
public class Pedido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToMany(mappedBy = "pedido", fetch = FetchType.EAGER)
    private List<ItemPedido> itens;
}

@GetMapping("/pedidos/{id}")
Pedido buscar(@PathVariable Long id) { // contrato de API acoplado ao schema
    return repository.findById(id).orElseThrow();
}
```

```java
// DEPOIS - entidade enxuta em infrastructure/persistence + record na borda via mapper
@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
    private List<ItemPedido> itens;

    @Override
    public boolean equals(Object outro) { // igualdade só pelo id, segura com proxies
        return outro instanceof Pedido p && id != null && id.equals(p.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode(); // estável antes e depois do persist
    }
}

public record PedidoResponse(Long id, int totalItens) { }

@GetMapping("/pedidos/{id}")
PedidoResponse buscar(@PathVariable Long id) {
    return mapper.paraResponse(useCase.buscarPorId(id)); // o use case abre a transação e carrega o necessário
}
```

## Antes/depois: leitura de poucos campos

```java
// ANTES - findAll() devolve a entidade inteira só para listar nome e preço
List<Produto> todos = repository.findAll();
```

```java
// DEPOIS - projeção por record (o Spring Data usa o construtor) com paginação
public record ProdutoResumo(Long id, String nome, BigDecimal preco) { }

Page<ProdutoResumo> listar(Pageable pageable); // método declarado no JpaRepository<Produto, Long>
```

## Execução comprovada

Idempotência por unique constraint, provada contra PostgreSQL real com 16 conexões concorrentes:
[`ProcessadorIdempotenteExternoIT`](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java)
(implementação em [`ProcessadorIdempotente`](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java)).
