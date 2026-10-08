# Catálogo Java para skills, agents e comandos

Catálogo em português do Brasil para engenharia de software e system design com **Java 25** e **Spring Boot 4**. Ele reúne instruções reutilizáveis, papéis especializados, comandos de fluxo, exemplos executáveis e validações para criar, projetar, revisar e operar sistemas Java.

Use o catálogo para tomar decisões sustentadas por limites de recursos, evidências de teste e fronteiras claras entre arquitetura, implementação, revisão e operação.

## Comece por clone

O clone é a forma mais simples de conhecer, avaliar e contribuir com o catálogo:

```bash
git clone https://github.com/srportto/skills-manager.git
cd skills-manager
mvn -f validation/java/pom.xml verify
```

Com o clone você pode ler as fontes, executar os exemplos e copiar apenas os recursos necessários para outro projeto. Ao copiar uma skill, mantenha juntos `SKILL.md`, `references/`, `assets/` e `evals/`. Esse modo é útil para experimentação e para repositórios sem plugin, porém cópias não recebem atualizações automaticamente.

Para usá-lo em um projeto, clone o catálogo dentro de `plugins/catalogo-java/` do projeto consumidor e siga a instalação por escopo abaixo. Para uso pessoal em todos os projetos, mantenha o clone em `~/.codex/plugins/catalogo-java/`.

## Instalação

### Codex: escopo de usuário

Este escopo torna as **skills** disponíveis para a conta local em qualquer projeto. O catálogo inclui `.codex-plugin/plugin.json`, que declara `skills/` como diretório distribuído.

1. Clone o repositório em `~/.codex/plugins/catalogo-java/`.
2. Crie ou atualize `~/.agents/plugins/marketplace.json`:

```json
{
  "name": "catalogo-local",
  "plugins": [
    {
      "name": "catalogo-java",
      "source": {
        "source": "local",
        "path": "./.codex/plugins/catalogo-java"
      },
      "policy": {
        "installation": "AVAILABLE",
        "authentication": "ON_INSTALL"
      },
      "category": "Developer Tools"
    }
  ]
}
```

3. Reinicie o cliente Codex e habilite `catalogo-java@catalogo-local` no navegador de plugins. Se preferir controlar a ativação em arquivo, use em `~/.codex/config.toml`:

```toml
[plugins."catalogo-java@catalogo-local"]
enabled = true
```

### Codex: escopo de projeto

Este escopo compartilha o mesmo catálogo com quem clonar o projeto consumidor.

1. Dentro do projeto consumidor, clone este repositório em `plugins/catalogo-java/`.
2. Crie `.agents/plugins/marketplace.json` no projeto consumidor:

```json
{
  "name": "catalogo-do-projeto",
  "plugins": [
    {
      "name": "catalogo-java",
      "source": {
        "source": "local",
        "path": "./plugins/catalogo-java"
      },
      "policy": {
        "installation": "AVAILABLE",
        "authentication": "ON_INSTALL"
      },
      "category": "Developer Tools"
    }
  ]
}
```

3. Habilite o plugin no `.codex/config.toml` do projeto:

```toml
[plugins."catalogo-java@catalogo-do-projeto"]
enabled = true
```

O Codex só carrega configuração de projeto em repositórios confiáveis. O marketplace torna o plugin descobrível; o `config.toml` decide se ele fica habilitado no projeto.

### Claude Code

O catálogo preserva o manifesto e o marketplace compatíveis com Claude Code. Depois de publicar ou disponibilizar o repositório como marketplace, execute:

```text
/plugin marketplace add srportto/skills-manager
/plugin install catalogo-java@srportto-catalogo
```

Como alternativa, instale manualmente `skills/<nome>/` em `.claude/skills/<nome>/`, os arquivos de `agents/` em `.claude/agents/` e os comandos em `.claude/commands/`. As referências relativas de uma skill devem acompanhar o diretório instalado.

## Como escolher o recurso certo

