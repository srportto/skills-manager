# Cache (@Cacheable, cache-aside e stampede)

Leia este arquivo quando for implementar cache declarativo ou manual, decidir invalidação/TTL ou proteger chave quente contra stampede.

## Cache declarativo (@Cacheable)

```java
@Service
@RequiredArgsConstructor
public class ProdutoService {

    @Cacheable(value = "produtos", key = "#id")
    public ProdutoResponse buscarPorId(UUID id) {
        return produtoRepository.findById(id)
            .map(ProdutoResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
    }

    @CachePut(value = "produtos", key = "#result.id")
    @Transactional
    public ProdutoResponse atualizar(UUID id, AtualizarProdutoRequest request) {
        Produto produto = produtoRepository.findById(id).orElseThrow();
        produto.atualizar(request);
        return ProdutoResponse.from(produtoRepository.save(produto));
    }

    @CacheEvict(value = "produtos", key = "#id")
    @Transactional
    public void deletar(UUID id) {
        produtoRepository.deleteById(id);
    }

    @CacheEvict(value = "produtos", allEntries = true)
    public void limparCache() {}
}
```

## Cache-aside manual

```java
@Service
@RequiredArgsConstructor
public class PedidoCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JsonMapper jsonMapper; // Jackson 3 — Boot auto-configura um bean JsonMapper
    private static final Duration TTL = Duration.ofMinutes(5);

    public Optional<PedidoResponse> obter(UUID pedidoId) {
        String chave = "pedidos:pedido:" + pedidoId;
        Object cacheado = redisTemplate.opsForValue().get(chave);
        if (cacheado == null) return Optional.empty();
        return Optional.of(jsonMapper.convertValue(cacheado, PedidoResponse.class));
    }

    public void salvar(PedidoResponse pedido) {
        String chave = "pedidos:pedido:" + pedido.id();
        redisTemplate.opsForValue().set(chave, pedido, TTL);
    }

    public void invalidar(UUID pedidoId) {
        redisTemplate.delete("pedidos:pedido:" + pedidoId);
    }
}
```

## Cache stampede

Quando uma chave quente expira, toda requisição concorrente erra o cache ao mesmo tempo e todas
batem no banco para recalcular o mesmo valor ("thundering herd"). Para cargas caras e de alto
tráfego, deixe um único chamador computar enquanto os outros esperam:

```java
@Cacheable(value = "produtos", key = "#id", sync = true)
public ProdutoResponse buscarPorId(UUID id) { ... }
```

`sync = true` serializa a recomputação por chave **dentro de uma única instância**. Com N réplicas, até N
recomputações simultâneas ainda chegam ao banco — normalmente aceitável. Para limitar entre réplicas, um lock
curto (`SET chave valor NX PX ttl`, liberado com script que confere o valor) reduz a duplicação, mas **não** é
exclusão mútua garantida (expira durante pausas de GC/rede): use-o como otimização, nunca para proteger efeito
de negócio. Combine com TTL com jitter e, sobretudo, **limite de concorrência ao banco** para o caso em que o
cache inteiro some. Detalhes, incluindo queda do cache e invalidação após commit:
[cache protegido](cache-protecao-java.md).


## Antes/depois: leitura que derruba o banco quando o cache some

```java
// ANTES: toda requisição concorrente em miss consulta o banco (stampede) e a queda do Redis vira queda do banco
public ProdutoResponse buscar(UUID id) {
    var cacheado = cache.obter(id);
    if (cacheado.isPresent()) return cacheado.get();
    var produto = ProdutoResponse.from(repository.findById(id).orElseThrow()); // sem limite de concorrência
    cache.salvar(produto);
    return produto;
}
```

```java
// DEPOIS: recomputação serializada por chave + TTL com jitter, e o banco protegido por limite de concorrência
@Cacheable(value = "produtos", key = "#id", sync = true) // um único recomputador por chave nesta instância
public ProdutoResponse buscar(UUID id) {
    try (var permissao = limiteBanco.adquirir(Duration.ofMillis(200))) { // Semaphore com espera limitada
        return ProdutoResponse.from(repository.findById(id).orElseThrow());
    }
}
// TTL efetivo = base + aleatório(0..20%) para não expirar em onda sincronizada
```

Código executável (limite de concorrência ao banco quando o cache falha) e a prova:
[CacheProtegido](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CacheProtegido.java)
com [CacheProtegidoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java).
