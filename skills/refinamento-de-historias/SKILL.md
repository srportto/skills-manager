---
name: refinamento-de-historias
description: "Refina demanda, requisito ou história bruta em especificação pronta para desenvolvimento (Definition of Ready) a partir do perfil do projeto recebido — INVEST, critérios de aceite observáveis em Dado/Quando/Então, roteamento por serviço impactado, interrogatório de idempotência/concorrência/contrato/espelhos, limites de carga, sobrecarga, indisponibilidade e recuperação, e lacunas classificadas por prontidão (Bloqueia / Ajusta / Registra). Use ao receber demanda vaga, escrever ou criticar história de usuário, montar critério de aceite, preparar refinamento/planning, ou antes de abrir uma change OpenSpec. Uso: sessão principal ou `/refinamento-de-historias`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "2.0.0"
  domain: requirements
  triggers: refinar, refinamento, história de usuário, user story, critério de aceite, definition of ready, INVEST, BDD, demanda, requisito, backlog, planning, fatiar história, isso está pronto pra desenvolver?
  role: refiner
  scope: requirements-refinement
  output-format: document
  related-skills: openspec-propose, openspec-explore, design-system-architecture, api-rest-design, arquitetura-limpa-java, mensageria-sqs-kafka, resiliencia-controle-fluxo-java, revisao-de-codigo-java
---

# Refinamento de Histórias

## Visão geral

Transforma demanda bruta — parágrafo do PO, print de chamado, bug report, requisito regulatório — em
especificação que um dev do projeto consegue implementar sem adivinhar nada. É a imagem espelhada de
`revisao-de-codigo-java` no outro extremo do ciclo: lá se classifica **defeito de código** por severidade
depois que ele existe; aqui se classifica **lacuna de história** por prontidão antes que ele exista. O
critério de gravidade é o mesmo — o que quebra em produção.

**Por que não um template genérico:** refinamento genérico produz história bonita e vazia. Os riscos
reais são específicos de cada projeto — convenção de status HTTP, grafo de estados, chamadores que repetem,
schemas espelhados, particionamento, capacidade de dependências. Por isso esta skill trabalha sobre um
**perfil do projeto** (abaixo) e não sobre suposições.

**Quando NÃO usar:**

- Você já sabe o que construir e quer os artefatos formais → `openspec-propose`.
- Ainda não há recorte e é preciso investigar o problema → `openspec-explore`.
- O detalhe que falta é o desenho do contrato REST → `api-rest-design`.
- A história exige decisão arquitetural (serviço novo, capacidade, consistência) → `design-system-architecture`.
- O código já existe e o objetivo é criticá-lo → `revisao-de-codigo-java`.

## Entrada

1. **A demanda**, em qualquer formato.
2. **O perfil do projeto**, montado a partir do contexto recebido (`CLAUDE.md`/`AGENTS.md`, docs de
   arquitetura, código): serviços e responsabilidades, convenção de status/erros HTTP, máquina de estados
   relevante, schemas espelhados ou compartilhados, mecanismos de mensageria, restrições de persistência,
   SLOs e limites conhecidos. O que não estiver no contexto vira **pergunta**, não suposição.

Exemplo de perfil completo, de outro projeto: [perfil de exemplo](references/perfil-exemplo-autorizacoes.md).
Não aplique as convenções dele a outro projeto.

## Fluxo de refinamento

1. **Enquadrar valor e ator** — quem pede, por quê, o que muda e para quem.
2. **Rotear pelos serviços impactados** — onde a mudança cai e o que cada serviço exige de resposta.
3. **Interrogar os eixos de risco** — contrato, estado, idempotência, dado, evento, observabilidade,
   carga/limites/recuperação, verificabilidade.
4. **Escrever critérios observáveis** — cada `Então` amarrado a um efeito verificável numa borda.
5. **Classificar as lacunas** — o que bloqueia, o que ajusta, o que fica registrado como débito.

> **Regra que atravessa as cinco etapas: nunca invente regra de negócio nem número para fechar uma
> lacuna.** Uma resposta inventada desaparece dentro de uma história bem formatada e é lida como requisito
> aprovado; uma pergunta em aberto fica visível e alguém a responde.

## Níveis de prontidão

| Nível | Definição | Efeito |
|---|---|---|
| **Bloqueia** | Lacuna que faz o time construir a coisa errada, corromper dado, quebrar contrato ou ficar sem limite sob carga — regra de negócio indefinida, transição de status não especificada, comportamento sob chamada repetida em aberto, efeito sob sobrecarga indefinido em fluxo crítico | História **não entra** em sprint até ser respondida |
| **Ajusta** | Gera retrabalho previsível, mas não impede começar — nome de campo, texto de erro, default, limite de paginação | Pode entrar; resolver antes do primeiro commit |
| **Registra** | Decisão consciente de adiar algo tocado de raspão | Débito com **gatilho de revisão explícito** |

