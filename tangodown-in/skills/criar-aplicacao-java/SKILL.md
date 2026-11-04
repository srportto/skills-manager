---
name: criar-aplicacao-java
description: "Gera esqueleto buildável de app Spring Boot 4 + Java 25 em hexagonal clássica (domain/application/infrastructure), com rota `/disponibilidade` e variante escolhida (REST, CRUD com banco, SQS listener, Kafka consumer, ponte SQS→Kafka, etc.). Use ao criar aplicação, microsserviço ou esqueleto novo. Uso: agent `java-construtor` (validado por `java-revisor` modo `auditoria`) ou `/criar-aplicacao-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "3.0.0"
  domain: application-scaffolding
  triggers: crie uma aplicação, novo microserviço, esqueleto de app java, app que consome fila, consumidor kafka, hexagonal Spring Boot, ports and adapters
  role: builder
  scope: application-generation
  output-format: code
  related-skills: arquitetura-limpa-java, mensageria-sqs-kafka, persistencia-jpa, java-moderno
---

# Criar Aplicação Java (Spring Boot, hexagonal clássica)

Gera uma aplicação Spring Boot **buildável** seguindo a **arquitetura hexagonal clássica (ports &
adapters)** — camadas `domain` / `application` / `infrastructure`, ver `arquitetura-limpa-java` — a partir
do esqueleto versionado em [`assets/esqueleto`](assets/esqueleto/pom.xml), combinado, opcionalmente, com uma
**variante** (CRUD com banco, consumo de fila, ponte de mensageria, etc.) descrita em `references/`.

**Princípio central:** a base (sem variante) roda **sem depender de nenhuma infraestrutura externa**
— nem banco, nem fila, nem broker. É o "esqueleto" seguro para começar qualquer aplicação. Cada
variante é quem introduz (e exige) a infraestrutura que passa a ser necessária.

**Prova:** o esqueleto é módulo do build de `examples/java` (`mvn -f examples/java/pom.xml verify` compila e roda
os testes dele). Fonte única: só existe em `assets/esqueleto`, nunca copiado para outro lugar do catálogo.

## Quando usar / Quando NÃO usar

**Use esta skill quando** o pedido for para **criar uma aplicação/microserviço nova do zero** — REST
puro, CRUD com banco, listener de fila SQS, ponte SQS→banco, ponte SQS→Kafka, consumidor Kafka, ou
REST que publica em Kafka.

**NÃO use esta skill para:**
- Adicionar uma feature/endpoint/entidade em uma aplicação **já existente** — use
  `arquitetura-limpa-java`.
- Tirar dúvidas sobre mensageria sem a intenção de criar uma aplicação nova — use
  `mensageria-sqs-kafka`.

## Entradas

Use o que o pedido já informou. **Nome da aplicação** e **variante** não têm default seguro: se faltarem,
pergunte. Os demais têm default declarado — aplique-o sem perguntar e liste na entrega os valores assumidos.
Tabela completa de parâmetros (pasta destino, porta, profile, container web Tomcat/Jetty, carga esperada) em
[parametros.md](references/parametros.md).

## Contrato da base (o que o esqueleto entrega sempre)

- Pacote `br.com.srportto.<nome>`, classe principal `<Nome>Application` (o esqueleto vem com
  `br.com.exemplo.esqueleto` / `EsqueletoApplication`: renomeie pacote, classe, `artifactId` e
  `spring.application.name`)
- As três camadas já materializadas (ver "Layout gerado" abaixo), com o health check passando por
  uma porta — é o exemplo vivo do padrão dentro do próprio esqueleto
- Rota `GET /disponibilidade` → `200 OK`, corpo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`
- Tratamento de erros (`BusinessException` → 422, `ApplicationException` → 500, validação de bean)
- Actuator com probes: `/actuator/health/liveness` (só o processo) e `/actuator/health/readiness` (estado
  desta réplica; dependência compartilhada fica fora) — semântica em `monitoramento-java`; `/disponibilidade` é smoke test
- Limites básicos de borda: corpo da requisição (JSON incluso) limitado por `LimiteCorpoRequisicaoFilter`
  (`app.http.limite-corpo`, padrão 1MB → 413), tamanho de header e timeouts explícitos em todo cliente gerado
- Logs estruturados em JSON, graceful shutdown e `Dockerfile` multi-stage Java 25 (padrão de `devops-cicd`)
- Teste de contexto (`@SpringBootTest`) que sobe sem infra externa

### Layout gerado

```
br.com.srportto.<nome>/
├── domain/
│   ├── model/                 ← modelo puro (ex.: Disponibilidade)
│   ├── port/in/               ← ConsultarDisponibilidadeUseCase
│   ├── port/out/              ← portas de saída (vazio na base pura)
│   └── exception/             ← BusinessException, ApplicationException
├── application/
│   └── usecase/               ← ConsultarDisponibilidadeService (implementa a port/in; bean em infrastructure/config)
└── infrastructure/
    ├── web/                   ← DisponibilidadeController, DTOs, ApiExceptionHandler, LimiteCorpoRequisicaoFilter
    └── config/                ← @Configuration
