# qualidade-codigo-java eval 4 — execução A

Executor independente: subagente `refatorador-java` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.

Prompt: "Esta ContaInvestimentoBloqueada estende Conta e sobrescreve sacar lançando UnsupportedOperationException; o pagarFatura(Conta) explode em runtime. Corrija."

## Saída bruta

### Diagnóstico

O sintoma é uma violação de LSP (Liskov), com ISP por trás dela. É o smell "override que lança `UnsupportedOperationException`" da skill `qualidade-codigo-java`, tratado em `references/solid.md` (seção L). `Conta` promete `sacar`, e `ContaInvestimentoBloqueada` não cumpre essa promessa. O erro só aparece em runtime, quando `pagarFatura(Conta)` chama `sacar`.

Descarto três saídas:
- **Override vazio:** troca falha ruidosa por falha silenciosa, porque o saque não acontece e ninguém sabe.
- **`instanceof ContaInvestimentoBloqueada` no cliente:** espalha o conhecimento do subtipo e quebra OCP.
- **Capturar a exceção no `pagarFatura`:** esconde o problema de modelagem.

### Correção proposta: modelar a capacidade

A capacidade de sacar vira um tipo próprio. O cliente passa a depender de quem garante o saque, e o erro deixa de compilar.

```java
// Conta passa a ser o contrato mínimo (somente leitura)
public interface Conta {
    BigDecimal saldo();
}

// Capacidade de movimentar: só quem realmente saca implementa
public interface ContaMovimentavel extends Conta {
    void sacar(BigDecimal valor);
}

public final class ContaCorrente implements ContaMovimentavel {
    // lógica atual de sacar, inalterada
}

// Não promete sacar; sem override de exceção
public final class ContaInvestimentoBloqueada implements Conta {
    // apenas saldo() e o que for próprio da conta bloqueada
}

// O tipo do parâmetro garante a capacidade
void pagarFatura(ContaMovimentavel conta, BigDecimal valor) {
    conta.sacar(valor);
}
```

Se `Conta` hoje é classe concreta com estado (`protected BigDecimal saldo`), há dois caminhos:
- **Extrair a interface `Conta`** e fazer a classe concreta virar `ContaCorrente`, como acima.
- **Manter uma classe base só com o estado comum.** Nesse caso, `sacar` sobe apenas para a subclasse ou interface movimentável. Prefira composição e classes `final`.

### Passos (refactoring de tipos, sem mudar o comportamento de quem já saca)

1. Rodar os testes existentes e anotar a contagem antes. Se `pagarFatura` e `sacar` não estiverem cobertos, escrever antes um teste de caracterização do saque normal, da validação de saldo e do `pagarFatura` com conta que saca.
2. Extrair `Conta` (leitura) e `ContaMovimentavel` (`sacar`). Fazer o `mvn -q compile` e rodar os testes.
3. Mover o `sacar` real para as implementações movimentáveis. Remover o override que lança exceção.
4. Trocar o parâmetro de `pagarFatura` para `ContaMovimentavel` e ajustar os chamadores. O compilador aponta quem passava conta bloqueada, e esses pontos passam a ser decisões explícitas de negócio.
5. Limpar os imports (`remover-imports-nao-usados`) e encaminhar o diff ao `java-revisor`.

### Como verificar

- `mvn -q compile` e os testes do módulo, antes e depois, comparando as contagens.
- Teste novo: tentar passar `ContaInvestimentoBloqueada` a `pagarFatura` deve **não compilar**. Dá para provar isso com compilação em memória via `javax.tools`, no formato do `PagamentoTest` citado em `testes-sistemas-java`.
- Teste de `pagarFatura` com `ContaCorrente`: saldo reduzido, e saldo insuficiente com o mesmo comportamento de antes.
- `grep` por `UnsupportedOperationException` e `instanceof Conta` no pacote deve retornar vazio.

### Ressalva de escopo

Este item pede uma correção de bug (explosão em runtime) e muda a assinatura pública de `pagarFatura`. Isso é mais do que refactoring puro, então vale registrar como mudança separada. Se algum chamador legítimo precisava mesmo passar conta bloqueada, essa regra de negócio (por exemplo, rejeitar com uma exceção de domínio) tem de ser decidida por quem é dono do domínio, e não inventada no refactoring.

### Invariantes

O código não é consumer/listener, então a tabela dos quatro invariantes não se aplica. Os invariantes relevantes são:
- O comportamento de `sacar` para contas movimentáveis, garantido pelo teste de caracterização.
- Nenhuma transação, ack, ordem ou recurso em `finally` é alterado.

Nenhum arquivo foi criado ou alterado, conforme pedido.
