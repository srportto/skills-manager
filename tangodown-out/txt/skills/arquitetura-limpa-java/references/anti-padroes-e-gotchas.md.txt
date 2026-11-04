Leia este arquivo ao revisar uma classe ou PR em app hexagonal e suspeitar de violação de fronteira: traz a tabela de anti-padrões com correção, o par ERRADO/CORRETO e os gotchas recorrentes de agents (JPA no domínio, `@Transactional` fora do lugar, `@MockBean` no Boot 4).

## Anti-padrões

| # | Anti-padrão | Por que é errado | Correção |
|---|---|---|---|
| 1 | Use case injetando `JpaRepository` direto | `application` passa a depender de Spring Data; o domínio deixa de ditar o contrato | Injete a `port/out`; o `JpaRepository` fica escondido dentro do adapter |
| 2 | Entidade JPA usada como modelo de domínio | `@Entity` + setters gerados = domínio anêmico acoplado ao schema do banco | `domain/model/` puro + `*JpaEntity` no adapter + mapper entre os dois |
| 3 | Chamada HTTP (`RestClient`) dentro do use case | Detalhe de infraestrutura vazando para `application` | Declare uma `port/out` e implemente em `infrastructure/external/` |
| 4 | Lógica de negócio no controller | Regra vaza para o adapter, fica não-reutilizável e só testável via HTTP | Regra no agregado (`Pedido.adicionarItem()`); controller só traduz DTO ⇄ command |
| 5 | Entidade JPA retornada como resposta HTTP | Acopla contrato REST ao schema e expõe campo interno | DTO próprio de `infrastructure/web/` |
| 6 | Domínio anotado com `@Component`/`@Service`/`@Entity` | Domínio passa a depender do container/ORM e perde o teste isolado | Domínio sem nenhuma anotação de framework |
| 7 | Service com parâmetro `HttpServletRequest` | `application` depende de `jakarta.servlet.*` | Controller extrai o dado (`@RequestHeader`) e passa tipo simples no command |

```java
// ERRADO - infraestrutura vazando para o use case e dominio anemico
@Service
public class CriarPedidoService {
    private final PedidoJpaRepository repo;      // JPA direto, sem porta
    private final RestClient restClient;         // HTTP dentro da application

    public PedidoJpaEntity criar(CriarPedidoRequest req) {
        PedidoJpaEntity p = new PedidoJpaEntity();
        p.setStatus("PENDENTE");                 // regra fora do dominio, status como String
        restClient.post().uri("/reservar").body(req.itens()).retrieve();
        return repo.save(p);                     // devolve entidade JPA para a borda
    }
}

// CORRETO - ver "Exemplo minimo" acima: use case fala so com port/in e port/out
```

## Gotchas comuns

- Agent importa `jakarta.persistence` em `domain/` — a entidade JPA pertence a
  `infrastructure/persistence/`.
- Agent injeta `JpaRepository` no use case — use a `port/out`.
- Agent põe `@Transactional` em `domain/service` — pertence a `application/usecase`.
- Agent confunde os dois lados: `port/in` = o que a aplicação **oferece**, `port/out` = o que ela
  **precisa**.
- Agent cria domínio anêmico só com getters/setters — comportamento vive nos próprios objetos.
- Agent expõe o `SpringDataXRepository` fora de `infrastructure/persistence/` — mantenha
  package-private.
- Agent usa `@MockBean` em teste — removido no Boot 4; use `@MockitoBean`.
- Agent usa `spring-boot-starter-aop` — renomeado para `spring-boot-starter-aspectj` no Boot 4.