```

> **Nunca** gere aplicação nova no layout legado `entrypoint`/`application`/`domain`/`shared` (usado por
> aplicações do monorepo de origem — contexto externo); a tabela de equivalência está em
> `arquitetura-limpa-java`.

## Decisão: variante → reference → o que adiciona

| Variante | Reference | O que adiciona sobre o esqueleto |
|---|---|---|
| **base pura** / REST | [variante-rest.md](references/variante-rest.md) | Casos de uso e controllers de negócio; opcionalmente `starter-validation`; nenhuma infra. |
| **rest-crud-banco** | [variante-crud-banco.md](references/variante-crud-banco.md) | JPA + driver + MapStruct, `port/out` de repositório, pool/paginação, `db` no grupo `dependencias` (fora da readiness), PUT com versão. |
| **sqs-listener** / **sqs-para-banco** | [variante-sqs-listener.md](references/variante-sqs-listener.md) | Cliente SQS, listener + interceptor central de erro, fila com DLQ + `RedrivePolicy`; idempotência (persistente em `sqs-para-banco`). |
| **kafka-consumer** | [variante-kafka-consumer.md](references/variante-kafka-consumer.md) | Starter Kafka, `@KafkaListener`, `DefaultErrorHandler` + DLT, commit após efeito. |
| **sqs-para-kafka** / **rest-para-kafka** | [variante-ponte-sqs-kafka.md](references/variante-ponte-sqs-kafka.md) | Producer atrás de `port/out` (`acks=all`, idempotente, deadline); delete SQS / resposta 200 só após confirmação do Kafka. |

> Em toda variante, o adaptador **nunca** conversa com outro adaptador: a entrada chama uma `port/in`
> e a saída é sempre uma `port/out` declarada no `domain`.

**Toda variante que envolva SQS SHALL nascer com DLQ na fila e com o interceptor central de erro de
consumo** — não é opcional, é parte da definição da variante (regras duras em `mensageria-sqs-kafka`).

Proporcional ao risco: a base não ganha broker, cache, WebFlux nem Resilience4j sem necessidade. Variantes com
dependência remota ou mensageria nascem com a proteção pertinente **e** o teste que a prova (tabela
"Proteções e provas" em cada reference de variante). Detalhes: `resiliencia-controle-fluxo-java`,
`mensageria-sqs-kafka`, `testes-sistemas-java`.

## Passo a passo

Copie e acompanhe:

```
- [ ] 1. Nome e variante definidos (demais parâmetros com defaults listados)
- [ ] 2. Copiar assets/esqueleto para a pasta destino; renomear pacote, classe, artifactId, spring.application.name
- [ ] 3. Aplicar o container web escolhido (parametros.md)
- [ ] 4. Aplicar a reference da variante (dependências, pacotes, application.yml, testes)
- [ ] 5. mvn clean verify (evidência separada: compilação, unitários, integração executada ou pendente)
- [ ] 6. Smoke: mvn spring-boot:run e GET /disponibilidade
- [ ] 7. Agent java-revisor (modo auditoria) com a lista de arquivos e a saída do build
```

1. **Copiar a base**: `assets/esqueleto` → pasta destino (`<nome>-service` por default). Renomeie
   `br.com.exemplo.esqueleto` para `br.com.srportto.<nome>` (pacote, diretórios, `package`/`import`), a classe
   `EsqueletoApplication` para `<Nome>Application`, `artifactId`/`name` no `pom.xml` e `spring.application.name`
   no `application.yml`. Aplique o container web escolhido.

2. **Aplicar a variante**, se houver: siga a reference da tabela de decisão. Para variantes com SQS, provisione a
   fila com DLQ (IaC local, ex. Terraform contra o Floci em `http://localhost:4566`) e implemente o interceptor
   central de erro **no mesmo passo** — não deixe para depois.

3. **Buildar e testar**: `mvn clean verify`. Testes que dependem de infraestrutura externa usam
   Testcontainers (Docker) — para SQS e demais serviços AWS, `FlociContainer` (`io.floci:testcontainers-floci`) — num perfil separado (`-Pintegracao`); se o ambiente não tiver Docker/emulador,
   **não** use `-DskipTests` para declarar sucesso — relate compilação, testes executados e testes
   **pendentes** separadamente (o `java-revisor` trata pendência como pendência, não aprovação).

4. **Smoke test**: suba a aplicação (`mvn spring-boot:run`) e confirme `GET /disponibilidade`
   respondendo `{"aplicacao":"<nome>","status":"DISPONIVEL"}` e `GET /actuator/health/readiness` em `200`.

5. **Validação obrigatória**: invoque o agent `java-revisor` (modo `auditoria`), passando a lista de arquivos
   gerados e a saída do build. Quando a aplicação tocar mensageria, o agent valida também DLQ e
   interceptor central (ver `mensageria-sqs-kafka`). Achados **críticos** bloqueiam a
   entrega — corrija e revalide antes de considerar a tarefa concluída.

## Saída

