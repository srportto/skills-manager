# Sealed classes e switch moderno (Java 25, sem preview)

Referência detalhada da skill `java-moderno`. Todo trecho abaixo é cópia de uma classe executável do módulo
[linguagem](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java), com teste de
mesmo nome em `src/test/java`.

## 1. Sealed: o que fecha e por quê

Um tipo `sealed` declara quem pode estendê-lo ou implementá-lo. O compilador passa a conhecer **todos** os subtipos,
o que permite `switch` exaustivo sem `default` (seção 4).

- `permits` lista os subtipos. Pode ser omitido quando todos estão no mesmo arquivo (como abaixo).
- Cada subtipo direto declara como continua a hierarquia: `final` (fecha), `sealed` (fecha com nova lista) ou
  `non-sealed` (reabre para qualquer um). Records e enums já são `final`: são as folhas naturais.
- Subtipos ficam no mesmo módulo nomeado do tipo selado ou, sem módulos, no mesmo pacote.
- Introspecção: `Class.isSealed()` e `Class.getPermittedSubclasses()` (usados em `PagamentoTest`).

```java
// Interface selada com permits implícito: só os records aninhados implementam Pagamento
public sealed interface Pagamento {
    BigDecimal valor();

    record Pix(String chave, BigDecimal valor) implements Pagamento {
        public Pix {
            Objects.requireNonNull(chave);
            exigirPositivo(valor);
        }
    }

    record Cartao(Bandeira bandeira, int parcelas, BigDecimal valor) implements Pagamento { /* validação */ }

    record Boleto(String linhaDigitavel, BigDecimal valor) implements Pagamento { /* validação */ }

    enum Bandeira { VISA, MASTERCARD, ELO }
}
```

Fonte: [Pagamento](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java).

Classe abstrata selada com `permits` explícito e as duas escolhas de subclasse:

```java
public abstract sealed class Notificacao permits Notificacao.Email, Notificacao.Webhook {
    // final: ninguém estende Email
    public static final class Email extends Notificacao { /* endereco */ }

    // non-sealed: integrações podem estender (ex.: webhook assinado) sem alterar esta hierarquia
    public static non-sealed class Webhook extends Notificacao { /* url */ }

    // Exaustivo sem default: o caso Webhook cobre também as subclasses dele
    public static String destino(Notificacao notificacao) {
        return switch (notificacao) {
            case Email email -> "email:" + email.endereco();
            case Webhook webhook -> "webhook:" + webhook.url();
        };
    }
}
```

Fonte: [Notificacao](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Notificacao.java);
`NotificacaoTest` estende `Webhook` para provar que `non-sealed` reabre a hierarquia.

**Quando evitar:** tipo que plugins ou módulos de terceiros precisam estender — selar transfere ao autor a
obrigação de conhecer todos os casos.

## 2. Modelar resultados com tipo selado

Resultado de chamada remota como tipo selado obriga quem consome a tratar o caso difícil. Aqui, `Desconhecida`
(timeout sem resposta) não pode ser esquecido, e a ação correta é reconciliar, nunca repetir a cobrança às cegas
(ver `resiliencia-controle-fluxo-java`).

```java
public sealed interface ResultadoCobranca {
    record Aprovada(String autorizacao) implements ResultadoCobranca {}
    record Recusada(String motivo) implements ResultadoCobranca {}
    // Timeout ou queda sem resposta: a cobrança pode ter acontecido no provedor
    record Desconhecida(String chaveIdempotencia) implements ResultadoCobranca {}

    enum Acao { CONCLUIR, OFERECER_OUTRO_MEIO, INFORMAR_RECUSA, RECONCILIAR }

    static Acao proximaAcao(ResultadoCobranca resultado) {
        return switch (resultado) {
            case Aprovada _ -> Acao.CONCLUIR;
            case Recusada(String motivo) when "SALDO_INSUFICIENTE".equals(motivo) -> Acao.OFERECER_OUTRO_MEIO;
            case Recusada _ -> Acao.INFORMAR_RECUSA;
            case Desconhecida _ -> Acao.RECONCILIAR;
        };
    }
}
```

Fonte: [ResultadoCobranca](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/ResultadoCobranca.java).

## 3. Switch moderno — referência rápida

| Recurso | Desde (final) | Exemplo |
|---|---|---|
| Rótulo `->` sem fallthrough | 14 (JEP 361) | `case LOJA -> Duration.ofDays(3);` |
| Rótulos múltiplos | 14 | `case APP, WEB -> Duration.ofDays(1);` |
| `yield` em bloco | 14 | `case TELEFONE -> { ...; yield analiseManual.plus(...); }` |
| Switch expression exaustivo sobre enum, sem `default` | 14 | `prazoEstorno(Canal)` |
| Padrão de tipo | 21 (JEP 441) | `case Pagamento.Pix p ->` |
| Padrão de record (desestruturação) | 21 (JEP 440) | `case Pagamento.Cartao(_, int parcelas, BigDecimal valor) ->` |
| Guarda `when` | 21 | `case Recusada(String motivo) when "SALDO_INSUFICIENTE".equals(motivo) ->` |
| `case null` e `case null, default` | 21 | `case null -> throw new IllegalArgumentException(...)` |
| Constante de enum qualificada no rótulo | 21 | `case Tarifacao.Canal.APP ->` |
| Variável/padrão sem nome `_` | 22 (JEP 456) | `case Pagamento.Boleto _ ->`, `Cartao(_, _, BigDecimal valor)` |
| Padrões primitivos em `switch`/`instanceof` | JEP 507 — **preview no Java 25** | não usar: o catálogo compila sem `--enable-preview` |

