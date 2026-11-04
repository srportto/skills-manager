---
name: arquitetura-limpa-java
description: "Referência para decidir a camada de um código em app hexagonal Java/Spring Boot (ports & adapters) — `domain` / `application` / `infrastructure` —, estrutura de pacotes, DDD tático (aggregate, value object, domain event, specification, ACL) e decomposição de monólito em bounded contexts; cobre também app não hexagonal (camadas clássicas) e escolha de módulos Spring. Use em dúvida de camada, revisão de fronteiras, modelagem de domínio ou escolha de stack. Uso: agent `java-revisor` (modo `auditoria`) ou `/arquitetura-limpa-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "3.0.0"
  domain: architecture
  triggers: onde coloco, qual camada, estrutura de pacotes, arquitetura limpa, arquitetura hexagonal, ports and adapters, porta, adaptador, bounded context, decompor monólito, hexagonal, aggregate, agregado, value object, domain event, specification, anti-corruption layer, DDD, camadas clássicas, controller service repository, Spring Security, Spring Data JPA, WebFlux, arquitetura Spring Boot
  role: architect
  scope: code-organization
  output-format: document
  related-skills: design-system-architecture, criar-aplicacao-java, revisao-de-codigo-java, testes-sistemas-java
---

# Arquitetura Limpa Java (Hexagonal clássica + DDD)

## Visão geral

Referência de bolso para decidir **em qual camada um código deve viver** em uma aplicação Java/Spring
Boot que segue a **arquitetura hexagonal clássica (ports & adapters)** — `domain` / `application` /
`infrastructure` — e para aplicar **DDD** ao decompor fronteiras entre contextos (microsserviço ou
módulo). Cobre também a variante **não hexagonal** (camadas clássicas) e a escolha de módulos Spring,
em `references/`.

## Quando usar

- Dúvida sobre em qual camada colocar uma classe, estrutura de pacotes ou revisão de fronteiras.
- Modelagem de domínio (aggregate, value object, domain event, specification, ACL).
- Decomposição de monólito em bounded contexts.
- App existente em camadas clássicas (`controller`/`service`/`repository`) ou escolha de módulos Spring.

**Quando NÃO usar:** para gerar o esqueleto de uma aplicação nova, use `criar-aplicacao-java` (que
aplica exatamente este layout). Para mensageria, use `mensageria-sqs-kafka`. Para persistência JPA,
use `persistencia-jpa`. Para estrutura de testes e Testcontainers, use `testes-sistemas-java`. Para
revisão de código completa, use `revisao-de-codigo-java`. Para um design pattern GoF, use
`padroes-de-projeto-java`.

## Entradas

- Código, classe ou diff em dúvida (ou descrição do requisito).
- Estilo da aplicação: hexagonal (padrão do catálogo) ou camadas clássicas.
- Se for decomposição: contextos candidatos, times e dados envolvidos.

## Decisão: hexagonal (padrão) × camadas clássicas

| Situação | Estilo | Onde ler |
|---|---|---|
| App nova ou migração deste catálogo | **Hexagonal** (`domain` / `application` / `infrastructure`) | este arquivo |
| App existente em `controller`/`service`/`repository` que não migrou | Camadas clássicas | [camadas-classicas](references/camadas-classicas.md) |
| Escolha de módulo Spring (Web × WebFlux, JPA, Security, Redis) | Independe do estilo | [modulos-spring](references/modulos-spring.md) |
| Dúvida é "como modelar" | DDD tático | [ddd-tatico](references/ddd-tatico.md) |
| Dúvida é "em qual serviço" | Bounded contexts | [decomposicao-bounded-contexts](references/decomposicao-bounded-contexts.md) |

## As três camadas e a regra de dependência

```
        ┌──────────────────── infrastructure ────────────────────┐
        │  driving adapters            driven adapters           │
        │  web/ · messaging/     persistence/ · external/        │
        │         │                          ▲                   │
        │         ▼                          │                   │
        │   ┌──────────── application (use cases) ─────────┐      │
        │   │                    │        ▲                │      │
        │   │                    ▼        │                │      │
        │   │   ┌──────────── domain ──────────────┐       │      │
        │   │   │  model/ · service/               │       │      │
        │   │   │  port/in  (o que a app oferece)  │       │      │
        │   │   │  port/out (o que a app precisa)  │       │      │
        │   │   └──────────────────────────────────┘       │      │
        │   └──────────────────────────────────────────────┘      │
        └────────────────────────────────────────────────────────┘
                     dependências apontam SEMPRE para dentro
```

- **`domain`** — Java puro. Zero `org.springframework.*`, zero `jakarta.persistence.*`, zero Jackson.
  Testável sem subir contexto Spring.
- **`application`** — implementa as `port/in` orquestrando `domain` + `port/out`. Spring é permitido
  aqui (`@Service`, `@Transactional`), mas nada de HTTP, JPA ou SDK de broker.
