# Nomenclatura (Don't Abbreviate)

Leia este arquivo ao nomear classe, metodo, campo, constante ou parametro, ou ao ver nomes genericos (`Handler`, `Util`, `get`) e abreviacoes. Cobre nomes grepaveis, unidades em parametros, enum no lugar de String e agrupamento de parametros.

## Convencoes de nomenclatura (Object Calisthenics: Don't Abbreviate)

A regra do Object Calisthenics "Don't Abbreviate" orienta a nunca usar nomes abreviados: nomes
com significado completo ajudam no entendimento, tornam o `rg "NomeClasse"` efetivo e previnem
falhas de design. Nomes com 3+ letras continuam legiveis para o LLM.

```java
// Classes/Records: PascalCase
public class MarketService {}
public record Money(BigDecimal amount, Currency currency) {}

// Metodos/campos: camelCase
private final MarketRepository marketRepository;
public Market findBySlug(String slug) {}

// Constantes: UPPER_SNAKE_CASE
private static final int MAX_PAGE_SIZE = 100;
```

**Nomes que revelam intencao e sao grepaveis** (nao abrevie sem motivo):

**[Codigo Nao Aderente]:**
```java
// abreviacoes obscuras e nomes genericos nao sao grepaveis
public List<Produto> get(String s) { ... }
public boolean chk(String str) { ... }
private static final int N = 100;
public class Handler { public void handle(String p, String rng) { ... } }
```

**[Violacao e Explicacao]:** nomes genericos (`Handler`, `get`, `N`, `chk`, `p`, `rng`)
poluem a busca lexical, escondem a intencao e violam a regra "Don't Abbreviate" do Object
Calisthenics. O agente tem que ler o corpo para descobrir o que o metodo faz. Se pesquisar pelo
nome retorna coisas irrelevantes, o nome esta ruim para a IA.

**[Exemplo de Refatoracao]:**
```java
// nome diz o que faz; busca lexical (rg "AutorizacaoExpiradaHandler") cai direto
public List<Produto> buscarAtivosPorCategoria(String categoria) { ... }
public boolean precoEhValido(BigDecimal preco) { ... }
private static final int TAMANHO_MAXIMO_PAGINA = 100;
public class AutorizacaoExpiradaHandler {
    public void expirarAutorizacao(AutorizacaoId id, MotivoExpiracao motivo) { ... }
}
```

> **Nomes genericos proibidos** (poluem `grep`, escondem intencao): `Handler`, `Manager`,
> `Helper`, `Util`, `Data`, `Process`, `Info`, `Common`, `Base`. Use nomes de dominio.
> Excecao: `Manager` e aceitavel **quando** o dominio e o proprio gerenciado
> (`PixBufferRingPartitionPurgeManager`), nunca sozinho.

> Use portugues ou ingles consistentemente dentro do mesmo pacote/classe - nao misture.

### Parametros de metodo ricos e nao abreviados

A regra "Don't Abbreviate" vale tambem para **parametros**: nome completo, sem sigla, que revela o
que o valor representa - inclusive a unidade de medida quando for numerico ou temporal.

**[Codigo Nao Aderente]:**
```java
// parametros abreviados obrigam o agente a abrir o corpo do metodo para decifrar o dominio
public void register(String fn, String ln, int age, double amt) { ... }
public void schedule(long timeout, long delay) { ... }
```

**[Violacao e Explicacao]:** `fn`, `ln`, `amt` escondem nome/sobrenome/valor; `timeout` e `delay`
sem unidade obrigam o caller a abrir a implementacao (ou a documentacao) para saber se e
milissegundos ou segundos - erro classico de integracao entre servicos.

**[Exemplo de Refatoracao]:**
```java
// nomes completos e, quando numerico/temporal, com a unidade explicita no proprio nome
public void registerUser(String firstName, String lastName, int ageInYears, double transactionAmount) { ... }
public void schedule(long timeoutInMilliseconds, long delayInSeconds) { ... }
```

> **Grupo de parametros relacionados:** quando os mesmos parametros viajam juntos em varios
> metodos (ex.: `latitude`/`longitude` sempre juntos), nao adicione mais parametros individuais -
> agrupe em um value object (`Coordinate`) - ver "Introduce Parameter Object" e "Primitive
> Obsession" mais abaixo.

> **Valor restrito a um conjunto conhecido:** parametro tipo `String status` ou `int tipo` que so
> aceita alguns valores validos deve virar `enum` (`BookingStatus status`), nao um primitivo
> generico - ver "Replace Magic Number with Symbolic Constant" mais abaixo.

