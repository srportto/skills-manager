# qualidade-codigo-java eval 5 — execução B (reteste)

Executor independente: subagente `refatorador-java` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.
Versão avaliada: `SKILL.md` com a linha "Pedido de 'aplicar SOLID' sem sintoma" na tabela de decisão.

Prompt: "Aplique SOLID neste service: crie uma interface para cada classe e quebre em uma classe por método."

## Saída bruta

### Decisão: não aplicar o pedido literalmente

O pedido ("uma interface para cada classe" e "uma classe por método") não é SOLID. É a abstração especulativa que o catálogo manda evitar. Sem um smell concreto, não se refatora (estilo não é bug). Fontes: `qualidade-codigo-java`, linha da tabela "Interface com implementação única 'para o futuro'" (KISS/YAGNI, refactoring Inline), e `padroes-de-projeto-java/references/quando-nao-aplicar.md`. Os Gotchas da skill reforçam que Object Calisthenics são heurísticas e que uma classe coesa não precisa ser quebrada.

Por que cada parte do pedido não se sustenta:
- **Interface por classe:** com uma única implementação, ela só adiciona indireção. Em Spring, o container já injeta a classe concreta, e o Mockito mocka classe. Interface se justifica quando há 2 ou mais implementações reais (aí vale Strategy por lista injetada) ou uma fronteira de porta/adaptador com inversão de dependência de verdade (`arquitetura-limpa-java`).
- **Uma classe por método:** isso fragmenta a coesão e vira um "service anêmico" espalhado em N classes. SRP significa uma razão para mudar, não um método por classe. Também aumenta o custo de contexto para o LLM, contra o princípio-mestre da skill.

### O que fazer em vez disso

Como o código não está anexado, não dá para apontar o smell real. O fluxo seria:
1. **Identificar o sintoma concreto.** Exemplos: service com mais de uma responsabilidade (regra de negócio, I/O e montagem de DTO misturados), métodos longos, `switch` por tipo, muitas dependências no construtor (cerca de 7 ou mais), duplicação de regra.
2. **Rede de segurança.** Rodar os testes existentes com `mvn -q test` no módulo e registrar a contagem. Se eles não cobrirem o trecho a alterar, escrever antes testes de caracterização (`testes-sistemas-java`). Trecho sem cobertura fica fora do diff e vira pendência declarada.
3. **Aplicar um passo pequeno por vez, escolhido pelo sintoma:**
   - Método longo: Extract Method em 2 ou 3 trechos, com métodos privados.
   - Duas responsabilidades distintas e coesas (ex.: cálculo de preço e envio de notificação): Extract Class só dessa responsabilidade, concreta e sem interface.
   - `switch` por tipo com 2 ou mais variantes reais: Strategy por lista injetada, ou `sealed` com `switch`.
   - Primitivos com invariante: Wrap primitives (record ou value object).
   - Interface que já existe com implementação única: Inline.
4. **Validar após cada passo** com `mvn -q compile` e os testes do módulo. Se quebrar, reverter o passo.
5. **Limpar imports** (`remover-imports-nao-usados`) e encaminhar o diff ao `java-revisor` em modo `tempestivo`.

Exemplo do que seria proposto (Extract Class concreta, sem interface), caso o service misture cálculo e notificação:

```java
// antes: PedidoService faz calcularTotal(...) e notificarCliente(...)
// depois: duas classes concretas, cada uma com uma razão para mudar
@Service
class CalculadoraTotalPedido { BigDecimal calcular(Pedido pedido) { /* igual ao original */ } }

@Service
class PedidoService {
    private final CalculadoraTotalPedido calculadora;
    private final NotificadorCliente notificador;
    // construtor; o método público orquestra e mantém a fronteira @Transactional
}
```

### Invariantes a declarar na entrega

Se o service for transacional, a fronteira `@Transactional` deve permanecer no método público de entrada. Extrair para outro bean muda a transação e o proxy: chamada interna não passa pelo proxy, e chamada entre beans pode abrir ou juntar transação diferente. Se o service for consumer/listener, a entrega deve listar sempre os quatro invariantes:
- Ordem por partição/chave.
- Momento do ack/commit.
- Idempotência.
- Cancelamento/revogação.

### Como verificar

- `mvn -q compile` e `mvn -q test` do módulo, antes e depois, comparando as contagens.
- Mutação rápida: quebrar a linha do invariante e confirmar que o teste de caracterização falha.
- Revisão do `java-revisor`.

### Pergunta ao usuário

Qual é o problema concreto que motivou o pedido? Pode ser dificuldade de testar, service grande ou acoplamento. Com o código ou o sintoma em mãos, aplico o refactoring mínimo adequado.
