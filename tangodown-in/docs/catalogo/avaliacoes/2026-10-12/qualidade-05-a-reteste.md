# qualidade-codigo-java eval 5 — execução A (reteste)

Executor independente: subagente `refatorador-java` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.
Versão avaliada: `SKILL.md` com a linha "Pedido de 'aplicar SOLID' sem sintoma" na tabela de decisão.

Prompt: "Aplique SOLID neste service: crie uma interface para cada classe e quebre em uma classe por método."

## Saída bruta

### Decisão: não aplicar o pedido como foi formulado; propor um refactoring dirigido por smell

O pedido tem dois mecanismos mecânicos: "uma interface para cada classe" e "uma classe por método". Nenhum dos dois é SOLID, e a skill `qualidade-codigo-java` e a `padroes-de-projeto-java` (`references/quando-nao-aplicar.md`) os tratam como over-engineering.

- **Interface por classe.** A skill diz que interface com implementação única "para o futuro" é KISS/YAGNI, e o refactoring indicado é Inline. A exceção é uma porta de fronteira com 2 ou mais implementações reais, ou que precise de fake nos testes.
- **Uma classe por método.** Isso fragmenta uma classe coesa. O gotcha da skill diz que Object Calisthenics são heurísticas, e que classe coesa não precisa ser quebrada. O ganho de SRP só existe quando há mais de uma razão para mudar.
- **Ausência de smell concreto.** O fluxo do refatorador exige um smell apontado. "Aplicar SOLID" não é smell. Sem ele, não se refatora.

### O que eu faria

1. **Pedir ou identificar o sintoma.** Exemplos: método longo, `switch` por tipo, dependências demais, acoplamento a infraestrutura, duas razões para mudar (regra de negócio e envio de e-mail, por exemplo).
2. **Rede de segurança.** Rodar `mvn -q test` no módulo e anotar a contagem antes. Se os testes não cobrirem o trecho a alterar, escrever testes de caracterização primeiro (`testes-sistemas-java`). Trecho sem cobertura fica fora do diff e vira pendência declarada.
3. **Passos pequenos, um refactoring por vez**, com `mvn -q compile` mais testes após cada passo. Se quebrar, reverte o passo.
   - Método longo: Extract Method em 2 ou 3 trechos, mantendo-os privados na mesma classe.
   - Parâmetro sem uso: Remove Parameter.
   - `switch` por tipo: Replace Conditional with Polymorphism ou `sealed` + `switch`, ou Strategy por lista injetada.
   - Só extrair uma **classe** quando houver responsabilidade distinta e coesa. Exemplo: um `CalculadoraDeFrete` com estado e regras próprias, nomeado pelo papel e não por `FazXService`.
   - Só criar interface onde existir segunda implementação real ou fronteira externa. Exemplo: um `GatewayPagamento` que tenha cliente real e fake.
4. **Limpar imports** da classe alterada (`remover-imports-nao-usados`: ferramenta nativa primeiro, depois compile).
5. **Encaminhar o diff ao `java-revisor`**, em modo `tempestivo`.

### Invariantes a declarar na entrega

Cada um deve apontar o teste ou trecho que o garante.

- **Ordem de processamento por chave ou partição.** Não se introduz paralelismo novo.
- **Momento do ack/commit.** Continua depois do efeito durável.
- **Idempotência.** A chave de deduplicação e o ponto onde é gravada ou consultada não mudam.
- **Cancelamento e revogação.** Interrupção e `close` seguem com o mesmo commit ou espera.
- **Fronteira de transação.** Cuidado especial: `@Transactional` não pode se perder ao mover código para outra classe, porque a chamada interna (self-invocation) deixa de passar pelo proxy.
- **Liberação de recursos em `finally`.**

Os quatro primeiros valem para consumer ou listener. Se o service não for consumer, declaro "não aplicável" para cada um, com o motivo.

### Como verificar

```bash
mvn -q test -pl <modulo>      # antes e depois, com a mesma contagem de testes verdes
mvn -q compile                # a cada passo
```

Na revisão do diff, conferir também:
- nenhuma interface com implementação única foi criada;
- nenhuma classe tem nome genérico (`Handler`, `Util`);
- as dependências do construtor não cresceram sem motivo.

### Pendência

O código não foi anexado. Preciso do service, do smell que motivou o pedido e do comando de testes. Com isso, devolvo o diff por passo. Se algum bug for encontrado durante o refactoring, ele vira achado para o `java-construtor` e não entra no diff.