O nível é sobre a **lacuna**, não sobre o tamanho da tarefa: um valor de status errado é uma linha de código
e ainda assim **Bloqueia**, porque fica gravado e não há como distinguir sua origem depois.

## Etapa 1 — Valor, ator e recorte

**O ator raramente é uma pessoa.** Chamadores costumam ser sistemas: API de parceiro, agendador, consumidor
de fila, job. Escrever "Como usuário" quando o chamador é uma máquina custa caro: **máquina repete, pessoa
não**. Nomeie o chamador real e a história passa a exigir sozinha o cenário de chamada repetida.

Das letras de INVEST, duas mordem de verdade:

**Small — fatie por contrato, não por camada.** "1) entidade, 2) use case, 3) controller" produz fatias não
demonstráveis. Fatie pelo contrato que muda: 1) aceitar e persistir o campo; 2) devolvê-lo na consulta;
3) propagá-lo no evento. Cada fatia é testável numa borda e pode ir para produção sozinha.

**Testable — o critério tem que poder falhar.** Se nenhum teste fica vermelho por causa de um `Então`, ele é
intenção, não critério.

> **Sinal de história grande demais:** tocar três ou mais serviços. Fatie antes de refinar o resto.

## Etapa 2 — Roteamento por serviço

Para cada serviço do perfil tocado pela história, responda a **pergunta obrigatória** que aquele serviço
impõe (transição de estado, espelhos, compatibilidade de schema, volume de consulta, chamador repetido...).
Enquanto ela estiver sem resposta, a lacuna é **Bloqueia**. Se o perfil não lista os serviços, a primeira
pergunta da história é "em qual serviço isso cai?".

## Etapa 3 — Eixos de interrogatório

Oito eixos. Não escreva uma seção por eixo — **não deixe a pergunta sem resposta**. O que sobrar vira
Questão em Aberto classificada.

### 1. Contrato de entrada e status HTTP

Use a convenção **do projeto** (perfil), não o padrão REST genérico. Se o projeto não tem convenção
declarada, use `api-rest-design` como default explícito e registre a decisão. Pontos que sempre importam:
qual status para entrada inválida, recurso inexistente, conflito de concorrência, quota do cliente (429) e
indisponibilidade/saturação do serviço (503); faixa declarada para campo numérico; tamanho máximo de
payload e de página.

### 2. Máquina de estados

Toda história que muda status responde: **de qual estado, para qual estado, com qual motivo**. Aresta
nova no grafo é mudança de alto impacto (**Bloqueia** até decisão explícita). **Alcançabilidade no grafo não é
idempotência**: pergunte qual é a **checagem explícita de origem** da transição, não só se a aresta existe.

### 3. Idempotência e concorrência

| Pergunta | Por que importa |
|---|---|
| Quem chama, e o chamador repete? | Filas e brokers entregam at-least-once; agendadores e clientes HTTP repetem após timeout |
| A segunda chamada devolve o quê? | O chamador automatizado precisa distinguir "já resolvida" (não repetir) de falha (repetir) |
| Mesma chave com payload diferente? | Deve ser conflito, não sobrescrita silenciosa |
| Dois chamadores simultâneos? | Lock otimista/restrição única e o status resultante |
| Quantos efeitos colaterais no total? | O critério diz **exatamente um** evento/cobrança, não "um evento é publicado" |

### 4. Persistência, particionamento e migration

Onde o campo precisa ser refletido (e o que **não** quebra a compilação se for esquecido), migration com
expand/contract, índice em tabela grande/particionada com procedimento próprio, volume esperado para
consultas novas (sem ele, critério de performance é chute).

### 5. Eventos e espelhos

Se o projeto espelha schemas manualmente, **a lista de espelhos entra na própria história**. Campo novo em
evento é pergunta de compatibilidade: nullable com default, ou o consumidor antigo quebra.

### 6. Observabilidade e dado sensível

Correlação (`traceId`) em fluxo assíncrono, rastreabilidade de quem decidiu e por qual canal, erro sem
detalhes internos, dado pessoal/financeiro fora de log. O critério diz o que **não** aparece no log.

### 7. Carga, limites e recuperação

Para fluxo com volume, dependência remota ou processamento assíncrono:

| Pergunta | Critério observável esperado |
|---|---|
| Taxa média, pico e **duração** do pico? Tamanho máximo do item? | Números com unidade ou Questão em Aberto (nunca número inventado) |
| O que acontece acima da capacidade? | Rejeição (429 quota / 503 saturação, com `Retry-After` quando útil), pausa do produtor ou persistência durável — e a métrica que mostra isso |
| Dependência lenta ou fora? | Deadline, sem retenção ilimitada de recursos; degradação semanticamente válida (nunca "aprovado" inventado) |
| Duplicidade após timeout ou reentrega? | Efeito único; resultado anterior devolvido |
| Dados podem estar desatualizados? | Staleness máximo aceito e onde ele é visível |
| Como volta ao normal? | Drenagem/replay em taxa limitada, sem tempestade de retries |

Em fluxo crítico, "comportamento sob sobrecarga indefinido" é **Bloqueia**. Em CRUD de baixo tráfego, basta
limite de payload/paginação e timeout — não exija infraestrutura distribuída sem motivo. Mecanismos e
números de referência: `resiliencia-controle-fluxo-java` e `design-system-architecture` (capacidade e SLOs).

### 8. Verificabilidade

Para cada `Então`: **existe um teste que fica vermelho se isso não acontecer?** Se não, o critério é vago ou
falta uma tarefa (ex.: teste de concorrência, de carga ou de falha). Indique o tipo de prova esperado
(unitário, integração com serviço real, carga) conforme `testes-sistemas-java`.

## Etapa 4 — Critérios de aceite observáveis

Um critério serve quando nomeia um **efeito observável numa borda**: status HTTP e shape do corpo, linha
persistida (coluna e valor), mensagem publicada (destino e atributo), chave em cache, métrica, entrada de log.

| Vago (não serve) | Observável (serve) |
|---|---|
| "o pedido é cancelado" | "a linha persistida tem `status` = `CANCELADO` **e** `motivo` = `<valor>`" |
| "retorna erro" | "responde `<status do projeto>` com o shape de erro do projeto **e** nada é persistido" |
| "o evento é publicado" | "**exatamente um** evento `<tipo>` é publicado em `<destino>`" |
| "a consulta é rápida" | "p99 < `<N>` ms com `<volume>` carregado" — sem `<N>` conhecido, Questão em Aberto **Bloqueia** |
| "aguenta o pico" | "com `<taxa>` req/s por `<duração>`, requisições acima de `<limite>` recebem 503 em < 50 ms, nenhuma fila passa de `<itens>` e o serviço volta ao p99 nominal em `<tempo>` após o pico" |
| "resiliente ao provedor" | "com provedor respondendo em > `<deadline>`, no máximo `<N>` chamadas simultâneas ficam abertas e o pedido fica `PENDENTE` para reconciliação" |

**Cobertura mínima de cenários:** caminho feliz; regra violada (o erro **e** o que não aconteceu); **chamada
repetida**; **concorrência**; recurso ausente/estado inválido; e, quando o eixo 7 se aplica, **sobrecarga**,
**dependência indisponível** e **recuperação**.

O item mais esquecido é uma cláusula: **o efeito que não deve acontecer** — "e nenhum evento é publicado",
"e exatamente uma cobrança no total", "e a fila não passa de N itens".

## Anti-padrões de história

### A história já é a solução

**[❌]** `Criar uma tabela de log de decisões com as colunas id, pedido_id, acao e data.`

**[🚨]** Entrega o desenho e esconde a necessidade; ninguém avalia se a informação já é recuperável.

**[✅]** `Como analista de operações, quero saber por qual caminho um pedido chegou ao estado atual, para
investigar reclamação sem depender da engenharia.`

### O critério não pode falhar

**[❌]** `Então o sistema processa corretamente e mantém a consistência dos dados.`

**[🚨]** Nenhum teste fica vermelho por causa disso; dá sensação de cobertura.

**[✅]** `Então a linha tem status ATIVO E motivo = ACEITO_POR_TODOS E exatamente um evento ATIVACAO é publicado.`

### Silêncio sobre a chamada repetida

**[❌]** `Quando o prazo expira, o pedido é rejeitado.`

**[🚨]** Quem dispara é um agendador at-least-once que não conhece o estado. E se a expiração chegar depois
da aprovação?

**[✅]**
```
Cenário: expiração chega depois da aprovação
  Dado que o pedido já está ATIVO
  Quando a expiração é processada
  Então a resposta identifica o status atual como erro de negócio
  E o pedido permanece ATIVO
  E nenhum evento é publicado
```

### "Aguenta qualquer volume"

**[❌]** `O endpoint de importação deve suportar alto volume.`

**[🚨]** Sem taxa, pico, duração e comportamento acima do limite, o dev escolhe uma fila em memória sem
limite — e o primeiro pico vira OutOfMemoryError.

**[✅]**
```
Cenário: importação acima da capacidade
  Dado que a importação processa até 200 itens/s e aceita no máximo 2.000 itens pendentes
  Quando chegam 1.000 itens/s por 10 s
  Então os itens acima de 2.000 pendentes recebem 503 com Retry-After
  E a métrica importacao_rejeitados_total registra cada rejeição
  E nenhum item aceito é perdido após reinício do serviço
```