Projeto buildável na pasta destino, lista dos parâmetros assumidos (defaults) e evidência de build/testes
separada por tipo (compilação, unitários, integração executada ou pendente com motivo).

## Validação

- Esqueleto: `mvn -f examples/java/pom.xml verify` (módulo `esqueleto-aplicacao` no reactor, com seus testes).
- Aplicação gerada: `mvn clean verify` + smoke + veredicto do `java-revisor` (modo `auditoria`) sem achados críticos.

### Checklist final

- [ ] Nome e variante informados ou confirmados; demais parâmetros listados com os defaults assumidos
- [ ] Evidência registrada separadamente: compilação, testes unitários, integração (executado ou **pendente**
      com motivo) — sem `-DskipTests` como prova
- [ ] Proteções e provas da variante (tabela "Proteções e provas" da reference) presentes
- [ ] Probes `/actuator/health/liveness` e `/readiness` com a semântica de `monitoramento-java`
- [ ] Rota `GET /disponibilidade` responde com o nome correto da aplicação
- [ ] Estrutura hexagonal clássica (`domain` com `model`/`port/in`/`port/out`, `application/usecase`,
      `infrastructure` com os adapters) presente e completa
- [ ] `domain` sem nenhum import de `org.springframework.*`, `jakarta.persistence.*` ou Jackson
- [ ] Todo adapter de saída implementa uma `port/out`; nenhum use case injeta `JpaRepository`,
      `RestClient` ou SDK de broker diretamente
- [ ] Se a variante envolve SQS: fila tem DLQ + `RedrivePolicy`, e existe interceptor central de erro
- [ ] Veredicto do agent `java-revisor` (modo `auditoria`) sem achados críticos

## Gotchas (erros comuns)

| Sintoma | Causa / correção |
|---------|-------------------|
| App não sobe: "Failed to configure a DataSource" | Incluiu `spring-boot-starter-data-jpa` sem o banco no ar. Suba o banco ou remova a dependência se não for usá-la. |
| `mvnw.cmd` quebrado/falha no Windows | Use `mvn` diretamente em vez do wrapper. |
| Plugin do Spring Boot não empacota `void main()` ("Unable to find main class" no `repackage`) | Use `public static void main(String[] args)` — o plugin 4.0.7 não reconhece o `void main()` do JDK 25 (verificado em 2026-10-06). |
| Porta ocupada | Escolha uma porta livre ou pare o processo conflitante. |
| Container web errado no log de startup | Confira se a exclusão do Tomcat + starter Jetty foi aplicada corretamente no `pom.xml`. |
| `NoSuchBeanDefinitionException` para `ObjectMapper` (variantes SQS/Kafka com Jackson) | Spring Boot 4 usa Jackson 3 por padrão e não cria um `ObjectMapper` clássico automaticamente — declare o bean explicitamente. |
| `mvn test` completo falha com `SdkClientException: Connection refused` | Variante com SQS exige o Floci rodando (`docker run -d -p 4566:4566 floci/floci:2.2.0`) e a fila já criada antes de rodar a suíte completa — não é bug. |
| Renomeou só o `pom.xml` e o build quebra ou o nome sai errado em `/disponibilidade` | Renomeie também pacote/diretórios, `EsqueletoApplication` e `spring.application.name`. |

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [parametros.md](references/parametros.md) | Definir nome, variante, pasta, porta, profile, carga e container web (Tomcat/Jetty) |
| [variante-rest.md](references/variante-rest.md) | Base pura ou REST sem infra: controllers, DTOs, erros, limites |
| [variante-crud-banco.md](references/variante-crud-banco.md) | REST com banco: JPA, `port/out`, pool, paginação, `db` fora da readiness, update com `@Version` |
| [variante-sqs-listener.md](references/variante-sqs-listener.md) | Consumir SQS (`sqs-listener`, `sqs-para-banco`): DLQ, interceptor, idempotência |
| [variante-kafka-consumer.md](references/variante-kafka-consumer.md) | Consumir Kafka: `@KafkaListener`, `DefaultErrorHandler`, DLT |
| [variante-ponte-sqs-kafka.md](references/variante-ponte-sqs-kafka.md) | Publicar no Kafka (`sqs-para-kafka`, `rest-para-kafka`): producer, confirmação, outbox |

## Delegação

Quando o pedido ocorrer dentro de um contexto de trabalho maior, a **geração** (passos 1–4) pode ser
delegada ao agent `java-construtor`. A **validação final** (passo 5) é sempre responsabilidade do
agent `java-revisor` (modo `auditoria`), independentemente de quem gerou os arquivos.

## Quem aplica o quê

| Tarefa | Quem | Skill |
|---|---|---|
| Gerar a aplicação (esqueleto + variante) | sessão principal ou `java-construtor` | esta skill |
| Decidir camada, fronteiras e DDD | `java-construtor` | `arquitetura-limpa-java` |
| DLQ, ack, interceptor de erro | `java-construtor` | `mensageria-sqs-kafka` |
| Persistência e transações | `java-construtor` | `persistencia-jpa` |
| Auditar o resultado | `java-revisor` (modo `auditoria`) | `revisao-de-codigo-java` |
