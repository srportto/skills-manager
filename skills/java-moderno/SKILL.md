---

name: java-moderno
description: "Referência de features modernas do Java 25 (records, sealed classes, pattern matching, switch expressions, text blocks, virtual threads, `var`) com exemplos antes/depois. Use ao escrever código que se beneficia de feature moderna, migrar de Java 8/11/17/21, ou perguntar \"qual o jeito moderno de fazer X em Java\". Uso: agents `java-construtor`/`java-revisor` ou `/java-moderno`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.2.0"
  domain: language-features
  triggers: records, sealed classes, pattern matching, virtual threads, text blocks, switch expressions, Java 25, var, switch pattern, when, case null, non-sealed, record patterns
  role: reference
  scope: java-language
  output-format: code
  related-skills: qualidade-codigo-java, padroes-de-projeto-java, arquitetura-limpa-java
---

# Java Moderno

## Visão geral

Guia de referência rápida das features modernas do Java (records, sealed classes, pattern matching,
switch expressions, text blocks, virtual threads, `var`) na stack fixa deste catálogo — **Java 25 +
Spring Boot 4.0.7**. Use esta skill para decidir se uma feature moderna resolve um código específico,
ver o exemplo antes/depois, e para orientar uma migração de código escrito em uma versão anterior.

**Quando NÃO usar:** para aplicar um design pattern GoF (Strategy, Factory, Builder...), use
`padroes-de-projeto-java`. Para uma revisão completa de código (não só modernização), use
`revisao-de-codigo-java`.

## 1. Records

Tipo imutável para modelar dados: o compilador gera construtor, accessors, `equals`/`hashCode`/
`toString` a partir dos componentes declarados. Substitui a classe "de dados" manual.

```java
// Java classico: campo final, construtor, getters, equals, hashCode escritos a mao (~20 linhas)
public final class Pedido {
    private final String id;
    private final BigDecimal valor;
    public Pedido(String id, BigDecimal valor) { this.id = id; this.valor = valor; }
    public String getId() { return id; }
    public BigDecimal getValor() { return valor; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido pedido)) return false;
        return Objects.equals(id, pedido.id) && Objects.equals(valor, pedido.valor);
    }
    @Override public int hashCode() { return Objects.hash(id, valor); }
}

// Java moderno: record de 1 linha - construtor, accessors, equals/hashCode/toString gerados
public record Pedido(String id, BigDecimal valor) {}
```

Records executáveis neste catálogo: `Pagamento.Pix`, `Pagamento.Cartao`, `ResultadoCobranca.Aprovada` (módulo
[linguagem](../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java)).

**Quando usar:** DTO de request/response, value object, chave composta imutável.
**Quando evitar:** quando precisa de mutabilidade (setter após a criação) ou de herdar de uma classe —
records são implicitamente `final` e só podem implementar interfaces, nunca estender outra classe.

## 2. Sealed classes/interfaces

Hierarquia fechada em tempo de compilação: só as classes/interfaces listadas em `permits` podem
implementar o tipo selado. Modela domínios finitos e conhecidos (tipos de pagamento, estados).

```java
// Java classico: interface aberta - qualquer classe pode implementar, sem o compilador avisar
public interface Pagamento {
    BigDecimal valor();
}
public class Pix implements Pagamento { /* ... */ }
public class Cartao implements Pagamento { /* ... */ }
// nada impede outra classe implements Pagamento aparecer depois, longe deste arquivo

// Java moderno: hierarquia fechada - so os records aninhados implementam Pagamento
// (permits implicito: todos no mesmo arquivo; records sao final e fecham a hierarquia)
public sealed interface Pagamento {
    BigDecimal valor();

    record Pix(String chave, BigDecimal valor) implements Pagamento {}
    record Cartao(Bandeira bandeira, int parcelas, BigDecimal valor) implements Pagamento {}
    record Boleto(String linhaDigitavel, BigDecimal valor) implements Pagamento {}

    enum Bandeira { VISA, MASTERCARD, ELO }
}
```

