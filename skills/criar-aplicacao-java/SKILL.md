---

name: criar-aplicacao-java
description: "Gera esqueleto buildável de app Spring Boot 4 + Java 25 em hexagonal clássica (domain/application/infrastructure), com rota `/disponibilidade` e variante escolhida (REST, CRUD com banco, SQS listener, Kafka consumer, ponte SQS→Kafka, etc.). Use ao criar aplicação, microsserviço ou esqueleto novo. Uso: agent `java-construtor` (validado por `java-revisor` modo `auditoria`) ou `/criar-aplicacao-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "2.0.0"
  domain: application-scaffolding
  triggers: crie uma aplicação, novo microserviço, esqueleto de app java, app que consome fila, consumidor kafka, hexagonal Spring Boot, ports and adapters
  role: builder
  scope: application-generation
  output-format: code
  related-skills: arquitetura-limpa-java, mensageria-sqs-kafka, persistencia-jpa, java-moderno
---

# Criar Aplicação Java (Spring Boot, hexagonal clássica)

## Visão geral

Gera uma aplicação Spring Boot **buildável** seguindo a **arquitetura hexagonal clássica (ports &
adapters)** — camadas `domain` / `application` / `infrastructure`, ver `arquitetura-limpa-java` — com
uma rota de disponibilidade pronta, combinada, opcionalmente, com uma **variante** que adiciona a
funcionalidade pedida (CRUD com banco, consumo de fila, ponte de mensageria, etc.). Não há templates
físicos para copiar neste catálogo — cada aplicação é gerada do zero seguindo os requisitos desta
skill e das skills referenciadas (`arquitetura-limpa-java`, `mensageria-sqs-kafka`,
`persistencia-jpa`, `java-moderno`).

**Princípio central:** a base (sem variante) roda **sem depender de nenhuma infraestrutura externa**
— nem banco, nem fila, nem broker. É o "esqueleto" seguro para começar qualquer aplicação. Cada
variante é quem introduz (e exige) a infraestrutura que passa a ser necessária.

A base entrega sempre:
- Pacote `br.com.srportto.<nome>`, classe principal `<Nome>Application`
- As três camadas já materializadas (ver "Layout gerado" abaixo), com o health check passando por
  uma porta — é o exemplo vivo do padrão dentro do próprio esqueleto
- Rota `GET /disponibilidade` → `200 OK`, corpo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`
- Tratamento de erros (`BusinessException` → 422, `ApplicationException` → 500, validação de bean)
- Actuator com probes: `/actuator/health/liveness` (só o processo) e `/actuator/health/readiness` (estado +
  dependências necessárias da variante) — semântica em `monitoramento-java`; `/disponibilidade` é smoke test
- Limites básicos de borda: tamanho máximo de requisição e timeouts explícitos em todo cliente gerado
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
│   └── usecase/               ← ConsultarDisponibilidadeService (@Service, implementa a port/in)
└── infrastructure/
    ├── web/                   ← DisponibilidadeController, DTOs, ApiExceptionHandler
    └── config/                ← @Configuration
```

> **Nunca** gere aplicação nova no layout legado `entrypoint`/`application`/`domain`/`shared` (usado por
> aplicações do monorepo de origem — contexto externo); a tabela de equivalência está em
> `arquitetura-limpa-java`.

## Quando usar / Quando NÃO usar

**Use esta skill quando** o pedido for para **criar uma aplicação/microserviço nova do zero** — REST
puro, CRUD com banco, listener de fila SQS, ponte SQS→banco, ponte SQS→Kafka, consumidor Kafka, ou
REST que publica em Kafka.

**NÃO use esta skill para:**
- Adicionar uma feature/endpoint/entidade em uma aplicação **já existente** — use
  `arquitetura-limpa-java`.
- Tirar dúvidas sobre mensageria sem a intenção de criar uma aplicação nova — use
  `mensageria-sqs-kafka`.

## Parâmetros: pergunte só o que falta

Use o que o pedido já informou. **Nome da aplicação** e **variante** não têm default seguro: se faltarem,
pergunte. Os demais têm default declarado — aplique-o sem perguntar e liste na entrega os valores assumidos,
para o usuário poder corrigir.

| Parâmetro | Uso | Default (se não informado) |
|-----------|-----|---------|
| **Nome da aplicação** | deriva `artifactId`, pacote `br.com.srportto.<nome>`, classe `<Nome>Application`, `spring.application.name` | — (perguntar) |
| **Variante** | base pura ou uma das 6 variantes — ver tabela abaixo | — (perguntar; "só um CRUD" = `rest-crud-banco`) |
| **Nome da pasta destino** | diretório onde o projeto será gerado | `<nome>-service` |
| **Porta** | `server.port` | `8080` |
| **Profile default** | `spring.profiles.default` | `local` |
| **Container web** | Tomcat (default) ou Jetty — ver abaixo | Tomcat |
| **Carga esperada** | taxa/pico, dependências — decide limites e proteções | baixa; proteções mínimas de borda |

