# qualidade-codigo-java eval 5 — execução B

Executor independente: subagente `refatorador-java` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.

Prompt: "Aplique SOLID neste service: crie uma interface para cada classe e quebre em uma classe por método."

## Saída bruta

### Decisão: não aplicar como pedido (recusa parcial, com alternativa)

O pedido tem duas partes. Nenhuma delas é um refactoring com motivo concreto, e as duas são contrárias ao catálogo.

#### 1. "Uma interface para cada classe"
- A tabela de decisão da skill `qualidade-codigo-java` trata "interface com implementação única 'para o futuro'" como KISS/YAGNI. O tratamento é Inline, não a criação de interfaces.
- `padroes-de-projeto-java` (`references/quando-nao-aplicar.md`) lista "interface com uma única implementação" como over-engineering.
- O D de SOLID (inversão de dependência) não exige uma interface por classe. Ele pede uma abstração onde há variação real, ou uma fronteira de camada (porta de saída do domínio, conforme `arquitetura-limpa-java`).
- Com Spring, a injeção por construtor já dá substituição em teste. Mockito mocka classe concreta.

#### 2. "Uma classe por método"
- Isso dispersa a coesão. Fica contra o Object Calisthenics, que são heurísticas e não regras: "classe coesa com 3 atributos nao precisa ser quebrada" (Gotchas da skill).
- Aumenta o contexto que o LLM precisa carregar (princípio-mestre de Clean Code for AI).
- Pode mudar o comportamento. Se os métodos compartilham a fronteira `@Transactional`, mover um deles para outro bean altera a transação. A chamada interna deixa de passar pelo proxy, ou passa a passar, e o resultado muda. O mesmo vale para a ordem das operações e para recursos liberados em `finally`.

#### O que eu faria
1. **Confirmar o smell.** Perguntar qual dor concreta motivou o pedido: método longo, classe inchada, `switch` por tipo, dependências demais, teste difícil. Sem smell, não refatoro.
2. **Rede de segurança.** Rodar os testes do módulo (`mvn -q test`). Se eles não cobrem o trecho que vou mexer, escrevo antes testes de caracterização desse trecho (`testes-sistemas-java`). Trecho sem cobertura fica fora do diff e vira pendência declarada.
3. **Passos pequenos, um por vez**, com `mvn -q compile` e testes depois de cada passo. Se quebrar, reverto o passo.
   - Método longo: Extract Method em 2 ou 3 trechos.
   - Classe com responsabilidades distintas e coesas entre si: Extract Class, agrupando por responsabilidade e não por método. Exemplo: separar o cálculo de preço da notificação, e não "um método = uma classe".
   - `switch` por tipo: Strategy por lista injetada (`padroes-de-projeto-java/references/strategy-lista-injetada.md`) ou `sealed` + `switch`.
   - Interface: só onde houver 2 ou mais implementações reais, ou fronteira de camada.
4. **Limpar imports** da classe alterada (`remover-imports-nao-usados`).
5. **Encaminhar o diff ao `java-revisor`**, modo `tempestivo`, ou `auditoria` se a mudança for grande.

#### Como verificar
```bash
mvn -q compile
mvn -q test          # antes e depois, comparando as contagens
```
Na entrega eu listaria o diff por passo, o refactoring aplicado e o motivo, e as contagens de testes antes e depois. Também listaria os invariantes preservados, cada um com o teste ou trecho que o garante: fronteira de transação, ordem, idempotência e `finally`. Se o service for consumer/listener, os quatro invariantes obrigatórios:
- **Ordem por partição/chave:** continua em sequência, sem paralelismo novo.
- **Momento do ack/commit:** continua depois do efeito durável.
- **Idempotência:** a chave de deduplicação e o ponto onde é gravada ou consultada não mudam.
- **Cancelamento/revogação:** interrupção, `close` e revogação seguem com o mesmo commit ou espera de antes.

#### Para prosseguir
Preciso do código do service e de qual sintoma motivou o pedido. Com isso proponho o Extract Method ou Extract Class mínimo que resolve o sintoma.

Nenhum arquivo foi criado ou alterado.
