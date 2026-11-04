---

name: api-rest-design
description: "Design e auditoria de contratos REST para Java/Spring Boot — modelagem de recursos, OpenAPI 3.1, versionamento, paginação (offset/cursor), HATEOAS, RFC 9457 Problem Details, validação de borda. Use ao desenhar API nova, revisar contrato ou padronizar erro/paginação. Uso: agent `projetista-api` ou `/api-rest-design`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.2.0"
  domain: api-design
  triggers: REST API, OpenAPI, swagger, versionamento, paginação, HATEOAS, RFC 9457, Problem Details, contrato HTTP
  role: architect
  scope: api-contract
  output-format: document
  related-skills: arquitetura-limpa-java, revisao-de-codigo-java, seguranca-aplicacao-java
---

# API REST Design (Java/Spring Boot)

Guia de design de APIs REST aplicadas ao stack Java/Spring Boot deste catálogo. Cobre desde
modelagem de recursos e versionamento até o contrato HTTP concreto (status, Problem Details RFC 9457,
paginação, HATEOAS, validação de borda).

## Quando usar

- Desenhar API nova, modelar recursos e escrever o `openapi.yaml`.
- Auditar contrato existente: status, erro, paginação, versionamento, idempotência e quotas.
- Padronizar formato de erro (RFC 9457) e paginação entre serviços.

## Quando NÃO usar

**Quando NÃO usar:** para implementar controllers (`@RestController`), use `arquitetura-limpa-java`
(camada e padrão de DTOs). Para validar a API gerada (testes de contrato, mocks), use
`revisao-de-codigo-java` ou o agent `java-revisor` (modo `auditoria`). Para design de microsserviços (borda entre
serviços), use `arquitetura-limpa-java` (seção DDD) — esta skill é só o **contrato HTTP** de um
único serviço.

## Entradas

- Domínio e requisitos de negócio; clientes da API (públicos, parceiros, internos).
- Contrato existente (`openapi.yaml`), se houver, e política de breaking changes.
- Restrições de capacidade: quotas por cliente, SLO, deadline dos chamadores.

## Decisão

| Pergunta | Se sim | Onde ver |
|---|---|---|
| Qual status devolver (400/422/409/429/503)? | Tabela de status e distinção de sobrecarga | [convencoes-rest](references/convencoes-rest.md) |
| POST com repetição (pagamento/pedido)? | `Idempotency-Key` + 422/409 definido | [idempotencia-quotas-http](references/idempotencia-quotas-http.md) |
| Lista grande ou feed? | Cursor em vez de offset; teto de página | [paginacao](references/paginacao.md) |
| Formato de erro? | Problem Details (RFC 9457), não envelope próprio | [problem-details-rfc9457](references/problem-details-rfc9457.md) |
| Cliente precisa descobrir transições? | HATEOAS (só API pública de longa duração) | [hateoas](references/hateoas.md) |
| Começar um contrato? | Partir de `assets/openapi-base.yaml` | [openapi-31](references/openapi-31.md) |
| Entrada do cliente? | Bean Validation no record de request | [validacao-borda](references/validacao-borda.md) |

## Passo a passo

1. **Analise o domínio** — requisitos de negócio, modelos de dados, necessidades dos clientes.
2. **Modele os recursos** — identifique recursos, relacionamentos e operações antes de escrever
   qualquer linha de OpenAPI.
3. **Defina endpoints** — URI patterns, métodos HTTP, schemas de request/response (seção
   "Convenções REST" abaixo como checklist).
4. **Especifique o contrato** — escreva o `openapi.yaml` (3.1) a partir de `assets/openapi-base.yaml`; valide com
   `npx @redocly/cli lint openapi.yaml`.
5. **Moque e verifique** — `npx @stoplight/prism-cli mock openapi.yaml` antes de implementar.
6. **Planeje a evolução** — versionamento, deprecation, política de breaking changes.

## Saída

- `openapi.yaml` 3.1 válido (base: `assets/openapi-base.yaml`) com erros em `application/problem+json`
  (exemplo de corpo: `assets/problem-details.json`).
- Lista de decisões de contrato (tradeoffs abaixo) registrada no PR ou ADR.

## Validação

- `npx @redocly/cli lint openapi.yaml` sem erros; mock com Prism responde os exemplos.
- Todo endpoint documenta 400/401/403/404/422 aplicáveis e, quando exposto sob carga, 429/503 com `Retry-After`.
- POST sujeito a repetição declara `Idempotency-Key` e o status de payload divergente (422 ou 409).