### Contrato inventado por analogia

**[❌]** `Se não existir, 404. Se inválido, 400.`

**[🚨]** É o REST genérico, não necessariamente a convenção do projeto. Confira o perfil; divergência é
decisão explícita, não detalhe.

### Campo novo sem a lista de espelhos

**[❌]** `Adicionar canalOrigem e disponibilizá-lo para os consumidores.`

**[🚨]** "Os consumidores" esconde cópias mantidas à mão; esquecer uma não quebra a compilação — o campo
chega nulo em produção.

**[✅]** A história lista cada espelho e declara o campo nullable com default.

## Formato de saída

````markdown
## 🎯 História de Usuário (INVEST)

**Como** [ator real — nomeie o sistema, se for sistema],
**Eu quero** [ação/funcionalidade],
**Para que** [benefício verificável].

## 🗺️ Escopo e serviços impactados

| Serviço | O que muda | Espelho a replicar |
|---|---|---|

## 📏 Limites e não funcionais (quando aplicável)

| Fluxo | Carga (média/pico/duração) | Limite e escopo | Acima do limite | SLO/latência | Prova |
|---|---|---|---|---|---|

## ✅ Critérios de Aceite

### Cenário 1: [Caminho feliz]
- **Dado que** [pré-condição verificável]
- **Quando** [ação]
- **Então** [efeito observável numa borda]
- **E** [efeito colateral esperado]

### Cenário 2: [Regra de negócio violada]
### Cenário 3: [Chamada repetida]
### Cenário 4: [Concorrência]
### Cenário 5: [Sobrecarga / dependência indisponível / recuperação] (quando aplicável)

## 🛠️ Detalhamento Técnico

- **Contrato**: rotas, headers, status por caso, tópicos/filas
- **Estado**: transição de → para, motivo, checagem explícita de origem
- **Persistência**: entidades, colunas, migration, particionamento
- **Eventos**: tipo, payload, compatibilidade de schema
- **Resiliência**: idempotência, concorrência, deadline, retry (dono e limite), DLQ, rejeição
- **Observabilidade**: correlação, métricas, o que nunca logar
- **Provas**: testes unitários, integração, carga — e o que fica pendente

## ⚠️ Bordas e Riscos
- [ ] [risco concreto e sua consequência]

## 🚦 Prontidão

| Nível | Lacuna | Ação |
|---|---|---|

## ❓ Questões em Aberto
- **[Bloqueia]** [pergunta objetiva, endereçada a quem pode respondê-la]
````

Omita seções sem conteúdo real, exceto **Prontidão** e **Questões em Aberto**, que sempre aparecem.

## Definition of Ready (resumo)

- Ator real nomeado; fatia demonstrável numa borda.
- Serviços impactados roteados e perguntas obrigatórias respondidas.
- Critérios observáveis cobrindo repetição, concorrência e o efeito que não deve acontecer.
- Para fluxos com volume/dependência: limites de aceitação (carga, comportamento acima do limite,
  indisponibilidade, staleness, recuperação) escritos com unidade ou como Questão em Aberto **Bloqueia**.
- Tipo de prova esperado para cada critério.
- Nenhuma lacuna **Bloqueia** em aberto.

## Ponte para o OpenSpec

Se o projeto usa OpenSpec, a história refinada é insumo de uma change: cada regra vira
`### Requirement:` com `SHALL`; cada cenário, `#### Scenario:` com `WHEN`/`THEN`/`AND` (o `Dado que`
normalmente é absorvido pelo `WHEN`). Um critério vago não sobrevive à tradução — se ela travar, a lacuna é
da história.

## Skills e agents relacionados

| Situação | Use |
|---|---|
| História refinada, hora de abrir a change | skill `openspec-propose` |
| Ainda investigando o problema | skill `openspec-explore` |
| Desenho de contrato REST | skill `api-rest-design` / agent `projetista-api` |
| Decisão arquitetural, capacidade, consistência | agent `arquiteto-sistemas` |
| Mecanismos de proteção e limites | skill `resiliencia-controle-fluxo-java` |
| Dúvida sobre DLQ, retry ou idempotência de listener | skill `mensageria-sqs-kafka` |
| Camada da implementação | skill `arquitetura-limpa-java` |
| Diagrama do fluxo | skill `gerar-diagramas` |
| A história virou código e você quer criticá-lo | skill `revisao-de-codigo-java` / agent `java-revisor` |

> **Coesão com `revisao-de-codigo-java`:** um achado **Crítico** na revisão que só existe porque a história
> não respondeu a uma pergunta é sinal de que faltou uma pergunta **Bloqueia** aqui.