- **`infrastructure`** — todo detalhe de framework: controllers, listeners, entidades JPA, clientes
  HTTP, configs. **Driving adapters** (web, messaging de entrada) chamam `port/in`; **driven
  adapters** (persistence, external, messaging de saída) implementam `port/out`.
- Inversão de dependência é o coração do padrão: `domain` **declara** a interface de que precisa
  (`port/out`), `infrastructure` **implementa**. Assim a seta continua apontando para dentro mesmo
  quando o fluxo de execução vai para fora.

## Estrutura de pacotes

```
br.com.srportto.<app>/
├── domain/                        ← Java puro, sem framework
│   ├── model/                     ← entidades de negócio, value objects, agregados
│   ├── port/in/                   ← driving ports: interfaces de use case + commands
│   ├── port/out/                  ← driven ports: repositórios, gateways, publishers
│   ├── service/                   ← domain services (regra que não cabe num único agregado)
│   ├── enums/
│   └── exception/                 ← BusinessException e exceções de negócio
├── application/
│   └── usecase/                   ← @Service implementando port/in
└── infrastructure/
    ├── web/                       ← @RestController, DTOs de request/response, ApiExceptionHandler
    ├── messaging/                 ← listener SQS / consumer Kafka (in), producer (out)
    ├── persistence/               ← entidade JPA + Spring Data repo + adapter da port/out
    ├── external/                  ← clientes HTTP de outros serviços
    └── config/                    ← @Configuration, beans, properties
```

## Que classe vai em qual camada

| Tipo de classe | Camada | Exemplo |
|---|---|---|
| Modelo de negócio, value object, agregado | `domain/model/` | `Pedido`, `PedidoId`, `Money` |
| Interface de use case + command | `domain/port/in/` | `CriarPedidoUseCase`, `CriarPedidoCommand` |
| Interface de repositório/gateway/publisher | `domain/port/out/` | `PedidoRepository`, `EstoquePort` |
| Regra pura entre agregados | `domain/service/` | `CalculadoraDeFrete` |
| Enum de negócio, exceção de negócio | `domain/enums/`, `domain/exception/` | `StatusPedido`, `BusinessException` |
| Implementação do use case (orquestra) | `application/usecase/` | `CriarPedidoService` |
| Controller REST + DTOs de request/response | `infrastructure/web/` | `PedidoController`, `CriarPedidoRequest` |
| Handler global de erro (`@RestControllerAdvice`) | `infrastructure/web/` | `ApiExceptionHandler` |
| Listener SQS, consumer Kafka (driving adapter) | `infrastructure/messaging/` | `PedidoSqsListener` |
| Producer Kafka/SNS (driven adapter, implementa `port/out`) | `infrastructure/messaging/` | `KafkaEventoPublisher` |
| Entidade JPA, Spring Data repo, adapter de persistência | `infrastructure/persistence/` | `PedidoJpaEntity`, `PedidoJpaAdapter` |
| Cliente HTTP de outro serviço (implementa `port/out`) | `infrastructure/external/` | `EstoqueHttpClient` |
| `@Configuration`, beans, properties | `infrastructure/config/` | `KafkaConfig`, `ObjectMapperConfig` |

> **Entidade JPA ≠ modelo de domínio.** `PedidoJpaEntity` (com `@Entity`, `@Column`, `@Version`) vive
> em `infrastructure/persistence/`; `Pedido` (Java puro, com invariantes) vive em `domain/model/`. Um
> mapper no adapter converte um no outro. É isso que mantém o `domain` livre de `jakarta.persistence`.

## Exemplo mínimo (as quatro peças)

```java
// domain/port/in/CriarPedidoUseCase.java — driving port
public interface CriarPedidoUseCase {
    Pedido executar(CriarPedidoCommand command);
}

// domain/port/out/PedidoRepository.java — driven port (NÃO é JpaRepository)
public interface PedidoRepository {
    Pedido salvar(Pedido pedido);
    Optional<Pedido> buscarPorId(PedidoId id);
}

// application/usecase/CriarPedidoService.java — orquestra, sem conhecer JPA nem HTTP
@Service
@Transactional
public class CriarPedidoService implements CriarPedidoUseCase {
    private final PedidoRepository repository;   // port/out
    private final EstoquePort estoque;           // port/out

    @Override
    public Pedido executar(CriarPedidoCommand command) {
        estoque.reservar(command.itens());
        return repository.salvar(Pedido.criar(command.clienteId(), command.itens()));
    }
}

// infrastructure/persistence/PedidoJpaAdapter.java — driven adapter
@Component
public class PedidoJpaAdapter implements PedidoRepository {
    private final SpringDataPedidoRepository jpa;   // detalhe interno do pacote
    private final PedidoPersistenceMapper mapper;

    @Override
    public Pedido salvar(Pedido pedido) {
        return mapper.paraDominio(jpa.save(mapper.paraEntidade(pedido)));
    }
}
```

