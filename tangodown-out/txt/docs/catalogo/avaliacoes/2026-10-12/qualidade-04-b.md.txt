# qualidade-codigo-java eval 4 — execução B

Executor independente: subagente `refatorador-java` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.

Prompt: "Esta ContaInvestimentoBloqueada estende Conta e sobrescreve sacar lançando UnsupportedOperationException; o pagarFatura(Conta) explode em runtime. Corrija."

## Saída bruta

### Decisão

Isto é uma violação do Princípio de Substituição de Liskov (LSP), e a causa é a hierarquia, não o `pagarFatura`. `Conta` promete `sacar`, e `ContaInvestimentoBloqueada` herda essa promessa e a quebra com `UnsupportedOperationException`. O erro só aparece em runtime. Fonte: `skills/qualidade-codigo-java/references/solid.md`, seção L, e a linha 51 do `SKILL.md`.

Não faça nenhuma destas duas correções:
- **Override vazio:** troca uma falha ruidosa por uma silenciosa. O saque "acontece" sem debitar nada e a fatura fica como paga. Pior que a exceção.
- **`instanceof ContaInvestimentoBloqueada` no cliente:** espalha o conhecimento do subtipo e quebra o OCP. Cada novo tipo bloqueado exigiria outro `if`.

A correção é modelar a capacidade. O cliente passa a depender do tipo que garante `sacar`, e o erro vira erro de compilação.

### Código proposto

```java
// Conta deixa de prometer saque
public interface Conta {
    BigDecimal saldo();
}

// capacidade de saque, separada
public interface ContaMovimentavel extends Conta {
    void sacar(BigDecimal valor);
}

public final class ContaCorrente implements ContaMovimentavel {
    private BigDecimal saldo;

    @Override public BigDecimal saldo() { return saldo; }

    @Override public void sacar(BigDecimal valor) { saldo = saldo.subtract(valor); }
}

// não promete sacar: sem override com throw
public final class ContaInvestimentoBloqueada implements Conta {
    private final BigDecimal saldo;

    public ContaInvestimentoBloqueada(BigDecimal saldo) { this.saldo = saldo; }

    @Override public BigDecimal saldo() { return saldo; }
}

// o tipo do parâmetro garante a capacidade
void pagarFatura(ContaMovimentavel conta, BigDecimal valor) {
    conta.sacar(valor);
}
```

Pontos de atenção:
- As classes concretas ficam `final`. A skill prefere composição e `final` a herança.
- Se `Conta` hoje é classe com estado e lógica compartilhada (`saldo`, validações), extraia essa parte para uma classe base ou para composição em `ContaCorrente`. A interface não deve carregar estado.
- Os chamadores de `pagarFatura` que passavam uma `Conta` genérica vão deixar de compilar. Isso é o efeito desejado, porque cada um mostra um ponto que antes explodia em runtime. Cada chamador deve receber `ContaMovimentavel` ou tratar o caso "conta bloqueada" como regra de negócio explícita.
- Se o negócio exige tentar sacar de uma conta bloqueada e recusar, isso é regra de domínio. Nesse caso, mantenha `sacar` no contrato e use uma exceção de domínio documentada (por exemplo `ContaBloqueadaException`, com contexto e causa), não `UnsupportedOperationException`. Só vale se a recusa fizer parte do contrato de `Conta`.

### Como verificar

Esta mudança altera tipos públicos, então não é um refactoring puro. A mudança de contrato é intencional e deve ser declarada. A skill pede teste verde antes de mexer.

1. **Antes:** rode `mvn -q test` no módulo e registre a contagem. Se não houver teste cobrindo `pagarFatura`, escreva um de caracterização primeiro, seguindo `testes-sistemas-java`. Ele deve provar o defeito: `pagarFatura` com `ContaInvestimentoBloqueada` lança `UnsupportedOperationException`.
2. **Compilação:** `mvn -q compile`. Falhas de compilação nos chamadores de `pagarFatura` são esperadas e devem ser tratadas uma a uma. Elas provam que o caminho de erro foi eliminado.
3. **Testes depois:**
   - `ContaCorrente` passa por `pagarFatura` e debita o saldo.
   - Um teste de compilação ou ArchUnit garante que `ContaInvestimentoBloqueada` não implementa `ContaMovimentavel`. Não use `instanceof` nem reflexão no código de produção.
   - Nenhum `UnsupportedOperationException` nem override vazio resta nas classes de conta (`grep`).
4. Limpe os imports das classes alteradas (`remover-imports-nao-usados`) e rode `mvn -q compile` de novo.
5. Encaminhe o diff ao `java-revisor` em modo `tempestivo`. O item 6.7 do checklist (contrato de subtipo) deve ficar sem achados.

Não alterei nenhum arquivo. O código citado não estava anexado, então tudo acima parte da sua descrição. Os nomes `Conta`, `ContaMovimentavel` e `ContaCorrente` seguem o exemplo da própria reference.
