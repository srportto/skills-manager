# Text blocks e `var`

Leia este arquivo quando for escrever SQL/JSON/HTML multilinha em string, ou decidir se `var` melhora ou piora a legibilidade de uma variável local.

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


## Text block com `formatted` e escapes

```java
// Antes: JSON de fixture montado por concatenação, aspas escapadas
String json = "{\"id\":\"" + id + "\",\"valor\":" + valor + "}";

// Depois: text block com formatted(); aspas não precisam de escape
String json = """
        {"id":"%s","valor":%s}
        """.formatted(id, valor);
```

Use `\` no fim da linha para continuar sem quebra e `\s` para preservar espaço final. Interpolar dado de usuário em
SQL continua proibido: text block não substitui query parametrizada.