O `@RestController` (driving adapter) injeta a **porta** `CriarPedidoUseCase`, nunca a implementação
`CriarPedidoService`.

## Mapa de erros e onde lançar

| Exceção/mecanismo | HTTP | Onde lançar |
|---|---|---|
| `BusinessException` (definida em `domain/exception/`) | 422 | `domain` (regra pura) ou `application` (orquestração) |
| `ApplicationException` | 500 | `application`/`infrastructure`, falha técnica inesperada |
| `@Valid` (Bean Validation) | 422 neste monorepo | **somente** nos DTOs de `infrastructure/web/` |

O tratamento centralizado é o `ApiExceptionHandler` (`@RestControllerAdvice`) em
`infrastructure/web/` — nenhuma outra classe monta `ResponseEntity` de erro.

> **Convenção deste monorepo (decisão de 2026-08-09, D3 da change `reconciliar-contrato-spec-doc`):**
> tanto `@Valid` quanto `BusinessException` respondem **422**; a distinção formato × regra é carregada
> pelo *shape* do corpo (`LayoutErrosApiValidationsResponse` vs `LayoutErrosApiResponse`), não pelo
> status. Em projeto fora deste monorepo, o default de mercado para `@Valid` é 400.

## Passo a passo

1. Identifique o estilo da aplicação (tabela de decisão) — código novo nasce hexagonal.
2. Classifique cada classe na tabela "Que classe vai em qual camada".
3. Cheque a regra de dependência: `domain` sem Spring/JPA/Jackson; `application` sem HTTP/JPA/SDK de broker; `infrastructure` implementa `port/out`.
4. Confirme que entidade JPA e modelo de domínio são classes distintas, com mapper no adapter.
5. Valide o mapa de erros: exceção de negócio no `domain`/`application`, tratamento só no `ApiExceptionHandler`.
6. Para o que não cabe aqui (DDD, bounded contexts, anti-padrões, legado, camadas clássicas, módulos Spring), siga o Guia de references.

## Saída

Veredicto por classe (camada correta ou movimento sugerido), violações da regra de dependência com a correção, e — quando houver modelagem — o agregado/value objects propostos. Em revisão, cite arquivo e linha.

## Validação

- `domain/` não importa `org.springframework.*`, `jakarta.persistence.*` nem Jackson.
- Use case injeta `port/out`, nunca `JpaRepository` nem cliente HTTP.
- Controller injeta `port/in` e não contém regra de negócio.
- `./mvnw verify` da aplicação passa (compilação não é teste; teste pulado é pendência).

## Gotchas

Os gotchas recorrentes de agents (JPA no `domain/`, `JpaRepository` no use case, `@Transactional` em `domain/service`, `port/in` × `port/out` trocados, domínio anêmico, `@MockBean` no Boot 4, `spring-boot-starter-aop`) e os 7 anti-padrões estão em [anti-padroes-e-gotchas](references/anti-padroes-e-gotchas.md).

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [anti-padroes-e-gotchas](references/anti-padroes-e-gotchas.md) | Revisando fronteira de camada ou PR; suspeita de JPA/HTTP vazando para `application` |
| [equivalencia-legado](references/equivalencia-legado.md) | Migrando do layout anterior (`entrypoint`/`shared`) para o hexagonal |
| [ddd-tatico](references/ddd-tatico.md) | Modelando aggregate, value object, domain event, specification ou ACL |
| [decomposicao-bounded-contexts](references/decomposicao-bounded-contexts.md) | Decidindo "em qual serviço"; fronteiras, comunicação, resiliência e probes |
| [camadas-classicas](references/camadas-classicas.md) | App não hexagonal (`controller`/`service`/`repository`), DTOs e injeção de dependência |
| [modulos-spring](references/modulos-spring.md) | Escolhendo módulos Spring, resource server JWT, virtual threads e proteção contra sobrecarga |

## Quem aplica o quê

| Situação | Quem | Skill usada |
|---|---|---|
| Dúvida sobre em qual camada colocar uma classe | sessão principal | esta skill |
| Desenhar arquitetura de aplicação Spring Boot nova | sessão principal | esta skill |
| Revisão arquitetural completa (camadas + DDD) | agent `java-revisor` (modo `auditoria`) | esta skill + `revisao-de-codigo-java` |
| Revisar estrutura de pacotes e escolhas de stack | agent `java-revisor` (modo `auditoria`) | `revisao-de-codigo-java` |
| Decompor monolito em microsserviços (design) | sessão principal (design, não há agent dedicado) | esta skill |
| Aplicar microsserviço novo (gerar) | agent `java-construtor` | `criar-aplicacao-java` + esta skill |
| Tuning de JPA/Hibernate | session principal | `persistencia-jpa` |
| Configurar segurança (JWT, CORS, headers) | session principal | `seguranca-aplicacao-java` |
| Configurar observabilidade | session principal | `monitoramento-java` |
