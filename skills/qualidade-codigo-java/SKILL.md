---

name: qualidade-codigo-java
description: 'Guia de clean code aplicado a Java — DRY, KISS, YAGNI, naming, imutabilidade, `Optional`, streams, tratamento de exceção, Object Calisthenics — e refactorings do Fowler (Remove Parameter, Extract Method, Replace Magic Number, etc.). É o lado "ativo" da revisão: `revisao-de-codigo-java` diz o que revisar; esta diz como aplicar. Uso: sessão principal e agent `java-construtor` (carregada proativamente quando código Java for gerado/alterado); também `java-revisor`/`refatorador-java` ou `/qualidade-codigo-java`.'
license: MIT
metadata:

  author: https://github.com/srportto/srportto
  version: "1.3.0"
  domain: code-quality
  triggers: clean code, boas praticas, refatorar, DRY, KISS, YAGNI, imutabilidade, Optional, streams, Fowler, Object Calisthenics, Wrap All Primitives, First Class Collections, Law of Demeter, Tell Don't Ask
  role: reference
  scope: code-quality
  output-format: code
  related-skills: revisao-de-codigo-java, padroes-de-projeto-java, java-moderno
---

# Qualidade de Codigo Java (clean code + refactoring + Object Calisthenics)

## Visao geral

Guia de **aplicacao** de clean code em Java - DRY, KISS, YAGNI, nomenclatura, imutabilidade,
`Optional`, streams, exception handling - e de refactorings do catalogo do Fowler (Remove Parameter,
Extract Method, Replace Magic Number, etc.) e de **Object Calisthenics** (Tell Don't Ask, Wrap All
Primitives, First Class Collections, One Dot Per Line, No Classes With More Than Two Instance
Variables, Don't Use Else, Don't Abbreviate). Esta skill e o "lado ativo" da revisao: a
`revisao-de-codigo-java` diz **o que revisar** com checklist e severidades; esta skill diz **como
aplicar** o que a revisao aponta.

**Carregamento proativo:** esta skill deve ser consultada **durante a geracao** de codigo Java -
nao so depois, na revisao. Sempre que a sessao principal ou o agent `java-construtor` for
escrever uma classe, metodo ou refactoring Java novo, aplique DRY/KISS/YAGNI, Object Calisthenics
e as convencoes de nomenclatura abaixo antes de entregar o codigo - nao espere o `java-revisor`
apontar a violacao depois.

**Quando NAO usar:** para revisar um diff/PR com checklist por severidade, use
`revisao-de-codigo-java` (ela referencia esta aqui). Para a regra de dependencia entre camadas
(`domain`/`application`/`infrastructure`), use `arquitetura-limpa-java`. Para JPA/Hibernate (N+1, dirty
checking), use `persistencia-jpa`. Para logging (formato, MDC), use `monitoramento-java` (`references/logs-*.md`).

## Clean code - principios com exemplo

> **Coesao com `revisao-de-codigo-java`:** esta skill e o "lado ativo" (o **como** aplicar cada
> refactoring). A `revisao-de-codigo-java` e o "lado passivo" (o **o que** revisar com checklist
> e severidades). Mesmo formato de exemplo (Codigo Nao Aderente / Violacao e Explicacao /
> Exemplo de Refatoracao), mesmas terminologias (`Magic Number`, `Primitive Obsession`, `Guard
> Clause`, `Tell Don't Ask`).

> **Principio-mestre (Clean Code for AI):** alem de bom para humanos, todo codigo deste
> catalogo deve estar **otimizado para a janela de contexto do LLM** - nomes grepaveis,
> metodos curtos, arquivos pequenos, tipos explicitos e comentarios "por que". Cada secao
> abaixo reforca esse objetivo.

### DRY - Don't Repeat Yourself

**[Codigo Nao Aderente]:**
```java
// logica de validacao duplicada em dois metodos
public void criarUsuario(UsuarioRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Email invalido");
    }
}

public void atualizarUsuario(UsuarioRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Email invalido");
    }
}
```

**[Violacao e Explicacao]:** mesma validacao em 2 lugares - a 3a ocorrencia (em
`importarEmLote`, por exemplo) confirma o padrao. Manter a duplicacao significa N lugares para
corrigir quando a regra mudar.

**[Exemplo de Refatoracao]:**
```java
// fonte unica: metodo privado resolve sem criar interface/factory para o futuro
public class UsuarioService {
    public void criarUsuario(UsuarioRequest req)  { validarEmail(req.getEmail()); /* ... */ }
    public void atualizarUsuario(UsuarioRequest req) { validarEmail(req.getEmail()); /* ... */ }

    private void validarEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Email invalido");
        }
    }
}
```