Fonte executável, com validação nos construtores compactos:
[Pagamento](../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java).

**Quando usar:** hierarquia de domínio finita e conhecida (tipos de pagamento, estados de um fluxo) —
casa direto com switch exaustivo (item 3), que quebra o build se um tipo novo não for tratado.
**Quando evitar:** hierarquia que precisa ser extensível por módulos/plugins externos que o autor do
tipo selado não controla.

Modificadores das subclasses (`final`, `sealed`, `non-sealed`), classe abstrata selada, `permits` implícito no
mesmo arquivo e introspecção (`isSealed`, `getPermittedSubclasses`): [sealed-e-switch](references/sealed-e-switch.md).

## 3. Pattern matching

Elimina o cast manual depois de um `instanceof`, permite `switch` com patterns exaustivo sobre
sealed types, e desestrutura records diretamente na condição (record patterns).

```java
// Java classico: instanceof + cast manual
if (pagamento instanceof Pagamento.Pix) {
    Pagamento.Pix pix = (Pagamento.Pix) pagamento;
    processar(pix.chave());
}
// Java moderno: instanceof com binding - "pix" ja nasce com o tipo certo, sem cast
if (pagamento instanceof Pagamento.Pix pix) {
    processar(pix.chave());
}
```

```java
// Java classico: if/else em cadeia, sem garantia do compilador se surgir um tipo novo
BigDecimal taxa;
if (pagamento instanceof Pagamento.Pix) {
    taxa = BigDecimal.ZERO;
} else if (pagamento instanceof Pagamento.Cartao) {
    taxa = new BigDecimal("2.99");
} else if (pagamento instanceof Pagamento.Boleto) {
    taxa = new BigDecimal("2.50");
} else {
    throw new IllegalStateException("Tipo de pagamento desconhecido");
}

// Java moderno: switch exaustivo sobre sealed interface - sem "default"; se um tipo novo entrar
// na hierarquia, o build quebra ate o switch ser atualizado
BigDecimal taxaModerna = switch (pagamento) {
    // sem "case null", um pagamento nulo lancaria NullPointerException
    case null -> throw new IllegalArgumentException("Pagamento ausente");
    case Pagamento.Pix _ -> BigDecimal.ZERO;
    // record pattern + guarda: desestrutura o Cartao e refina; vem antes do caso sem guarda (dominancia)
    case Pagamento.Cartao(_, int parcelas, BigDecimal valor) when parcelas > 1 ->
            percentual(valor, TAXA_CARTAO.add(ACRESCIMO_POR_PARCELA.multiply(BigDecimal.valueOf(parcelas - 1))));
    case Pagamento.Cartao(_, _, BigDecimal valor) -> percentual(valor, TAXA_CARTAO);
    case Pagamento.Boleto _ -> TARIFA_BOLETO;
};
```

Record patterns desestruturam o record direto na condição; `_` (Java 22) descarta o componente que não interessa.
Fonte executável: [Tarifacao](../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Tarifacao.java);
regras de exaustividade e dominância, com prova de compilação: [sealed-e-switch](references/sealed-e-switch.md).

**Quando usar:** sempre que houver `instanceof` seguido de cast manual, e especialmente sobre
hierarquias `sealed`. **Quando evitar:** quando o comportamento por tipo já é resolvido por
polimorfismo simples (método sobrescrito) — pattern matching é para decidir por tipo concreto,
não substitui um bom design orientado a objetos.

## 4. Switch expressions

`switch` que produz um valor, com `->` (sem fallthrough — cada ramo é isolado) e `yield` quando o
ramo precisa de mais de uma instrução antes do valor final.