| Recurso | Use quando | Resultado |
|---|---|---|
| **Skill** | Você precisa de um procedimento ou conhecimento especializado reutilizável. | Decisão, artefatos e validações do tema. |
| **Agent** | A tarefa exige um papel com escopo e fronteiras definidos. | Entrega especializada com evidências e encaminhamentos. |
| **Comando** | Há um fluxo frequente composto por skill e agent. | Encadeamento curto, sem duplicar regras. |
| **Exemplo Java** | Você quer validar uma técnica em código executável. | Código Maven e testes reproduzíveis. |

No Codex, uma skill pode ser escolhida pelo contexto ou solicitada explicitamente, por exemplo: `$devops-cicd crie um pipeline para esta aplicação`. Os arquivos de `agents/` e `commands/` descrevem fluxos completos do catálogo, mas clientes Codex não os carregam automaticamente como componentes de plugin. Neles, use a skill indicada e informe o papel ou o fluxo desejado no pedido. No Claude Code, agents e comandos podem ser instalados pelo plugin.

## Uso rápido

| Objetivo | Pedido ou comando recomendado |
|---|---|
| Criar aplicação REST com banco | `$criar-aplicacao-java crie uma aplicação de pedidos REST com PostgreSQL` ou `/java:nova-app` |
| Revisar um diff Java | `$revisao-de-codigo-java revise este diff` ou `/java:revisar tempestivo` |
| Projetar uma API | `Use o agent projetista-api para desenhar o contrato desta API` |
| Investigar N+1 | `$persistencia-jpa investigue este acesso N+1` |
| Criar pipeline, Dockerfile e Kubernetes | `$devops-cicd prepare CI, imagem e manifests` |
| Abrir uma mudança orientada a especificação | `/opsx:propose` |
| Gerar um ADR | `/arq:adr` |
| Avaliar a qualidade dos agents | `/catalogo:avaliar` |

Um fluxo comum de entrega é: `refinamento-de-historias` → `openspec-propose` → `java-construtor` → `java-revisor` em modo `auditoria`.

## Inventário

**31 skills**, uma pasta por item em `skills/`: 21 para a trilha Java, 1 para fluxo spec-driven e 9 ferramentas auxiliares. Há também **11 agents** e **12 comandos**.

### Skills da trilha Java

| Skill | Responsabilidade | Agents principais |
|---|---|---|
| `api-rest-design` | Contrato REST, OpenAPI 3.1, paginação, RFC 9457, 429/503 e idempotência | `projetista-api` |
| `arquitetura-limpa-java` | Camadas, módulos Spring, DDD e fronteiras | `java-revisor`, `java-construtor` |
| `banco-de-dados-performance` | SQL/SGBD, índices, tuning, conexões e replicação | `especialista-banco-dados` |
| `chaos-engineer` | Experimentos de falha, game days, abort e recuperação | `engenheiro-chaos` |
| `cloud-architect` | Topologia, tráfego, IAM, DR e FinOps | `arquiteto-cloud` |
| `criar-aplicacao-java` | Esqueleto Spring Boot 4 e variantes REST, banco, SQS e Kafka | `java-construtor` |
| `design-system-architecture` | System design, capacidade, SLO, consistência e ADR | `arquiteto-sistemas` |
| `devops-cicd` | Pipeline, Dockerfile, Kubernetes, probes e drenagem | `engenheiro-devops` |
| `gerar-diagramas` | Diagramas Mermaid versionados | sessão principal |
| `java-moderno` | Records, sealed classes, virtual threads e demais recursos Java 25 | `java-construtor`, `java-revisor` |
| `mensageria-sqs-kafka` | Ack, offset, DLQ, idempotência, outbox e replay | `java-construtor`, `java-revisor` |
| `monitoramento-java` | Logs, métricas, tracing, SLO, alertas e health groups | `especialista-monitoramento`, `java-revisor` |
| `padroes-de-projeto-java` | Padrões GoF e critérios para não aplicá-los | `java-revisor`, `refatorador-java` |
| `persistencia-jpa` | JPA/Hibernate, transações, locking e idempotência transacional | `especialista-banco-dados`, `java-construtor` |
| `qualidade-codigo-java` | Clean code e refactorings aplicados | `java-construtor`, `refatorador-java` |
| `refinamento-de-historias` | História pronta para desenvolvimento, DoR e critérios de aceite | sessão principal |
| `resiliencia-controle-fluxo-java` | Backpressure, admissão, timeout, retry, breaker, bulkhead e fallback | `arquiteto-sistemas`, `java-construtor`, `java-revisor` |
| `revisao-de-codigo-java` | Revisão por severidade | `java-revisor`, `projetista-api` |
| `seguranca-aplicacao-java` | OWASP, JWT, CORS, abuso de recursos e quotas | `engenheiro-seguranca` |
| `spring-data-redis` | Cache protegido, limite distribuído e streams | `java-construtor` |
| `testes-sistemas-java` | Provas de concorrência, idempotência, falha, contrato e carga | `java-construtor`, `java-revisor` |

