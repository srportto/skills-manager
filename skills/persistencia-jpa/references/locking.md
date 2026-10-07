Leia este arquivo quando houver risco de lost update, conflito de escrita concorrente, reserva de estoque, deadlock, necessidade de `@Version`, lock pessimista, isolamento ou timeout de transação.

## Locking otimista

Para evitar *lost update* (duas transações leem o mesmo registro e a segunda grava por cima da
primeira sem saber que ele mudou), adicione `@Version` na entidade:

```java
// infrastructure/persistence/Produto.java
@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long versao;

    private String nome;
    private BigDecimal preco;

    public void validar() {
        if (preco == null || preco.signum() <= 0) {
            throw new BusinessException("Preco do produto deve ser maior que zero");
        }
    }
}
```

O Hibernate incrementa `versao` a cada `UPDATE` e compara o valor lido com o valor atual no banco; se
divergirem, lança `OptimisticLockingFailureException`. **Atenção ao momento:** a verificação acontece no
*flush*, que normalmente ocorre no commit — **depois** do `return` do método `@Transactional`. Um `try/catch`
em volta de `save(...)` dentro da transação não pega o conflito. Duas opções corretas:

```java
// Opção 1 — forçar o flush dentro do try (o conflito aparece aqui)
@Transactional
public Produto atualizar(Produto produto) {
    try {
        return repository.saveAndFlush(produto);
    } catch (OptimisticLockingFailureException conflito) {
        throw new ConflitoDeConcorrencia("Produto foi alterado por outro processo", conflito);
    }
}

// Opção 2 — traduzir fora da transação, no handler central de erros (infrastructure/web)
@ExceptionHandler(OptimisticLockingFailureException.class)
ProblemDetail conflito(OptimisticLockingFailureException erro) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Recurso alterado por outra requisição; releia e tente de novo.");
}
```

Conflito de concorrência é **409** (ver `api-rest-design`); o cliente relê e decide. Repetir automaticamente
só é seguro se a operação for recalculada a partir do estado novo (releitura + reaplicação), com tentativas
limitadas.

### Atualização (PUT) com `@Version`

`@Version` só protege o PUT se a versão que o **cliente leu** chegar até a comparação. O cliente devolve essa
versão no corpo (`versao`) ou em `If-Match` (ETag). A atualização **carrega a entidade gerenciada**, confere a
versão, copia os campos editáveis e deixa o dirty checking gerar `UPDATE ... WHERE id = ? AND versao = ?`.

```java
// ANTES - monta uma entidade nova a partir do DTO e chama save
repository.save(mapper.paraEntidade(new Produto(id, nome, preco)));
// Long versao == null → Spring Data trata como nova → persist de entidade com id: o PUT falha.
// long versao == 0    → merge com versão 0 → todo PUT depois do primeiro dá conflito.
// Nenhum dos dois compara com a versão que o cliente leu: o lost update continua possível.
```

```java
// DEPOIS - carrega, confere a versão recebida, altera a entidade gerenciada
@Transactional
public Produto atualizar(Long id, long versaoLida, AtualizarProduto comando) {
    Produto produto = repository.findById(id).orElseThrow(() -> new ProdutoNaoEncontrado(id));   // 404
    if (produto.getVersao() != versaoLida) {           // alguém gravou depois da leitura do cliente
        throw new ConflitoDeConcorrencia("Produto alterado por outra requisição; releia e tente de novo.");
    }
    produto.setNome(comando.nome());                    // só campos editáveis; nunca id nem versao
    produto.setPreco(comando.preco());
    return repository.saveAndFlush(produto);            // UPDATE com versao no WHERE; devolve a versão nova
}
```

A comparação explícita pega o conflito entre a leitura do cliente e a requisição. O `WHERE versao = ?` do flush
pega a corrida entre o `findById` e o commit (`OptimisticLockingFailureException`). Os dois viram **409**; com
`If-Match`, a divergência pode ser **412**. `saveAndFlush` devolve a versão já incrementada, que vai na resposta
(ou no `ETag`) para o próximo PUT. Criação continua com `save` de entidade com `id` e `versao` nulos (`persist`).

