---
name: exj
description: "Entrada EXJ para pedidos de engenharia Java: identifica o assunto e encaminha à skill especializada do catálogo. Use para localizar a capacidade Java adequada; tarefas de outras linguagens ficam fora desta trilha."
---

# EXJ — Especialista em tudo Java

## Quando usar

Use para localizar a capacidade do catálogo adequada a uma tarefa de desenvolvimento, arquitetura, revisão ou operação de sistemas Java.

## Quando não usar

Para Python e outros assuntos claramente fora da trilha Java, informe que `$exj` não é o roteador adequado e siga diretamente o recurso pertinente, se disponível. Não carregue skills Java para esse pedido.

## Entradas e decisão

Identifique o resultado pedido e o contexto já fornecido. Quando um pedido envolver mais de um tema, escolha a skill da tarefa principal; consulte outra apenas se o trabalho realmente a exigir. Diferencie desenhar o contrato de uma API de implementar seus endpoints.

| Tema do pedido | Skill | Agent, se instalado no cliente |
|---|---|---|
| Criar aplicação ou microsserviço novo | `criar-aplicacao-java` | `java-construtor` |
| Implementar feature em aplicação existente | `arquitetura-limpa-java` | `java-construtor` |
| Revisar código Java | `revisao-de-codigo-java` | `java-revisor` |
| Desenhar contrato de API REST | `api-rest-design` | `projetista-api` |
| Desenhar arquitetura de sistema ou ADR | `design-system-architecture` | `arquiteto-sistemas` |
| Refatorar código existente | `qualidade-codigo-java` | `refatorador-java` |
| Otimizar SQL ou SGBD | `banco-de-dados-performance` | `especialista-banco-dados` |
| Diagnosticar JPA ou Hibernate | `persistencia-jpa` | `especialista-banco-dados` |
| Auditar segurança de aplicação | `seguranca-aplicacao-java` | `engenheiro-seguranca` |
| Projetar topologia de nuvem | `cloud-architect` | `arquiteto-cloud` |
| Preparar pipeline, container ou deploy | `devops-cicd` | `engenheiro-devops` |
| Observar métricas, traces e alertas | `monitoramento-java` | `especialista-monitoramento` |
| Planejar experimento de caos | `chaos-engineer` | `engenheiro-chaos` |

## Encaminhamento e saída

Para um pedido Java dirigido a `$exj`, comece a resposta com `Skill: <id> + agent: <id>` da linha escolhida e, na mesma frase, esclareça que no Codex se aplica a skill e que o agent só pode ser acionado se estiver instalado no cliente. Mostre o par mesmo quando o agent não estiver disponível; não presuma que o Codex carrega esses agents como plugins. Em seguida, leia somente a skill indicada, na instalação do catálogo em uso, e execute o pedido conforme suas instruções ou peça a entrada concreta indispensável que faltar. Nunca encerre com mera repetição ou reformulação do pedido sem encaminhamento.

Entregue a seleção feita, o resultado solicitado e as verificações e limitações pertinentes conforme a skill escolhida. Confirme que o encaminhamento corresponde ao resultado pedido e preserve os limites da skill especializada. Esta entrada não substitui nem reproduz seus procedimentos.

## Quem aplica o quê

`$exj` identifica o assunto. A skill especializada orienta a execução; o agent indicado só se aplica em cliente onde esteja instalado.