### Fluxo spec-driven

| Skill | Responsabilidade |
|---|---|
| `openspec-catalogo-java` | Conecta as fases do OpenSpec às skills, agents e provas do catálogo. |

### Ferramentas auxiliares

| Skill | Uso |
|---|---|
| `graphify` | Cria e consulta grafo de conhecimento do código. |
| `openspec-apply-change` | Implementa tarefas de uma change OpenSpec. |
| `openspec-archive-change` | Arquiva uma change concluída. |
| `openspec-explore` | Explora ideias, problemas e requisitos. |
| `openspec-propose` | Cria uma change com artefatos iniciais. |
| `openspec-sync-specs` | Sincroniza delta specs com as especificações principais. |
| `python-pro` | Orienta serviços Python fora da trilha Java. |
| `remover-imports-nao-usados` | Remove imports sem uso em múltiplas linguagens. |
| `terraform-engineer` | Estrutura e valida infraestrutura como código Terraform. |

### Agents

| Agent | Papel | Entrega verificável |
|---|---|---|
| `arquiteto-sistemas` | Decide arquitetura, capacidade, consistência e proteções | ADR, capacidade e matriz de falhas |
| `arquiteto-cloud` | Desenha topologia, limites, DR e custo | Topologia, custo e plano de recuperação |
| `engenheiro-chaos` | Exercita sobrecarga, falhas e recuperação | Hipótese, baseline, abort e relatório |
| `engenheiro-devops` | Entrega pipeline, imagem, manifests e probes | Pipeline e ciclo de vida verificados |
| `engenheiro-seguranca` | Audita segurança e abuso de recursos | Modelo de ameaça e testes de abuso |
| `especialista-banco-dados` | Otimiza SQL, conexões, locks e lag | Baseline e comparação após ajuste |
| `especialista-monitoramento` | Instrumenta SLO, saturação, alertas e runbook | Instrumentação validada e runbook |
| `java-construtor` | Implementa aplicações e funcionalidades Java | Código e resultados de testes |
| `java-revisor` | Revisa invariantes e evidências | Achados e veredicto |
| `projetista-api` | Desenha contratos HTTP | Contrato e testes de contrato |
| `refatorador-java` | Aplica refactorings sem mudar comportamento | Testes antes e depois |

### Comandos

| Comando | Finalidade |
|---|---|
| `/arq:adr` | Registra um ADR com `arquiteto-sistemas`. |
| `/catalogo:avaliar` | Executa o protocolo de avaliação do catálogo. |
| `/catalogo:validar` | Executa as validações Maven e separa executado de pendente. |
| `/java:feature` | Refina, propõe, implementa e revisa uma feature Java. |
| `/java:nova-app` | Cria uma aplicação Java e solicita auditoria. |
| `/java:refatorar` | Aplica refactoring e revisão tempestiva. |
| `/java:revisar` | Revisa uma classe, diff ou entrega Java. |
| `/opsx:apply` | Implementa tarefas de uma change OpenSpec. |
| `/opsx:archive` | Arquiva uma change concluída. |
| `/opsx:explore` | Explora uma mudança antes da proposta. |
| `/opsx:propose` | Cria uma proposta OpenSpec. |
| `/opsx:sync` | Sincroniza delta specs. |