Tudo junto, num só método:

```java
public static BigDecimal taxa(Pagamento pagamento) {
    return switch (pagamento) {
        // Sem "case null", um pagamento nulo lançaria NullPointerException
        case null -> throw new IllegalArgumentException("Pagamento ausente");
        case Pagamento.Pix _ -> BigDecimal.ZERO;
        // A guarda refina o padrão e precisa vir antes do caso sem guarda do mesmo tipo (dominância)
        case Pagamento.Cartao(_, int parcelas, BigDecimal valor) when parcelas > 1 ->
                percentual(valor, TAXA_CARTAO.add(ACRESCIMO_POR_PARCELA.multiply(BigDecimal.valueOf(parcelas - 1))));
        case Pagamento.Cartao(_, _, BigDecimal valor) -> percentual(valor, TAXA_CARTAO);
        case Pagamento.Boleto _ -> TARIFA_BOLETO;
    };
}

public static Duration prazoEstorno(Canal canal) {
    return switch (canal) {
        case APP, WEB -> Duration.ofDays(1);
        case LOJA -> Duration.ofDays(3);
        case TELEFONE -> {
            // Ramo com mais de uma instrução devolve o valor com yield
            Duration analiseManual = Duration.ofDays(2);
            yield analiseManual.plus(Duration.ofDays(3));
        }
    };
}
```

Fonte: [Tarifacao](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Tarifacao.java).

## 4. Regras que o compilador impõe

- **Exaustividade:** todo switch expression, e todo switch (inclusive statement) que usa padrões ou `case null`,
  precisa cobrir todos os valores possíveis. Sobre tipo selado ou enum, isso se resolve listando os casos, sem
  `default`. Faltar um subtipo é erro: *"the switch expression does not cover all possible input values"*.
- **Dominância:** um caso que já cobre outro não pode vir antes dele. `case Meio m` antes de `case Pix p` é erro
  (*"this case label is dominated by a preceding case label"*); pela mesma regra, o caso com guarda `when` vem
  antes do caso sem guarda do mesmo tipo.
- **`null`:** sem `case null`, o switch lança `NullPointerException` para entrada nula (comportamento clássico).
  Com ele, o `null` vira um caso explícito.

Prova: [PagamentoTest](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java)
compila em memória, com `--release 25`, um switch sem um dos subtipos e outro com o caso geral antes do específico,
e confere os dois erros acima.

## 5. Armadilhas

- `default` num switch sobre tipo selado **esconde** subtipos novos: o build deixa de avisar quando alguém
  acrescenta um tipo ao `permits`. Use `default` só quando o domínio é aberto de verdade.
- `instanceof` seguido de cast manual continua compilando, mas perde a verificação de exaustividade; prefira o
  switch de padrões quando a decisão é "qual dos tipos conhecidos".
- Pattern matching decide **por tipo concreto**. Quando o comportamento pertence ao próprio tipo (cada meio sabe
  calcular a própria taxa e ninguém mais precisa decidir por tipo), um método polimórfico continua sendo o desenho
  mais simples.
- Padrões primitivos (`case int i when i > 10`) ainda são preview no Java 25 (JEP 507) e não fazem parte do
  catálogo.

## 6. Antes/depois (migrado do SKILL.md)

Comparativo clássico vs moderno que antes ficava no corpo do SKILL.md; o modelo de Pagamento é o mesmo do módulo executável.

### Sealed classes/interfaces

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
[Pagamento](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java).

**Quando usar:** hierarquia de domínio finita e conhecida (tipos de pagamento, estados de um fluxo) —
casa direto com switch exaustivo (item 3), que quebra o build se um tipo novo não for tratado.
**Quando evitar:** hierarquia que precisa ser extensível por módulos/plugins externos que o autor do
tipo selado não controla.


### Switch expressions

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
`Tarifacao.prazoEstorno` em [Tarifacao](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Tarifacao.java);
tabela completa (`case null`, guarda `when`, `_`, constantes qualificadas, o que ainda é preview) em
[sealed-e-switch](sealed-e-switch.md).

**Quando usar:** sempre que o `switch` produz um valor a ser atribuído/retornado.
**Quando evitar:** quando cada ramo só executa um efeito colateral distinto (sem produzir valor) — um
`switch` statement com `->` (ainda sem fallthrough) já resolve, sem precisar de `yield`.