```java
// Java classico: switch statement exige break em cada case para evitar fallthrough (nao mostrado)

// Java moderno: switch expression com "->", sem fallthrough, atribui o valor direto.
// Enum tratado por completo dispensa "default": um valor novo no enum quebra o build aqui.
String descricao = switch (status) {
    case ATIVO -> "Em vigor";
    case CANCELADO -> "Cancelado";
};

// Rotulos multiplos num so case; yield quando o ramo precisa de mais de uma instrucao
Duration prazo = switch (canal) {
    case APP, WEB -> Duration.ofDays(1);
    case LOJA -> Duration.ofDays(3);
    case TELEFONE -> {
        Duration analiseManual = Duration.ofDays(2);
        yield analiseManual.plus(Duration.ofDays(3));
    }
};
```

Com `default` sobre um enum, um valor novo passa sem aviso — prefira listar todos os casos. Fonte executável:
`Tarifacao.prazoEstorno` em [Tarifacao](../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Tarifacao.java);
tabela completa (`case null`, guarda `when`, `_`, constantes qualificadas, o que ainda é preview) em
[sealed-e-switch](references/sealed-e-switch.md).

**Quando usar:** sempre que o `switch` produz um valor a ser atribuído/retornado.
**Quando evitar:** quando cada ramo só executa um efeito colateral distinto (sem produzir valor) — um
`switch` statement com `->` (ainda sem fallthrough) já resolve, sem precisar de `yield`.

## 5. Text blocks

String literal multilinha delimitada por `"""`, que preserva formatação e remove indentação
incidental automaticamente.

```java
// Java classico: concatenacao com \n, dificil de ler e de manter formatado
String sql = "SELECT p.id, p.nome, p.preco\n" +
             "FROM produto p\n" +
             "WHERE p.ativo = true\n" +
             "ORDER BY p.nome";

// Java moderno: text block preserva a formatacao do SQL, sem concatenacao
String sql2 = """
        SELECT p.id, p.nome, p.preco
        FROM produto p
        WHERE p.ativo = true
        ORDER BY p.nome
        """;
```

**Quando usar:** SQL, JSON, HTML ou qualquer conteúdo com quebras de linha significativas (fixtures
de teste, payloads de exemplo).
**Quando evitar:** strings de uma linha (overhead sintático sem ganho) ou quando o conteúdo exige
indentação dinâmica incompatível com a remoção automática de whitespace incidental do text block —
nesses casos, `String.format`/concatenação continuam mais previsíveis.

## 6. Virtual threads

Threads leves gerenciadas pela JVM (não mapeadas 1:1 com thread do SO), permitindo dezenas de
milhares de threads concorrentes com baixo custo. Ativação em aplicações Spring Boot:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Quando ajudam:** cargas **I/O-bound** com muitas requisições concorrentes — chamadas HTTP a outros
serviços, queries JDBC, leitura de arquivo. Cada requisição ocupa uma virtual thread barata enquanto
espera o I/O, sem esgotar um pool fixo de threads do SO.

**Quando NÃO ajudam / atenção:**
- **CPU-bound:** processamento pesado (cálculo, criptografia, serialização grande) não ganha nada —
  o gargalo é a CPU, não a espera por I/O; o número de núcleos continua sendo o limite real.
