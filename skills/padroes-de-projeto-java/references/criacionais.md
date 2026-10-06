# Padrões criacionais em Java

Cada entrada: problema, exemplo ANTES/DEPOIS resumido (Java 25) e quando usar/evitar. Prefira o
construtor direto quando a criação não tem variação nem regra.

## Builder

**Problema:** objeto com muitos parâmetros opcionais; construtores telescópicos confundem a ordem.

```java
// ANTES: qual argumento é qual?
var pedido = new Pedido("c-1", null, true, false, 3, null);

// DEPOIS: record imutável + builder só onde há muitos opcionais
public record Pedido(String cliente, String cupom, boolean expresso, int parcelas) {
    public static Builder para(String cliente) { return new Builder(cliente); }

    public static final class Builder {
        private final String cliente;
        private String cupom;
        private boolean expresso;
        private int parcelas = 1;

        private Builder(String cliente) { this.cliente = cliente; }
        public Builder cupom(String valor) { cupom = valor; return this; }
        public Builder expresso() { expresso = true; return this; }
        public Builder parcelas(int valor) { parcelas = valor; return this; }
        public Pedido build() { return new Pedido(cliente, cupom, expresso, parcelas); }
    }
}
```

**Use** com 4+ opcionais ou validação no `build()`. **Evite** em records de 2–3 campos.

## Factory Method

**Problema:** o tipo concreto depende de dado em runtime e a regra de escolha se repete.

```java
// DEPOIS: a regra de criação fica num ponto único; switch exaustivo sobre sealed/enum
static Notificador notificador(Canal canal) {
    return switch (canal) {
        case EMAIL -> new NotificadorEmail();
        case SMS -> new NotificadorSms();
    };
}
```

**Use** quando a decisão de criação tem regra. **Evite** fábrica que só faz `new` (ver SKILL, "Quando NÃO
aplicar"). Em Spring, prefira Strategy por lista injetada.

## Abstract Factory

**Problema:** famílias de objetos que precisam combinar (ex.: cliente + serializador do mesmo provedor).

```java
public interface ProvedorPagamento {
    ClientePagamento cliente();
    ConversorPayload conversor();
}
// Uma implementação por provedor garante que cliente e conversor nunca se misturem.
```

**Use** quando misturar membros de famílias diferentes é um bug. **Evite** com uma única família.

## Singleton

**Problema:** uma instância compartilhada por processo.

```java
// DEPOIS em Spring: o container já gerencia a instância única e permite injeção em testes
@Component
public class RelogioSistema { /* ... */ }
```

**Use** `enum` singleton apenas fora de containers de DI. **Evite** `getInstance()` estático: dificulta
teste e esconde dependência. Instância única também não é quota global entre réplicas.

## Prototype

**Problema:** criar cópias de um objeto pré-configurado caro de montar.

```java
public record Template(String assunto, List<String> destinatarios) {
    public Template { destinatarios = List.copyOf(destinatarios); } // cópia defensiva
    public Template comAssunto(String novo) { return new Template(novo, destinatarios); }
}
```

**Use** records com métodos `withX`/cópia explícita. **Evite** `Cloneable`/`clone()` (cópia rasa e
contrato frágil).
