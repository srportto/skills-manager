# Pattern matching

Leia este arquivo quando houver `instanceof` seguido de cast, cadeia de `if/else` por tipo, ou quando precisar desestruturar um record na condição (record patterns).

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
Fonte executável: [Tarifacao](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Tarifacao.java);
regras de exaustividade e dominância, com prova de compilação: [sealed-e-switch](sealed-e-switch.md).

**Quando usar:** sempre que houver `instanceof` seguido de cast manual, e especialmente sobre
hierarquias `sealed`. **Quando evitar:** quando o comportamento por tipo já é resolvido por
polimorfismo simples (método sobrescrito) — pattern matching é para decidir por tipo concreto,
não substitui um bom design orientado a objetos.


## Antes/depois: desestruturar record

```java
// Antes: acessa componente por componente e repete o cast
if (pagamento instanceof Pagamento.Cartao) {
    var cartao = (Pagamento.Cartao) pagamento;
    if (cartao.parcelas() > 1) cobrarParcelado(cartao.valor(), cartao.parcelas());
}

// Depois: o record pattern já entrega os componentes tipados; "_" descarta o que não interessa
if (pagamento instanceof Pagamento.Cartao(_, int parcelas, BigDecimal valor) && parcelas > 1) {
    cobrarParcelado(valor, parcelas);
}
```

Prova executável: [TarifacaoTest](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/TarifacaoTest.java)
(taxa por tipo, guarda `when`, `case null`). Regras de exaustividade e dominância: [sealed-e-switch](sealed-e-switch.md).