- **Pinning no Java 25:** desde o JDK 24 ([JEP 491](https://openjdk.org/jeps/491)) bloquear dentro de
  `synchronized` ou em `Object.wait()` **não** prende mais a carrier thread — a recomendação antiga de
  trocar todo `synchronized` por `ReentrantLock` para evitar pinning não vale para Java 25. Pinning ainda
  ocorre em código nativo/FFM (JNI, upcalls) e em casos raros de inicialização de classe; detecte com o
  evento JFR `jdk.VirtualThreadPinned` em vez de presumir.
- **Virtual threads não aumentam capacidade de recursos:** 10 conexões no banco continuam 10 conexões.
  Milhares de virtual threads esperando o pool só deslocam a fila para dentro da JVM. Limite a admissão
  (`Semaphore`/bulkhead por recurso), dê timeout à aquisição de conexão e rejeite o excedente — ver
  `resiliencia-controle-fluxo-java`.
- **Não faça pool de virtual threads:** crie uma por tarefa (`Executors.newVirtualThreadPerTaskExecutor()`);
  para limitar concorrência use semáforo, não um pool de tamanho fixo.
- **`ThreadLocal` com muitas threads:** caches por thread (ex.: buffers grandes) multiplicam memória quando
  há centenas de milhares de virtual threads; prefira objetos com escopo explícito.

```java
// Limite real de concorrência com virtual threads: o semáforo, não o executor.
var conexoesDisponiveis = new Semaphore(10);
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var pedido : pedidos) {
        executor.submit(() -> {
            // Espera limitada: excedente falha de forma visível em vez de enfileirar sem fim.
            if (!conexoesDisponiveis.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                throw new RejectedExecutionException("Banco saturado");
            }
            try {
                return repositorio.salvar(pedido);
            } finally {
                conexoesDisponiveis.release();
            }
        });
    }
}
```

## 7. `var`

Inferência de tipo para variável **local** (desde o Java 10): o compilador deduz o tipo a partir do
lado direito da atribuição. Não é tipagem dinâmica — o tipo continua fixo e checado em compilação.

```java
// Java classico: tipo repetido nos dois lados da atribuicao
List<Produto> produtos = new ArrayList<Produto>();

// Java moderno: var - tipo obvio pelo lado direito, sem repeticao
var produtos2 = new ArrayList<Produto>();

// ERRADO - var esconde o tipo de retorno; quem le precisa abrir o metodo para saber o que e
var resultado = servicoExterno.processar(request);

// CORRETO - tipo explicito quando o retorno do metodo nao deixa o tipo obvio para quem le
ResultadoProcessamento resultado2 = servicoExterno.processar(request);
```

**Quando usar:** o tipo já é óbvio pelo lado direito. **Quando evitar:** quando `var` obscurece o
tipo para quem lê o código. Vale só para variáveis locais — não existe `var` em campo, parâmetro de
método ou tipo de retorno.

## 8. Guia de migração por versão

| Vindo do Java | Já pode usar | Ainda falta para chegar no 25 |
|---|---|---|
| **8** | lambdas, streams, `Optional` (já eram do próprio 8) | records, sealed classes, pattern matching, switch expressions, text blocks, `var`, virtual threads |
| **11** | tudo do 8 + `var` (inferência de tipo local, finalizada no LTS 11) | records, sealed classes, pattern matching, switch expressions, text blocks, virtual threads |
| **17** | tudo do 11 + records, sealed classes, switch expressions, text blocks, pattern matching para `instanceof` (todos finalizados até o LTS 17) | pattern matching para `switch`, record patterns, virtual threads (finalizados no LTS 21) |
| **21** | tudo do 17 + pattern matching completo para `switch`, record patterns, virtual threads (LTS 21 finaliza o Project Loom) | `_` para variáveis e padrões sem nome (JEP 456, final no 22); o resto desta skill o 21 já cobre |

**Nota JDK 25 + Spring Boot:** nenhuma feature desta lista é obrigatória ao migrar do 21 para o 25 —
o ponto de atenção é o entrypoint da aplicação. O JDK 25 introduz instance main methods (classe sem
nome, `void main()` sem `args`), mas o **plugin do Spring Boot ainda não suporta `void main()` do
JDK 25** — o `spring-boot-maven-plugin` (versão 4.0.7, fixa neste catálogo) exige o entrypoint
clássico para gerar o jar executável: com `void main()` o goal `repackage` falha com "Unable to find main class"
(verificado em 2026-10-06). Por isso a classe principal mantém `public static void main(String[] args)` dentro de
`@SpringBootApplication` — não troque por `void main()` em aplicações Spring Boot deste catálogo.

## 9. Validação

Depois de aplicar qualquer modernização em código real (trocar uma classe por record, introduzir
sealed, aplicar pattern matching, etc.), peça revisão ao agent `java-revisor` antes de considerar a
mudança concluída. Ele aplica o checklist da skill `revisao-de-codigo-java` e confirma que a
modernização não alterou o comportamento nem quebrou o contrato existente.
