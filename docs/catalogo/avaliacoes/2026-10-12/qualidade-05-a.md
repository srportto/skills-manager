# qualidade-codigo-java eval 5 — execução A

Executor independente: subagente `refatorador-java` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.

Prompt: "Aplique SOLID neste service: crie uma interface para cada classe e quebre em uma classe por método."

## Saída bruta

**Decisão: não aplicar o pedido como está.** Recomendo recusar a versão mecânica ("uma interface para cada classe" e "uma classe por método") e propor um refactoring dirigido por sintoma.

**Por quê (base no catálogo):**
- A skill `qualidade-codigo-java` exige um smell concreto antes de refatorar. "Aplicar SOLID" não é um smell.
- Interface com uma única implementação "para o futuro" é o sintoma KISS/YAGNI da tabela de decisão. O remédio listado é Inline, ou seja, o oposto do que foi pedido.
- A skill `padroes-de-projeto-java` (`references/quando-nao-aplicar.md`) trata esse caso como over-engineering.
- Uma classe por método gera classes anêmicas e muita indireção. Isso piora a legibilidade e o contexto do LLM, que é o princípio-mestre do catálogo. O Object Calisthenics também é heurística, não regra: "classe coesa com 3 atributos não precisa ser quebrada".
- Ainda não vi o código, então não consigo apontar o smell real.

**O que eu faria:**
1. Pedir o código e o motivo concreto: método longo, `switch` por tipo, mistura de responsabilidades (regra + I/O + persistência), dificuldade de testar?
2. Verificar os testes existentes. Se não cobrirem o trecho a alterar, escrever antes testes de caracterização (`testes-sistemas-java`). Trecho sem cobertura fica fora do diff e vira pendência declarada.
3. Aplicar passos pequenos, com `mvn -q compile` e os testes do módulo após cada passo:
   - Extract Method em 2 ou 3 trechos, em vez de um método gigante.
   - Se houver responsabilidades distintas (por exemplo, cálculo de regra versus envio de notificação), Extract Class apenas para essa fronteira coesa. A classe extraída é concreta e injetada por construtor, sem interface.
   - `switch` por tipo: Replace Conditional with Polymorphism ou Strategy por lista injetada (`strategy-lista-injetada.md`).
   - Só criar interface onde houver 2 ou mais implementações reais, ou uma fronteira de porta (infraestrutura) exigida por `arquitetura-limpa-java`.
4. Limpar imports (`remover-imports-nao-usados`) e encaminhar o diff ao `java-revisor`.

**Exemplo de forma-alvo (ilustrativo, sem o código real):**
```java
// Antes: PedidoService com validar(), calcularTotal(), persistir() e notificar() num método de 120 linhas.
// Depois: o service orquestra; só o que tem responsabilidade distinta vira classe.
public class PedidoService {
    private final CalculadoraDeTotal calculadora;   // classe concreta, sem interface
    private final PedidoRepository repository;      // porta já existente
    private final NotificadorDePedido notificador;

    public Pedido criar(NovoPedido novo) {
        var pedido = Pedido.de(novo, calculadora.total(novo.itens()));
        repository.salvar(pedido);
        notificador.pedidoCriado(pedido);
        return pedido;
    }
}
```
Preservando a fronteira de transação (`@Transactional` continua no mesmo método público) e a ordem salvar, depois notificar.

**Invariantes a preservar e como verificar:**
- Transação: a anotação e o ponto de commit não mudam. Verificar com teste de integração ou slice (`@DataJpaTest`).
- Ordem de efeitos: salvar antes de notificar, coberta por teste de caracterização com mock em ordem (`InOrder`).
- Exceções e liberação de recursos em `finally` mantidas.
- Se o service for consumer/listener (Kafka, SQS, Redis Streams), declarar sempre os quatro invariantes: ordem por partição/chave, momento do ack/commit, idempotência e cancelamento/revogação.

**Como verificar:**
- Antes e depois: `mvn -q test` no módulo, com as contagens registradas.
- A cada passo: `mvn -q compile`.
- No fim: revisão do `java-revisor` em modo `tempestivo`.
- Se alterar o catálogo: `mvn -f validation/java/pom.xml verify`.

**Pendência:** preciso do código e do smell que motivou o pedido antes de qualquer alteração. Nenhum arquivo foi criado ou modificado.
