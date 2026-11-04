Leia este arquivo quando precisar decidir onde fica `@Transactional` (use case, nunca controller ou domain), usar `readOnly = true`, diagnosticar transação que "não funciona" por auto-invocação ou limitar a duração da transação.

## Transações

`@Transactional` vive em `application/usecase/` — **nunca** nos driving adapters de
`infrastructure/` (controller, listener SQS) nem no `domain/`. O controller apenas chama a `port/in`;
é o use case quem delimita a fronteira transacional.

Padrão adotado neste projeto (`ProdutoService`, overlay `rest-crud-banco`): `readOnly = true` na
classe inteira, e `@Transactional` (leitura/escrita) sobrescrito nos métodos que gravam. Isso desliga o
dirty checking do Hibernate nos métodos de leitura (menos overhead) e deixa explícito, por método,
quais alteram dado:

```java
// application/usecase/ProdutoService.java
@Service
@Transactional(readOnly = true)
public class ProdutoService {

    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Produto criar(Produto produto) {
        produto.validar();
        return repository.save(produto);
    }

    public Produto buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Produto nao encontrado: " + id));
    }

    public List<Produto> listar() {
        return repository.findAll();
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarPorId(id));
    }
}
```

### Pitfall: auto-invocação não passa pelo proxy

`@Transactional` funciona via proxy do Spring. Uma chamada interna (`this.metodo(...)`) não passa pelo
proxy, então a anotação é **ignorada silenciosamente**:

```java
// ERRADO - chamada interna (this.criar) nao passa pelo proxy Spring; @Transactional de criar() e ignorado
@Service
@Transactional(readOnly = true)
public class ProdutoService {

    public void processarLote(List<Produto> produtos) {
        produtos.forEach(this::criar); // this.criar() -> sem transacao real aqui
    }

    @Transactional
    public Produto criar(Produto produto) {
        produto.validar();
        return repository.save(produto);
    }
}
```

```java
// CORRETO - extrai o metodo transacional para outro bean, chamado de fora (passa pelo proxy)
@Service
public class ProcessadorLoteService {

    private final ProdutoService produtoService;

    public ProcessadorLoteService(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    public void processarLote(List<Produto> produtos) {
        produtos.forEach(produtoService::criar); // chamada externa, passa pelo proxy
    }
}
```


## Antes/depois: transação curta, sem I/O externo dentro

Segurar a transação (e a conexão do pool) durante uma chamada HTTP ou fila transforma lentidão do
terceiro em esgotamento do pool. Faça o I/O fora da transação e grave só o resultado.

```java
// ANTES - a conexão do pool fica presa durante a chamada HTTP ao gateway
@Transactional
public void cobrar(Long pedidoId) {
    Pedido pedido = repository.findById(pedidoId).orElseThrow();
    ResultadoCobranca resultado = gateway.cobrar(pedido); // I/O externo com transação e conexão abertas
    pedido.registrar(resultado);
}
```

```java
// DEPOIS - lê em transação curta, chama o gateway sem transação, grava em outra transação curta
@Service
public class CobrancaService {

    private final PedidoTransacional pedidos; // bean separado: as chamadas passam pelo proxy
    private final GatewayPagamento gateway;

    public CobrancaService(PedidoTransacional pedidos, GatewayPagamento gateway) {
        this.pedidos = pedidos;
        this.gateway = gateway;
    }

    public void cobrar(Long pedidoId) {
        Pedido pedido = pedidos.carregar(pedidoId);            // transação 1 (readOnly), termina aqui
        ResultadoCobranca resultado = gateway.cobrar(pedido);  // sem transação, com timeout próprio
        pedidos.registrar(pedidoId, resultado);                // transação 2, curta
    }
}
```

Dimensionamento do pool e timeouts de aquisição de conexão têm **fonte única** em
[orcamento-conexoes](../../banco-de-dados-performance/references/orcamento-conexoes.md); esta skill só
decide *onde* a transação começa e termina.

## Execução comprovada

A atomicidade "registro de idempotência + efeito na mesma transação" e a arbitragem pela restrição única
em conexões concorrentes estão provadas em
[`ProcessadorIdempotente`](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java)
e [`ProcessadorIdempotenteTest`](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteTest.java).
