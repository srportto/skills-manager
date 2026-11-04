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

## Quando usar

Demanda vaga, história a escrever ou criticar, critério de aceite a montar, preparação de refinamento/planning
ou antes de abrir uma change OpenSpec.

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

## Quando NÃO usar

- Você já sabe o que construir e quer os artefatos formais → `openspec-propose`.
- Ainda não há recorte e é preciso investigar o problema → `openspec-explore`.
- O detalhe que falta é o desenho do contrato REST → `api-rest-design`.
- A história exige decisão arquitetural (serviço novo, capacidade, consistência) → `design-system-architecture`.
- O código já existe e o objetivo é criticá-lo → `revisao-de-codigo-java`.

## Entradas

1. **A demanda**, em qualquer formato.
2. **O perfil do projeto**, montado a partir do contexto recebido (`CLAUDE.md`/`AGENTS.md`, docs de
   arquitetura, código): serviços e responsabilidades, convenção de status/erros HTTP, máquina de estados
   relevante, schemas espelhados ou compartilhados, mecanismos de mensageria, restrições de persistência,
   SLOs e limites conhecidos. O que não estiver no contexto vira **pergunta**, não suposição.

Exemplo de perfil completo, de outro projeto: [perfil de exemplo](references/perfil-exemplo-autorizacoes.md).
Não aplique as convenções dele a outro projeto.

## Passo a passo (fluxo de refinamento)

1. **Enquadrar valor e ator** — quem pede, por quê, o que muda e para quem.
2. **Rotear pelos serviços impactados** — onde a mudança cai e o que cada serviço exige de resposta.
3. **Interrogar os eixos de risco** — contrato, estado, idempotência, dado, evento, observabilidade,
   carga/limites/recuperação, verificabilidade.
4. **Escrever critérios observáveis** — cada `Então` amarrado a um efeito verificável numa borda.
5. **Classificar as lacunas** — o que bloqueia, o que ajusta, o que fica registrado como débito.

> **Regra que atravessa as cinco etapas: nunca invente regra de negócio nem número para fechar uma
> lacuna.** Uma resposta inventada desaparece dentro de uma história bem formatada e é lida como requisito
> aprovado; uma pergunta em aberto fica visível e alguém a responde.

## Decisão: níveis de prontidão

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

Oito eixos: contrato e status HTTP, máquina de estados, idempotência e concorrência, persistência e migration,
eventos e espelhos, observabilidade e dado sensível, carga/limites/recuperação e verificabilidade. Não escreva
uma seção por eixo — **não deixe a pergunta sem resposta**; o que sobrar vira Questão em Aberto classificada.
Perguntas completas em [references/eixos-interrogatorio.md](references/eixos-interrogatorio.md).

## Etapa 4 — Critérios de aceite observáveis

Um critério serve quando nomeia um **efeito observável numa borda**: status HTTP e shape do corpo, linha
persistida, mensagem publicada, chave em cache, métrica, entrada de log. Cobertura mínima: caminho feliz, regra
violada, **chamada repetida**, **concorrência**, recurso ausente/estado inválido e, quando o eixo 7 se aplica,
**sobrecarga**, **dependência indisponível** e **recuperação**. Não esqueça a cláusula do efeito que **não** deve
acontecer. Tabela vago × observável, exemplo completo e reescrita ruim → bom em
[references/criterios-aceite.md](references/criterios-aceite.md).

## Anti-padrões de história

Seis erros recorrentes: a história já é a solução; o critério não pode falhar; silêncio sobre a chamada repetida;
"aguenta qualquer volume"; contrato inventado por analogia; campo novo sem a lista de espelhos. Cada um com
antes → depois em [references/anti-padroes-historia.md](references/anti-padroes-historia.md).

## Formato de saída

Use o template em [assets/template-historia.md](assets/template-historia.md) (seções com emoji: 🎯 história,
🗺️ escopo, 📏 limites, ✅ critérios, 🛠️ detalhamento, ⚠️ riscos, 🚦 prontidão, ❓ questões em aberto).
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

## Validação

Antes de entregar a história: percorra a Definition of Ready acima; para cada `Então`, confirme que existe um
teste que ficaria vermelho se ele falhasse; confirme que toda pergunta **Bloqueia** está na seção Questões em
Aberto com destinatário.

## Gotchas

- Nunca invente regra de negócio nem número para fechar lacuna: pergunta aberta é visível, resposta inventada vira requisito.
- O nível de prontidão é sobre a lacuna, não sobre o tamanho da tarefa.
- Perfil de exemplo é modelo de detalhe, não convenção a aplicar em outro projeto.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [references/eixos-interrogatorio.md](references/eixos-interrogatorio.md) | Ao interrogar a demanda nos oito eixos de risco (Etapa 3) |
| [references/criterios-aceite.md](references/criterios-aceite.md) | Ao escrever ou criticar critérios Dado/Quando/Então; traz exemplo completo e reescrita ruim → bom |
| [references/anti-padroes-historia.md](references/anti-padroes-historia.md) | Ao revisar uma história pronta contra erros recorrentes |
| [references/perfil-exemplo-autorizacoes.md](references/perfil-exemplo-autorizacoes.md) | Ao montar o perfil de um projeto, como modelo de nível de detalhe |
| [assets/template-historia.md](assets/template-historia.md) | Ao redigir a saída final da história |

## Quem aplica o quê

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