## Mapa rápido de skills por tarefa

| Tarefa | Skill principal | Complementos |
|---|---|---|
| Criar aplicação | `criar-aplicacao-java` | `arquitetura-limpa-java`, `mensageria-sqs-kafka`, `persistencia-jpa` |
| Decidir camada ou módulo | `arquitetura-limpa-java` | `design-system-architecture` |
| Desenhar sistema distribuído ou ADR | `design-system-architecture` | `resiliencia-controle-fluxo-java`, `gerar-diagramas` |
| Proteger fluxo contra sobrecarga ou falha | `resiliencia-controle-fluxo-java` | `testes-sistemas-java`, `monitoramento-java` |
| Provar concorrência, idempotência, falha ou carga | `testes-sistemas-java` | `resiliencia-controle-fluxo-java` |
| Decompor monólito em microsserviços | `arquitetura-limpa-java` | `design-system-architecture`, `mensageria-sqs-kafka` |
| Projetar API | `api-rest-design` | `testes-sistemas-java` |
| Diagnosticar JPA | `persistencia-jpa` | `banco-de-dados-performance` |
| Otimizar banco | `banco-de-dados-performance` | `persistencia-jpa` |
| Padronizar logs estruturados | `monitoramento-java` | `revisao-de-codigo-java` |
| Configurar observabilidade | `monitoramento-java` | `devops-cicd` |
| Implementar segurança ou quotas | `seguranca-aplicacao-java` | `resiliencia-controle-fluxo-java` |
| Refinar demanda ou história | `refinamento-de-historias` | `openspec-propose`, `api-rest-design` |
| Revisar diff ou pull request | `revisao-de-codigo-java` | `testes-sistemas-java`, `arquitetura-limpa-java` |
| Adicionar mensageria | `mensageria-sqs-kafka` | `testes-sistemas-java` |
| Usar Redis ou Valkey | `spring-data-redis` | `resiliencia-controle-fluxo-java` |
| Escolher padrões de projeto | `padroes-de-projeto-java` | `qualidade-codigo-java` |
| Migrar para recursos modernos Java | `java-moderno` | `revisao-de-codigo-java` |
| Gerar diagrama Mermaid | `gerar-diagramas` | `design-system-architecture` |
| Pipeline, imagem e Kubernetes | `devops-cicd` | `monitoramento-java` |
| Cloud, IAM, DR e custo | `cloud-architect` | `terraform-engineer` |
| Chaos engineering | `chaos-engineer` | `monitoramento-java` |
| Refatorar sem mudar comportamento | `qualidade-codigo-java` | `remover-imports-nao-usados` |
| Remover imports não usados | `remover-imports-nao-usados` | — |
| Usar Terraform | `terraform-engineer` | `cloud-architect` |
| Criar ou consultar grafo de conhecimento | `graphify` | — |
| Trabalhar por especificação | `openspec-propose` | `openspec-apply-change`, `openspec-sync-specs` |

## Fluxos de trabalho

