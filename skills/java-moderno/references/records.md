# Records

Leia este arquivo quando precisar modelar dado imutável (DTO, value object, chave composta) ou decidir entre `record` e classe comum; traz o antes/depois, a validação em construtor compacto e os limites do record.

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
[linguagem](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java)).

**Quando usar:** DTO de request/response, value object, chave composta imutável.
**Quando evitar:** quando precisa de mutabilidade (setter após a criação) ou de herdar de uma classe —
records são implicitamente `final` e só podem implementar interfaces, nunca estender outra classe.


## Validação no construtor compacto

O construtor compacto roda antes da atribuição dos campos: é o lugar de validar e normalizar, sem repetir a lista de parâmetros.

```java
// Antes: record aceita estado inválido e o erro só aparece longe da criação
public record Pix(String chave, BigDecimal valor) {}
new Pix(null, new BigDecimal("-10")); // compila e "funciona"

// Depois: o invariante vive no tipo; ninguém consegue criar um Pix inválido
public record Pix(String chave, BigDecimal valor) {
    public Pix {
        Objects.requireNonNull(chave, "chave é obrigatória");
        if (valor.signum() <= 0) throw new IllegalArgumentException("valor deve ser positivo");
    }
}
```

Prova executável: [PagamentoTest](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java)
exercita as validações dos records de [Pagamento](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java).

Atenção: o record é imutável só de forma rasa. Componente mutável (`List`, `Date`) exige cópia defensiva no
construtor compacto (`itens = List.copyOf(itens);`).
