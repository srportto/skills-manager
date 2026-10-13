# SOLID com contexto (SRP, OCP, LSP, ISP, DIP)

Leia este arquivo ao suspeitar de classe com varios motivos para mudar, override que lanca
`UnsupportedOperationException` ou fica vazio, interface "gorda" que obriga a implementar metodo
inutil, ou `instanceof` no cliente para desviar de um subtipo. OCP e DIP ja tem fonte propria no
catalogo; aqui ficam so o criterio e o link.

Todos os principios buscam o mesmo objetivo: **reduzir acoplamento e custo de mudanca**. Aplique onde
existe **variacao real ou esperada**; abstracao sem variacao e so custo de leitura (YAGNI).

| Letra | Principio | Pergunta-chave | Onde esta o detalhe |
|---|---|---|---|
| **S** | Single Responsibility | Quantos motivos (atores) esta classe tem para mudar? | Esta reference |
| **O** | Open/Closed | Posso adicionar comportamento sem editar codigo existente? | [Replace Conditional with Polymorphism](refatoracoes-fowler.md#replace-conditional-with-polymorphism), [Strategy por lista injetada](../../padroes-de-projeto-java/references/strategy-lista-injetada.md), [sealed e switch](../../java-moderno/references/sealed-e-switch.md) |
| **L** | Liskov Substitution | O subtipo substitui o tipo-pai sem surpresa para o cliente? | Esta reference |
| **I** | Interface Segregation | Alguem implementa ou depende de metodo que nao usa? | Esta reference |
| **D** | Dependency Inversion | A regra de negocio depende de detalhe de infraestrutura? | [arquitetura-limpa-java](../../arquitetura-limpa-java/SKILL.md) (`port/out`) |

## S - Single Responsibility Principle

Uma classe deve ter **um unico motivo para mudar**, ou seja, responder a um unico "ator" do negocio.
SRP fala de **motivo de mudanca**, nao de numero de metodos: "uma classe por metodo" e excesso.

**[Codigo Nao Aderente]:**
```java
// regra, persistencia e notificacao misturadas no mesmo metodo
public class PagamentoService {
    public void processar(Pagamento pagamento) {
        if (pagamento.valor().signum() <= 0) {
            throw new IllegalArgumentException();
        }
        jdbcTemplate.update("INSERT INTO pagamento ...", pagamento.id(), pagamento.valor());
        kafkaTemplate.send("pagamentos", pagamento.id(), paraJson(pagamento));
    }
}
```

**[Violacao e Explicacao]:** tres atores mudam este arquivo - quem define a regra de valor, quem
mexe no schema do banco e quem muda formato ou topico do evento. Cada mudanca abre a regra de
negocio, aumenta o diff e gera conflito de merge; o teste da regra precisa de banco e broker.

**[Exemplo de Refatoracao]:**
```java
// regra no dominio: o value object nao nasce invalido
public record Pagamento(IdPagamento id, BigDecimal valor) {
    public Pagamento {
        if (valor.signum() <= 0) {
            throw new ValorPagamentoInvalidoException(valor);
        }
    }
}

// portas do dominio (domain/port/out): persistencia e evento viram detalhe de adapter
public interface PagamentoRepository { void salvar(Pagamento pagamento); }
public interface PagamentoPublisher { void publicar(Pagamento pagamento); }

// use case so orquestra; mudar o topico Kafka nao abre este arquivo
public class ProcessarPagamentoService {
    private final PagamentoRepository repository;
    private final PagamentoPublisher publisher;

    public ProcessarPagamentoService(PagamentoRepository repository, PagamentoPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public void processar(Pagamento pagamento) {
        repository.salvar(pagamento);
        publisher.publicar(pagamento);
    }
}
```

> Sinais de SRP ferida: arquivo acima de 300-500 linhas, grupos de atributos sem relacao entre si
> (ver [object-calisthenics.md](object-calisthenics.md)), imports de
> camadas diferentes (JDBC + Kafka + regra) na mesma classe. Ordem entre `salvar` e `publicar`
> (dual write) e assunto de `mensageria-sqs-kafka` (outbox); refatorar nao muda essa garantia.

## O - Open/Closed Principle

Aberto para **extensao**, fechado para **modificacao**. Sintoma: o mesmo `switch`/`if` por tipo
repetido em varios lugares. As duas correcoes do catalogo tem trade-offs opostos (*expression
problem*):

| Abordagem | Facil adicionar | Dificil adicionar | Quando usar |
|---|---|---|---|
| Interface + Strategy (`List<Interface>` injetada) | **novos tipos** | novas operacoes | Conjunto de tipos aberto: plugins, produtos, regras que crescem |
| `sealed` + `switch` exaustivo (Java 25) | **novas operacoes** | novos tipos (o compilador aponta cada ponto a alterar) | Conjunto de tipos fechado e estavel: modelagem de dominio |

Na Strategy, o "nenhuma regra aplicavel" vira excecao de dominio com contexto
(`orElseThrow(() -> new TipoPagamentoNaoSuportadoException(tipo))`), nunca `orElseThrow()` mudo
(ver [excecoes.md](excecoes.md)). Exemplos completos nos links da tabela inicial.

## L - Liskov Substitution Principle

Se `S` e subtipo de `T`, todo codigo que usa `T` funciona com `S` **sem conhecer a diferenca**. Vale
para o contrato inteiro: pre-condicoes (o subtipo nao pode exigir mais), pos-condicoes (nao pode
entregar menos) e invariantes.

**[Codigo Nao Aderente]:**
```java
public class Conta {
    protected BigDecimal saldo;

    public void sacar(BigDecimal valor) { saldo = saldo.subtract(valor); }
}

public class ContaInvestimentoBloqueada extends Conta {
    @Override
    public void sacar(BigDecimal valor) {
        // quebra o contrato de Conta: o cliente nao tem como prever
        throw new UnsupportedOperationException("Conta bloqueada");
    }
}

// cliente que confiava no contrato de Conta falha em runtime
void pagarFatura(Conta conta, BigDecimal valor) { conta.sacar(valor); }
```

**[Violacao e Explicacao]:** a heranca promete uma capacidade (`sacar`) que o subtipo nao tem. O erro
so aparece em runtime, e a "correcao" tipica - `if (conta instanceof ContaInvestimentoBloqueada)` no
cliente - espalha o conhecimento do subtipo e quebra OCP. Trocar o `throw` por override vazio e pior:
a falha ruidosa vira falha silenciosa (saque que nao aconteceu).

**[Exemplo de Refatoracao]:**
```java
// modele a capacidade, nao force a heranca
public interface Conta { BigDecimal saldo(); }
public interface ContaMovimentavel extends Conta { void sacar(BigDecimal valor); }

public final class ContaCorrente implements ContaMovimentavel { /* ... */ }
public final class ContaInvestimentoBloqueada implements Conta { /* nao promete sacar */ }

// o tipo do parametro garante a capacidade; nao ha caminho para o erro
void pagarFatura(ContaMovimentavel conta, BigDecimal valor) { conta.sacar(valor); }
```

| Sinal de violacao | Exemplo |
|---|---|
| Override que lanca excecao | `throw new UnsupportedOperationException()` |
| Override vazio ou que ignora o argumento | `@Override void sacar(BigDecimal valor) {}` |
| Pre-condicao mais forte no subtipo | o pai aceita qualquer valor positivo; o filho exige `valor > 100` |
| Pos-condicao mais fraca no subtipo | o pai garante lista nao nula; o filho devolve `null` |
| Checagem de tipo no cliente | `if (conta instanceof ContaBloqueada)` antes de chamar o metodo |

> **Nuance do JDK:** `List.of(...)` lanca `UnsupportedOperationException` em `add()`, mas o contrato
> de `Collection` declara `add` como **operacao opcional** - e um contrato fraco documentado, aceito
> por pragmatismo. No seu dominio, prefira tipos que nao prometem o que nao entregam. Prefira
> composicao e classes `final`; heranca so quando o subtipo cumpre o contrato inteiro.

## I - Interface Segregation Principle

Clientes nao devem depender de metodos que nao usam. Prefira **interfaces pequenas por papel**
(*role interfaces*) a uma interface "gorda".

**[Codigo Nao Aderente]:**
```java
public interface MeioPagamento {
    Autorizacao autorizar(Pagamento pagamento);
    void estornar(IdPagamento id);
    List<Parcela> parcelar(Pagamento pagamento, int quantidadeParcelas);
    void agendar(Pagamento pagamento, LocalDate data);
}

public final class Pix implements MeioPagamento {
    @Override
    public List<Parcela> parcelar(Pagamento pagamento, int quantidadeParcelas) {
        // PIX nao parcela: violacao de ISP que vira violacao de LSP
        throw new UnsupportedOperationException();
    }
    // ...
}
```

**[Violacao e Explicacao]:** `Pix` e obrigado a implementar `parcelar`, e todo cliente de
`MeioPagamento` "pode" chamar algo que explode. Mocks de teste ficam enormes, e uma mudanca em
`parcelar` afeta quem so usa PIX.

**[Exemplo de Refatoracao]:**
```java
// uma interface por capacidade; cada meio implementa so o que suporta
public interface Autorizavel { Autorizacao autorizar(Pagamento pagamento); }
public interface Estornavel { void estornar(IdPagamento id); }
public interface Parcelavel { List<Parcela> parcelar(Pagamento pagamento, int quantidadeParcelas); }
public interface Agendavel { void agendar(Pagamento pagamento, LocalDate data); }

public final class Pix implements Autorizavel, Estornavel, Agendavel { /* ... */ }
public final class Cartao implements Autorizavel, Estornavel, Parcelavel { /* ... */ }

// o cliente declara so a capacidade de que precisa
public class ParcelamentoService {
    private final Parcelavel parcelavel;
    // ...
}
```

> Em app hexagonal, a mesma ideia vale para `port/out`: a porta expressa o que o caso de uso
> **precisa**, nao tudo o que o adapter sabe fazer. Nao fatie interface coesa que todos os clientes
> usam inteira - ISP resolve dependencia de metodo inutil, nao tamanho de interface.

## D - Dependency Inversion Principle

Modulos de alto nivel (regra de negocio) **nao dependem** de modulos de baixo nivel (infraestrutura);
ambos dependem de abstracoes, e a **abstracao pertence ao dominio** (`domain/port/out`). Sintoma:
`new KafkaProducer<>(...)`, `new OracleDao()` ou SDK de broker dentro de `domain`/`application`.
Estrutura, regra de dependencia e onde mora cada porta: [arquitetura-limpa-java](../../arquitetura-limpa-java/SKILL.md).

| Termo | O que e |
|---|---|
| **Dependency Inversion** (DIP) | Principio de design: em que **direcao** apontam as dependencias |
| **Dependency Injection** (DI) | Tecnica: **como** a dependencia chega ao objeto (construtor, no Spring) |

Injetar por construtor uma classe concreta de infraestrutura no dominio e DI **sem** DIP.

## Como os principios se reforcam

| Principio | Smell que resolve | Ferramenta Java tipica |
|---|---|---|
| SRP | Classe "Deus", arquivo grande, imports de varias camadas | Extract Class, `record` com invariante, portas |
| OCP | `switch`/`if` por tipo repetido | Strategy com `List<Interface>`, `sealed` + `switch` |
| LSP | `UnsupportedOperationException`, override vazio, `instanceof` no cliente | Composicao, interfaces de capacidade, `final` |
| ISP | Implementacao que lanca excecao ou fica vazia | Role interfaces, porta por caso de uso |
| DIP | `new` de infraestrutura no dominio | Porta em `domain/port/out` + injecao por construtor |

Violar ISP costuma gerar violacao de LSP (metodo nao suportado); aplicar DIP habilita OCP (trocar
adapter sem editar o dominio).

## Quando NAO aplicar (excesso de engenharia)

| Situacao | Recomendacao |
|---|---|
| Interface com uma unica implementacao e sem segunda variacao a vista | Classe concreta; Spring e Mockito ja resolvem a testabilidade (ver [clean-code-principios.md](clean-code-principios.md)). Excecao: `port/in`/`port/out`, exigidas pela fronteira hexagonal |
| Conjunto de tipos fechado e estavel | `sealed` + `switch` e mais simples que Strategy |
| Script, POC ou lambda descartavel | SRP e DIP adicionam indirecao sem retorno |
| "Uma classe por metodo" | SRP mede motivo de mudanca, nao quantidade de metodos |

Antes de introduzir abstracao por causa de um principio, confira [quando nao aplicar pattern](../../padroes-de-projeto-java/references/quando-nao-aplicar.md).