## Gotchas

- Misturar envelope customizado e Problem Details causa inconsistência: escolha **um**.
- 400 é formato; 422 é regra de negócio; não use 500 para validação.
- 429 é culpa do cliente (quota); 503 é saturação do serviço e conta no SLO.
- Quota por `X-Forwarded-For` livre é burlável: identidade vem do token autenticado.
- Aninhamento de recursos acima de 2 níveis e IDs auto-incrementais vazam volume e permitem enumeração.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [references/convencoes-rest.md](references/convencoes-rest.md) | Envelope, mapeamento de status, sobrecarga/quotas, URL e versionamento nativo Boot 4 |
| [references/idempotencia-quotas-http.md](references/idempotencia-quotas-http.md) | `Idempotency-Key`, 429 + `Retry-After`, 503 por admissão, deadline; aponta para [resiliencia-controle-fluxo-java](../resiliencia-controle-fluxo-java/references/capacidade-e-limites.md) e [cache-protecao-java](../spring-data-redis/references/cache-protecao-java.md) |
| [references/paginacao.md](references/paginacao.md) | Payload de página, limite de tamanho, offset vs cursor |
| [references/problem-details-rfc9457.md](references/problem-details-rfc9457.md) | Payload de erro RFC 9457 e handler global |
| [references/hateoas.md](references/hateoas.md) | Links hypermedia: quando usar e como modelar |
| [references/openapi-31.md](references/openapi-31.md) | Contrato OpenAPI 3.1, lint, mock e geração de código |
| [references/validacao-borda.md](references/validacao-borda.md) | Bean Validation no request, 400 vs 422 |
| [assets/openapi-base.yaml](assets/openapi-base.yaml) | Esqueleto OpenAPI 3.1: paginação, ProblemDetail, 429/503 com `Retry-After` |
| [assets/problem-details.json](assets/problem-details.json) | Corpo de erro RFC 9457 de exemplo |

Código executável relacionado (em `examples/java`):
[`AdmissaoPorPrioridade`](../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/AdmissaoPorPrioridade.java) (origem do 503),
[`TokenBucket`](../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/TokenBucket.java) (quota/429),
[`ProcessadorIdempotente`](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java) (idempotência).

## Tradeoffs comuns (decidir antes de implementar)

| Decisão | Opção A | Opção B | Quando escolher A | Quando escolher B |
|---|---|---|---|---|
| Envelope de resposta | Customizado (`{success, data, error, ...}`) | Problem Details (RFC 9457) | API interna, cliente único, quer timestamp em toda resposta | API pública/padrão IETF, múltiplos clientes, evolução a longo prazo |
| Versionamento | Em path (`/api/v1`) | Em header (`Accept-Version`) | Cacheable, fácil de debugar (`curl /api/v1`) | "URLs limpas", múltiplas versões ativas simultaneamente |
| Paginação | Offset (`page`/`size`) | Cursor (`?cursor=`) | UI com páginas numeradas, dataset pequeno | Feed infinito, dataset grande, inserts concorrentes |
| Identificador | UUID | Long auto-incremento | Distribuído, sem enumeração | Humano-legível, debugging fácil |
| Documentação | OpenAPI manual | Anotações Spring (`@Operation`) | Fonte de verdade versionada, gera SDK | Documentação "viva" só no backend, sem cliente gerado |
| Validação | Bean Validation (`@Valid`) | Schema custom no service | Padrão JSR-380, mensagens i18n | Lógica muito específica que anotações não expressam |
| Estilo de API | REST | gRPC / GraphQL / WebSocket | Recursos, cache HTTP, clientes heterogêneos | Ver `design-system-architecture` → protocolos (contrato tipado, streaming, projeção pelo cliente, duplex) |
| Operação longa | Síncrona com deadline | 202 Accepted + recurso de status | Termina dentro do deadline do cliente | Excede o deadline; cliente consulta/recebe callback |

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Desenhar API nova, modelar recursos | sessão principal | esta skill |
| Implementar controller e DTOs | session principal ou `java-construtor` | `arquitetura-limpa-java` |
| Auditar contrato de API existente | agent `java-revisor` (modo `auditoria`) | esta skill + `revisao-de-codigo-java` |
| Definir estratégia de microsserviço (fronteira entre serviços) | sessão principal | `arquitetura-limpa-java` (seção DDD) |