## Locking pessimista, isolamento e timeouts

Use lock pessimista quando o conflito é frequente e repetir é caro (ex.: reservar a última unidade de estoque):

```java
// infrastructure/persistence — SELECT ... FOR UPDATE com espera limitada (sem timeout, espera indefinida)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
@Query("select e from EstoqueEntity e where e.sku = :sku")
Optional<EstoqueEntity> travarPorSku(@Param("sku") String sku);
```

- O suporte ao hint de timeout varia por banco/dialeto (o PostgreSQL não tem `FOR UPDATE WAIT n`; lá, garanta o
  limite com `lock_timeout` por papel ou `SET LOCAL lock_timeout` na transação). Confira o SQL gerado e teste
  a espera com duas transações concorrentes.
- Trave sempre na **mesma ordem** (ex.: por id crescente) para evitar deadlock; o banco aborta uma das
  transações em deadlock — trate como conflito transitório com retry limitado.
- Transação curta: nada de chamada HTTP, fila ou espera de backoff com lock/conexão seguros.
- Atualização condicional atômica dispensa lock explícito em muitos casos:
  `update estoque set quantidade = quantidade - :qtd where sku = :sku and quantidade >= :qtd` (linhas afetadas
  = 0 → sem estoque).
- `@Transactional(timeout = 5)` (segundos) e `statement_timeout`/`setQueryTimeout` limitam a duração; o
  nível de isolamento padrão (READ COMMITTED no PostgreSQL, REPEATABLE READ no MySQL/InnoDB) muda quais
  anomalias são possíveis — documente quando depender de um nível específico.


## Antes/depois: check-then-act vs atualização condicional atômica

```java
// ANTES - lê, decide em memória e grava: duas transações leem 1 unidade e ambas "vendem"
@Transactional
public void reservar(String sku, int qtd) {
    EstoqueEntity estoque = repository.findBySku(sku).orElseThrow();
    if (estoque.getQuantidade() < qtd) {
        throw new SemEstoque(sku);
    }
    estoque.setQuantidade(estoque.getQuantidade() - qtd); // lost update sem @Version nem lock
}
```

```java
// DEPOIS - o banco arbitra: UPDATE condicional atômico, sem lock explícito nem releitura
public interface EstoqueRepository extends JpaRepository<EstoqueEntity, Long> {

    @Modifying
    @Query("update EstoqueEntity e set e.quantidade = e.quantidade - :qtd "
            + "where e.sku = :sku and e.quantidade >= :qtd")
    int baixar(@Param("sku") String sku, @Param("qtd") int qtd);
}

@Transactional
public void reservar(String sku, int qtd) {
    if (repository.baixar(sku, qtd) == 0) { // 0 linhas afetadas = sem estoque (ou sku inexistente)
        throw new SemEstoque(sku);
    }
}
```

## Antes/depois: espera de lock ilimitada no PostgreSQL

```sql
-- ANTES - sem limite: uma transação lenta segura todas as demais atrás do SELECT ... FOR UPDATE
SELECT * FROM estoque WHERE sku = 'ABC-1' FOR UPDATE;

-- DEPOIS - espera limitada na própria transação; estourou, o banco aborta e a aplicação trata como conflito
BEGIN;
SET LOCAL lock_timeout = '2s';
SELECT * FROM estoque WHERE sku = 'ABC-1' FOR UPDATE;
COMMIT;
```

Para investigar quem está bloqueando quem em produção, use a consulta de locks de
[diagnostico-postgresql.sql](../../banco-de-dados-performance/assets/diagnostico-postgresql.sql).

## Execução comprovada

Corrida de 16 conexões reais contra PostgreSQL arbitrada por restrição única (nenhuma duplicata passa, nem
sem lock explícito):
[`ProcessadorIdempotenteExternoIT`](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java);
versão local sem container: [`ProcessadorIdempotenteTest`](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteTest.java).
