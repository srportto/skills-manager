# Object Calisthenics e smells (resumo)

Leia este arquivo ao decidir se uma tecnica de Object Calisthenics ou refactoring do Fowler vale a pena (tabela quando ajuda / quando nao forcar) e a regra de que refatorar preserva comportamento. Os exemplos completos estao em refatoracoes-fowler.md.

## Object Calisthenics, smells e refactorings do Fowler (resumo)

Exemplos completos (Codigo Nao Aderente / Violacao / Refatoracao) em
[refatoracoes e Object Calisthenics](refatoracoes-fowler.md). Sao **heuristicas de design**, nao bugs:
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


## Exemplo antes/depois (Guard Clause / Don't Use Else)

**[Codigo Nao Aderente]:**
```java
public BigDecimal desconto(Pedido pedido) {
    if (pedido != null) {
        if (pedido.ehVip()) {
            return pedido.total().multiply(new BigDecimal("0.10"));
        } else {
            return BigDecimal.ZERO;
        }
    } else {
        throw new IllegalArgumentException("pedido obrigatorio");
    }
}
```

**[Exemplo de Refatoracao]:**
```java
// guard clause + retorno antecipado: caminho feliz sem aninhamento
private static final BigDecimal PERCENTUAL_DESCONTO_VIP = new BigDecimal("0.10");

public BigDecimal desconto(Pedido pedido) {
    if (pedido == null) throw new IllegalArgumentException("pedido obrigatorio");
    if (!pedido.ehVip()) return BigDecimal.ZERO;
    return pedido.total().multiply(PERCENTUAL_DESCONTO_VIP);
}
```

Mais exemplos executaveis de refactoring (Tell Don't Ask, Value Objects, Replace Conditional with Polymorphism): ver [refatoracoes-fowler.md](refatoracoes-fowler.md).