> **DRY com bom senso:** regra das 3 ocorrencias - na 1a e 2a, duplicar pode ser mais barato que a
> abstracao errada; extraia na 3a. Nao crie `EmailValidator` com interface e implementacao unica "para
> o futuro" - abstracao especulativa e over-engineering (ver `padroes-de-projeto-java`, secao "Quando
> NAO aplicar pattern").

### KISS - Keep It Simple / YAGNI - You Aren't Gonna Need It

**[Codigo Nao Aderente]:**
```java
// sobre-engenharia para 1 implementacao, sem segunda variacao a vista
public interface UserFactory {
    User createUser();
}
public class ConcreteUserFactory implements UserFactory {
    public User createUser() { return new User(); }
}
```

**[Violacao e Explicacao]:** interface + implementacao unica **"para o futuro"** e a abstracao
especulativa classica (YAGNI). O custo (mais arquivos para ler, mais para o agente raciocinar)
nao traz beneficio enquanto houver 1 variante.

**[Exemplo de Refatoracao]:**
```java
// chamada direta; implemente a abstracao quando a segunda variacao aparecer de fato
public User createUser() { return new User(); }
```

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

## Imutabilidade

**[Codigo Nao Aderente]:**
```java
// classe com setters publicos: estado mutavel depois da construcao
public class Market {
    private Long id;
    private String name;
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}
```

**[Violacao e Explicacao]:** setters publicos expoem o estado interno mutavel; o chamador
pode alterar `id`/`name` apos a construcao, quebrando invariantes do dominio e criando bugs
sutis em fluxos assincronos.

**[Exemplo de Refatoracao]:**
```java
// record para DTOs e value objects (imutavel, equals/hashCode/toString gerados)
public record MarketDto(Long id, String name, MarketStatus status) {}

// ou classe com final fields e getters only
public class Market {
    private final Long id;
    private final String name;
    // getters only, no setters
}
```

Records sao o padrao deste catalogo (ver `java-moderno`): use para DTOs, value objects, chaves
compostas (`IdAutorizacao`). Nao use records quando precisar de mutabilidade ou heranca.

## Optional - uso correto

**[Codigo Nao Aderente]:**
```java
// get() sem verificar presenca
public Market buscarPorSlug(String slug) {
    Optional<Market> market = marketRepository.findBySlug(slug);
    return market.get();   // NoSuchElementException se vazio
}
```

**[Violacao e Explicacao]:** `Optional.get()` sem `.orElse`/`.orElseThrow`/`.isPresent()` joga
a decisao para o `NoSuchElementException` em runtime; o caller nao tem como reagir.

**[Exemplo de Refatoracao]:**
```java
// retorne Optional de metodos find*, use map/flatMap em vez de get() direto
public Market buscarPorSlug(String slug) {
    return marketRepository.findBySlug(slug)
        .orElseThrow(() -> new EntityNotFoundException("Market not found: " + slug));
}
```

## Streams - pipelines curtos, sem efeito colateral

**[Codigo Nao Aderente]:**
```java
// forEach com mutacao de lista externa
List<String> nomesAtivos = new ArrayList<>();
markets.stream().forEach(m -> {
    if (m.isAtivo()) {
        nomesAtivos.add(m.name().toUpperCase());
    }
});
```

**[Violacao e Explicacao]:** `forEach` capturando variavel externa e o anti-pattern classico
de stream; forca o agente a rastrear o efeito colateral e quebra paralelizacao futura.

**[Exemplo de Refatoracao]:**
```java
// pipeline curto, transformacao pura
List<String> names = markets.stream()
    .filter(Market::isAtivo)
    .map(m -> m.name().toUpperCase())
    .toList();
```

Quando o pipeline exigiria multiplos `flatMap`/estado acumulado so para simular um `for`, prefira o
loop explicito - clareza vale mais que "tudo em stream".

## Exception handling

- Use **unchecked exceptions** para erros de dominio (`BusinessException` - mapeada para 422 pelo
  handler central; ver `arquitetura-limpa-java`).
- **Crie excecoes especificas do dominio** (`MarketNotFoundException`) em vez de `RuntimeException`
  generica.
- **Evite** `catch (Exception ex)` amplo, a menos que seja para relancar/logar centralmente.
- **Sempre preserve a causa** (`throw new ApplicationException(msg, e)`) - perder a stack trace
  original torna investigacao quase impossivel.
- **Recursos** - sempre try-with-resources; `close()` manual nao executa se o codigo anterior lancar.
- **Mensagens de erro claras** - a mensagem deve dizer o que deu errado + identificador da
  operacao. Mensagens vagas forcam o agente a gastar turnos extras para descobrir a causa.

**[Codigo Nao Aderente]:**
```java
// perde a causa
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}
```