> Derive os identificadores do "nome da aplicação": pacote = `br.com.srportto.<nome>` (minúsculo),
> classe principal = `<Nome>Application` (PascalCase).

### Container web

`spring-boot-starter-webmvc` traz **Tomcat** por padrão.

| Container | Quando preferir |
|-----------|------------------|
| **Tomcat** (default) | Sem alteração; máxima compatibilidade, maior base de troubleshooting. |
| **Jetty** | Cloud-native/containers, alta concorrência, muitos WebSockets/streaming. |

> ⚠️ **Undertow NÃO existe no Spring Boot 4.x** (o BOM só gerencia Tomcat e Jetty para web MVC, mais
> reactor-netty para reativo). `spring-boot-starter-undertow` falha com "version is missing".

Para Jetty, exclua o Tomcat do starter web e adicione o starter Jetty:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```
> Após trocar o container, valide no log de startup a linha do servidor ativo (`Jetty started on port
> <porta>` em vez de `Tomcat started on port <porta>`).

### Variante — componentes obrigatórios

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **base pura** | Só a base hexagonal, sem infra externa. | — |
| **rest-crud-banco** | Modelo puro em `domain/model/`, `port/out` de repositório, use case em `application/usecase/`, e em `infrastructure/persistence/` a entidade JPA + Spring Data repo + adapter que implementa a porta (mapeamento via MapStruct). | `persistencia-jpa` |
| **sqs-listener** | Listener (driving adapter) em `infrastructure/messaging/` com idempotência (em memória só para demonstração de instância única; persistente em produção — ver `sqs-para-banco`), **interceptor central de erro de consumo** (`infrastructure/messaging/*ErrorInterceptor`) e **fila provisionada com DLQ + `RedrivePolicy`** (nunca uma sem a outra). | `mensageria-sqs-kafka` (seções 2 e 3) |
| **sqs-para-banco** | Como acima + idempotência **persistente** (constraint única) + gravação via `port/out` e adapter JPA. | `mensageria-sqs-kafka`, `persistencia-jpa` |
| **sqs-para-kafka** | Ponte: consome SQS (interceptor + DLQ, como acima) e republica no Kafka através de uma `port/out` implementada por um producer em `infrastructure/messaging/`. | `mensageria-sqs-kafka` |
| **kafka-consumer** | `@KafkaListener` em `infrastructure/messaging/` + `DefaultErrorHandler`/`DeadLetterPublishingRecoverer` central (o ponto único de erro é o próprio `DefaultErrorHandler`, configurado em `infrastructure/config/`). | `mensageria-sqs-kafka` (seções 3 e 5) |
| **rest-para-kafka** | Endpoint REST (`POST /eventos`) que chama um use case, o qual publica pela `port/out` implementada em `infrastructure/messaging/`. | `mensageria-sqs-kafka` (seção 4) |

> Em toda variante, o adaptador **nunca** conversa com outro adaptador: a entrada chama uma `port/in`
> e a saída é sempre uma `port/out` declarada no `domain`.

**Toda variante que envolva SQS SHALL nascer com DLQ na fila e com o interceptor central de erro de
consumo** — não é opcional, é parte da definição da variante (ver regra de ouro em
`mensageria-sqs-kafka` seção 2 e o padrão da seção 3).

### Proteções e provas por variante

Proporcional ao risco: a base não ganha broker, cache, WebFlux nem Resilience4j sem necessidade. Variantes com
dependência remota ou mensageria nascem com a proteção pertinente **e** o teste que a prova.

| Variante | Proteções obrigatórias | Prova mínima gerada |
|---|---|---|
| base pura / REST | Limite de payload, paginação com tamanho máximo, timeouts em clientes | Teste de contrato (status/erro) |
| rest-crud-banco | Pool dentro do orçamento (`maximum-pool-size`, `connection-timeout`), timeout de consulta/transação, paginação | Teste de repositório + teste do limite de página |
| sqs-listener / sqs-para-banco | Mensagens em voo limitadas, visibility timeout coerente (ou renovação), DLQ + RedrivePolicy, idempotência (persistente em `sqs-para-banco`), delete só após efeito | Duplicata não repete efeito; falha não apaga mensagem |
| sqs-para-kafka | Tudo de SQS + producer `acks=all`/idempotente com timeout; delete SQS só após confirmação do Kafka | Falha do Kafka não apaga a mensagem SQS |
| kafka-consumer | `max.poll.records` dimensionado, commit após efeito, `DefaultErrorHandler` com tentativas limitadas + DLT, idempotência | Reentrega não duplica efeito; DLT indisponível não commita |
| rest-para-kafka | Deadline na publicação; 503 quando o broker não confirma; outbox se houver escrita em banco no mesmo fluxo | Broker fora → 503 sem evento fantasma |

Detalhes: `resiliencia-controle-fluxo-java`, `mensageria-sqs-kafka`, `testes-sistemas-java`.

## Fluxo de geração

1. **Gerar a base**: estrutura `domain`/`application`/`infrastructure` (ver "Layout gerado"), classe
   principal, rota `/disponibilidade` atendida via `port/in`, tratamento de erro genérico e teste de
   contexto — seguindo `arquitetura-limpa-java`. Aplique o container web escolhido no `pom.xml`.

2. **Aplicar a variante**, se houver: gere os componentes obrigatórios da tabela acima, seguindo a
   skill de referência indicada. Para variantes com SQS, provisione a fila com DLQ (IaC local, ex.
   Terraform contra o Floci em `http://localhost:4566`) e implemente o interceptor central de erro **no mesmo passo** — não
   deixe para depois.

3. **Buildar e testar**: `mvn clean verify`. Testes que dependem de infraestrutura externa usam
   Testcontainers (Docker) — para SQS e demais serviços AWS, `FlociContainer` (`io.floci:testcontainers-floci`) — num perfil separado (`-Pintegracao`); se o ambiente não tiver Docker/emulador,
   **não** use `-DskipTests` para declarar sucesso — relate compilação, testes executados e testes
   **pendentes** separadamente (o `java-revisor` trata pendência como pendência, não aprovação).

4. **Smoke test**: suba a aplicação (`mvn spring-boot:run`) e confirme `GET /disponibilidade`
   respondendo `{"aplicacao":"<nome>","status":"DISPONIVEL"}`.

5. **Validação obrigatória**: invoque o agent `java-revisor` (modo `auditoria`), passando a lista de arquivos
   gerados e a saída do build. Quando a aplicação tocar mensageria, o agent valida também DLQ e
   interceptor central (ver `mensageria-sqs-kafka` seção 8). Achados **críticos** bloqueiam a
   entrega — corrija e revalide antes de considerar a tarefa concluída.

## Delegação

Quando o pedido ocorrer dentro de um contexto de trabalho maior, a **geração** (passos 1–4) pode ser
delegada ao agent `java-construtor`. A **validação final** (passo 5) é sempre responsabilidade do
agent `java-revisor` (modo `auditoria`), independentemente de quem gerou os arquivos.

## Erros comuns

| Sintoma | Causa / correção |
|---------|-------------------|
| App não sobe: "Failed to configure a DataSource" | Incluiu `spring-boot-starter-data-jpa` sem o banco no ar. Suba o banco ou remova a dependência se não for usá-la. |
| `mvnw.cmd` quebrado/falha no Windows | Use `mvn` diretamente em vez do wrapper. |
| Plugin do Spring Boot não empacota `void main()` ("Unable to find main class" no `repackage`) | Use `public static void main(String[] args)` — o plugin 4.0.7 não reconhece o `void main()` do JDK 25 (verificado em 2026-10-06). |
| Porta ocupada | Escolha uma porta livre ou pare o processo conflitante. |
| Container web errado no log de startup | Confira se a exclusão do Tomcat + starter Jetty foi aplicada corretamente no `pom.xml`. |
| `NoSuchBeanDefinitionException` para `ObjectMapper` (variantes SQS/Kafka com Jackson) | Spring Boot 4 usa Jackson 3 por padrão e não cria um `ObjectMapper` clássico automaticamente — declare o bean explicitamente. |
| `mvn test` completo falha com `SdkClientException: Connection refused` | Variante com SQS exige o Floci rodando (`docker run -d -p 4566:4566 floci/floci:2.2.0`) e a fila já criada antes de rodar a suíte completa — não é bug. |

## Checklist final

- [ ] Nome e variante informados ou confirmados; demais parâmetros listados com os defaults assumidos
- [ ] Evidência registrada separadamente: compilação, testes unitários, integração (executado ou **pendente**
      com motivo) — sem `-DskipTests` como prova
- [ ] Proteções e provas da variante (tabela "Proteções e provas por variante") presentes
- [ ] Probes `/actuator/health/liveness` e `/readiness` com a semântica de `monitoramento-java`
- [ ] Rota `GET /disponibilidade` responde com o nome correto da aplicação
- [ ] Estrutura hexagonal clássica (`domain` com `model`/`port/in`/`port/out`, `application/usecase`,
      `infrastructure` com os adapters) presente e completa
- [ ] `domain` sem nenhum import de `org.springframework.*`, `jakarta.persistence.*` ou Jackson
- [ ] Todo adapter de saída implementa uma `port/out`; nenhum use case injeta `JpaRepository`,
      `RestClient` ou SDK de broker diretamente
- [ ] Se a variante envolve SQS: fila tem DLQ + `RedrivePolicy`, e existe interceptor central de erro
- [ ] Veredicto do agent `java-revisor` (modo `auditoria`) sem achados críticos
