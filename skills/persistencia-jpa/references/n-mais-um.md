Leia este arquivo quando uma listagem dispara muitas queries, quando houver `LazyInitializationException`, ao decidir entre `JOIN FETCH`, `@EntityGraph` e projeção, ou ao revisar `FetchType.EAGER`, `findAll()` sem paginação e `open-in-view`.

## N+1 em detalhe

> O problema de performance mais comum em JPA/Hibernate.

```java
// infrastructure/persistence/Pedido.java
@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
    private List<ItemPedido> itens;
}
```

```java
// ERRADO - N+1: 1 query para buscar os pedidos + 1 query por pedido para buscar os itens
List<Pedido> pedidos = pedidoRepository.findAll();   // 1 query
for (Pedido pedido : pedidos) {
    pedido.getItens().size();                        // 1 query POR pedido (lazy)
}
// 50 pedidos = 51 queries
```

Para confirmar a suspeita, habilite `hibernate.SQL: DEBUG` (ou `show-sql: true`) e conte as queries no
log.

**Solução 1 — `JOIN FETCH` (JPQL)**: uma única query traz pedidos e itens juntos. Use quando a
associação sempre é necessária para o caso de uso da consulta.

```java
// infrastructure/persistence/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("SELECT p FROM Pedido p JOIN FETCH p.itens")
    List<Pedido> buscarTodosComItens();
}
```

**Solução 2 — `@EntityGraph`**: reaproveita o método padrão do `JpaRepository` (`findAll`) sem escrever
JPQL. Prefira quando a mesma query base precisa às vezes carregar a associação e às vezes não (múltiplos
métodos `@EntityGraph` sobre o mesmo `findById`, por exemplo).

```java
// infrastructure/persistence/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findAll();
}
```


## Erros comuns

| Anti-padrão | Por que é errado | Correção |
|---|---|---|
| `FetchType.EAGER` em coleção | Carrega TODOS os itens em TODA consulta da entidade dona, mesmo quando não precisa | `FetchType.LAZY` por padrão; carregar explicitamente via `JOIN FETCH`/`@EntityGraph` quando o caso de uso exigir |
| `findAll()` sem paginação | Carrega a tabela inteira em memória; piora a cada registro novo | `Pageable` — `JpaRepository` já oferece `findAll(Pageable)` de graça |
| Entidade `@Entity` retornada pelo controller | Serializar a entidade acopla o contrato de API ao schema do banco | Mapper converte para DTO record antes de sair pela borda |

```java
// ERRADO - EAGER em colecao carrega TODOS os itens em TODA consulta de Pedido, mesmo quando nao precisa
@OneToMany(mappedBy = "pedido", fetch = FetchType.EAGER)
private List<ItemPedido> itens;

// CORRETO - LAZY por padrao; carrega a colecao explicitamente so quando o caso de uso precisa
@OneToMany(mappedBy = "pedido", fetch = FetchType.LAZY)
private List<ItemPedido> itens;
```

### `open-in-view` ligado

Por padrão, o Spring Boot mantém a sessão do Hibernate aberta durante toda a requisição HTTP
(`spring.jpa.open-in-view: true` é o default). Isso evita `LazyInitializationException` de forma
implícita, mas esconde o problema: a query real dispara durante a serialização da resposta, fora de
qualquer `@Transactional` visível, e prende a conexão de banco pelo tempo inteiro da requisição
(inclusive chamadas HTTP externas feitas depois). Recomendação:

```yaml
spring:
  jpa:
    open-in-view: false
```

Com `open-in-view: false`, qualquer acesso lazy fora da transação falha explicitamente com
`LazyInitializationException` no lugar certo (o service), forçando a resolver com `JOIN FETCH`,
`@EntityGraph` ou projeção DTO — nunca reabrindo a sessão.

## Antes/depois: projeção DTO em vez de entidade + coleção lazy

Quando a listagem só precisa de alguns campos, nem `JOIN FETCH` é necessário: leia direto um record.
A projeção evita carregar a entidade (e o dirty checking) e não depende de sessão aberta.

```java
// ANTES - carrega a entidade inteira e navega a coleção lazy só para contar itens (N+1 + memória)
List<Pedido> pedidos = pedidoRepository.findAll();
List<String> linhas = pedidos.stream()
        .map(p -> p.getId() + ": " + p.getItens().size() + " itens")
        .toList();
```

```java
// DEPOIS - record de projeção + JPQL com agregação: 1 query, só as colunas necessárias
// infrastructure/persistence/PedidoResumo.java
public record PedidoResumo(Long id, Long totalItens) { }

// infrastructure/persistence/PedidoRepository.java
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("""
            select new br.com.srportto.pedidos.infrastructure.persistence.PedidoResumo(p.id, count(i))
            from Pedido p left join p.itens i
            group by p.id
            """)
    Page<PedidoResumo> resumir(Pageable pageable);
}
```

> Atenção: `JOIN FETCH` de **coleção** combinado com `Pageable` faz o Hibernate paginar **em memória**
> (aviso `HHH90003004` no log). Para página de entidades com coleção, pagine os ids primeiro e busque os
> itens num segundo passo, ou use projeção como acima.

## Antes/depois: configuração para enxergar o N+1 e reduzir viagens

```yaml
# ANTES - sem visibilidade: o N+1 só aparece em produção, como latência
spring:
  jpa:
    open-in-view: true
```

```yaml
# DEPOIS - queries visíveis em desenvolvimento/teste e carregamento em lote como rede de segurança
spring:
  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_batch_fetch_size: 50   # associações lazy carregadas em lotes de 50 ids (IN), não uma a uma
logging:
  level:
    org.hibernate.SQL: DEBUG           # apenas fora de produção
```

`default_batch_fetch_size` não substitui `JOIN FETCH`/projeção — só reduz o custo quando o acesso lazy é
inevitável (51 queries viram ~2). Para provar a regra em teste, conte as queries (por exemplo com
`Statistics#getPrepareStatementCount` do Hibernate) e falhe se passar do esperado.