**[Violacao e Explicacao]:** perde a `Throwable cause` (stack trace original) e produz
mensagem generica sem contexto da operacao; investigacao quase impossivel depois.

**[Exemplo de Refatoracao]:**
```java
// especifica, com causa preservada
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}
```

## Genericos e type safety (tipos explicitos para IA)

**[Codigo Nao Aderente]:**
```java
// raw type; agente precisa inferir
public Map indexById(Collection items) { ... }   // sem type safety
```

**[Violacao e Explicacao]:** codigo sem anotacoes de tipo ou com raw types obriga agentes e
humanos a inferirem o que entra e sai, gerando falhas. O agente poupa trabalho de descoberta em
codigos tipados.

**[Exemplo de Refatoracao]:**
```java
// generic explicito
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }
```

### Tipo de parametro: prefira interface a implementacao concreta

Assinatura de metodo deve receber (e retornar, quando fizer sentido) o tipo mais generico que
atenda o contrato - normalmente uma interface (`List`, `Map`, `Set`) - nunca a implementacao
concreta (`ArrayList`, `HashMap`, `HashSet`). Isso desacopla o chamador da escolha de estrutura
interna e permite trocar a implementacao sem quebrar callers.

**[Codigo Nao Aderente]:**
```java
// amarra o caller a ArrayList; List.of(...) (imutavel) ou LinkedList exigiriam copia so pra chamar
public void processarClientes(ArrayList<String> customerNames) { ... }
```

**[Violacao e Explicacao]:** o parametro exige especificamente `ArrayList`; o metodo nao deveria
se importar com a implementacao, so com o contrato (`List`). Um caller com `List.of(...)` ou
`LinkedList` precisa copiar a colecao so para satisfazer a assinatura.

**[Exemplo de Refatoracao]:**
```java
// aceita qualquer List; caller escolhe a implementacao que fizer sentido
public void processarClientes(List<String> customerNames) { ... }
```


## Object Calisthenics, smells e refactorings do Fowler (resumo)

Exemplos completos (Codigo Nao Aderente / Violacao / Refatoracao) em
[refatoracoes e Object Calisthenics](references/refatoracoes-fowler.md). Sao **heuristicas de design**, nao bugs:
aplique quando reduzem um problema concreto (regra espalhada, valor invalido possivel, acoplamento que
dificulta mudanca). Uma classe com tres atributos coesos nao precisa ser quebrada so para cumprir a regra.

| Tecnica | Quando ajuda | Quando nao forcar |
|---|---|---|
| Tell, Don't Ask | Regra de negocio decidida fora do objeto dono dos dados | DTOs, records de transporte, projecoes de leitura |
| Wrap primitives / Value Objects | Valor com invariante (CPF, dinheiro, quantidade) validado em varios lugares | Campo sem regra propria |
| First Class Collections | Colecao com regras (limite, unicidade, soma) espalhadas | Lista simples sem comportamento |
| One Dot Per Line / Demeter | Navegacao profunda acopla a estrutura interna de outro objeto | Fluent APIs e streams (encadeamento e o design) |
| No more than two instance variables | Classe acumula responsabilidades | Classe coesa com 3–4 dependencias necessarias |
| Replace Magic Number | Numero de regra de negocio repetido ou sem nome | Constantes obvias (0, 1, indice) |
| Guard Clauses / Don't Use Else | Aninhamento profundo esconde o caminho feliz | `else` curto e claro |
| Remove Parameter, Extract Method, Replace Conditional with Polymorphism, Introduce Parameter Object, Replace Loop with Pipeline | Ver a referencia para o passo a passo | Refatorar e mudar comportamento no mesmo passo |

**Refatorar preserva comportamento** — inclusive o que nao aparece no tipo: ordem de processamento, momento do
ack/commit, transacao, liberacao de recursos em `finally`, cancelamento e timeouts. Rode os testes antes e
depois; se o trecho nao tem teste, escreva a prova primeiro (`testes-sistemas-java`).

# Quem aplica o que

| Situacao | Quem | Skill |
|---|---|---|
| Aplicar refactoring em uma classe/metodo | sessao principal | esta skill |
| Revisar diff/PR com checklist de severidade | agent `java-revisor` | `revisao-de-codigo-java` |
| Remocao de parametro focada (passo-a-passo) | sessao principal | esta skill (`references/refatoracoes-fowler.md#remove-parameter`) |
| Limpar imports nao usados | sessao principal | `remover-imports-nao-usados` |
| Centralizar configuracao dispersa (Shotgun Surgery) | session/engenheiro-devops | `arquitetura-limpa-java` (`references/modulos-spring.md`) |
| Decidir onde mora um value object novo | session/java-construtor | `arquitetura-limpa-java` |