| Você quer... | Primeiro | Depois |
|---|---|---|
| Criar uma aplicação nova | `java-construtor` | `java-revisor` no modo `auditoria` |
| Desenhar um sistema ou decisão arquitetural | `arquiteto-sistemas` | `java-construtor` → `java-revisor` |
| Adicionar feature em aplicação existente | sessão principal com skills | `java-revisor` em modo `tempestivo` e depois `auditoria` |
| Revisar um PR ou diff | `java-revisor` no modo `tempestivo` | `java-revisor` no modo `auditoria`, se necessário |
| Aplicar refactoring | `refatorador-java` | `java-revisor` no modo `tempestivo` |
| Desenhar contrato de API | `projetista-api` | `java-construtor` → `java-revisor` |
| Investigar query lenta ou pool saturado | `especialista-banco-dados` | — |
| Configurar observabilidade e SLO | `especialista-monitoramento` | `engenheiro-chaos` exercita os alertas |
| Testar resiliência | `engenheiro-chaos` | `especialista-monitoramento` |
| Auditar segurança | `engenheiro-seguranca` | `java-revisor` no modo `auditoria` |
| Montar pipeline, imagem ou Kubernetes | `engenheiro-devops` | `java-revisor` no modo `auditoria` para entrega Java |

`java-revisor` é a última linha de defesa antes de declarar uma entrega pronta. O modo `tempestivo` cobre um diff pontual; `auditoria` emite veredicto para a entrega completa.

## Migração de nomes

| Nome antigo | Destino | Observação |
|---|---|---|
| `refactoring-remove-parameter` | `qualidade-codigo-java` | Consulte `references/refatoracoes-fowler.md#remove-parameter`. |
| `java-architecture` | `arquitetura-limpa-java` + `testes-sistemas-java` | Camadas e módulos vivem em `arquitetura-limpa-java`; testes de slice e Testcontainers vivem em `testes-sistemas-java`. |
| `padrao-de-logs-java` | `monitoramento-java` | Consulte `references/logs-estruturados.md`, `logs-mdc-correlacao.md` e `logs-por-camada.md`. |
| agent `cloud-architect` | agent `arquiteto-cloud` | O nome foi alterado para evitar ambiguidade com a skill `cloud-architect`. |

## Estrutura do repositório

```text
skills/                      # uma pasta por skill
agents/                      # definições dos papéis especializados
commands/                    # comandos finos por fluxo
docs/catalogo/               # convenções, compatibilidade e avaliações
examples/java/               # exemplos Maven executáveis
validation/java/             # testes de estrutura, links e inventário
.claude-plugin/              # distribuição Claude Code
.codex-plugin/               # distribuição das skills no Codex
```

O contrato de cada skill e sua anatomia estão em [convenções do catálogo](docs/catalogo/convencoes.md). As referências são lidas sob demanda e os exemplos Java ficam em [examples/java](examples/java/README.md).

## Manutenção e validação

Depois de alterar uma skill, atualize seu `evals/evals.json` e execute `/catalogo:avaliar` nos casos afetados. Antes de commitar, execute:

```bash
mvn -f validation/java/pom.xml verify
mvn -f examples/java/pom.xml verify
mvn -f examples/java/pom.xml -Pintegracao verify
mvn -f examples/java/pom.xml -Pcarga verify
```

Os perfis `integracao` e `carga` têm requisitos de ambiente adicionais. Relate separadamente o que foi executado e o que permaneceu pendente; teste pulado não é aprovação.

## Princípios

- Uma fonte de verdade por tema: regras de resiliência, mensageria, observabilidade e provas não são duplicadas.
- Limites de fila, concorrência, espera, retry e fallback devem ter unidade, escopo e justificativa.
- Evidência executada vale mais que afirmação: compilação, testes e pendências são reportados separadamente.
- A solução deve ser proporcional; CRUD simples não recebe broker ou circuit breaker sem requisito concreto.
- Cada skill declara quando usar, quando não usar e quem aplica seu conteúdo.

## Compatibilidade e créditos

Skills seguem o formato `SKILL.md` com referências, assets e avaliações opcionais. O formato de plugin do Codex distribui skills; o Claude Code também pode distribuir agents e comandos deste catálogo. A publicação em marketplace ou workspace é uma etapa separada da instalação local.

Parte do conteúdo técnico foi traduzida, adaptada e estendida de [Jeffallan/claude-skills](https://github.com/Jeffallan/claude-skills). As skills OpenSpec vêm do próprio OpenSpec e `graphify` vem do projeto Graphify.
