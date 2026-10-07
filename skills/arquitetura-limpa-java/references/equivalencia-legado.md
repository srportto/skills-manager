Leia este arquivo ao migrar uma aplicação do layout anterior (`entrypoint`/`application`/`domain`/`shared`) para o hexagonal de referência, ou ao decidir se código no layout antigo é defeito.

## Equivalência com a estrutura legada do monorepo

> **Contexto externo:** esta seção descreve o monorepo de origem do catálogo (`apps/`, `openspec/changes/`)
> e é mantida como exemplo de migração de layout. Esses caminhos não existem neste repositório.

A migração das cinco aplicações de `apps/` do layout anterior
(`entrypoint`/`application`/`domain`/`shared`) para o de referência é trabalho em andamento,
app por app (ver `openspec/changes/hexagonal-classico-*`). Estado em 2026-08-15: `contratocommand`
já está no layout de referência, domínio incluindo a separação modelo/entidade JPA
(`hexagonal-classico-contratocommand-portas` + `hexagonal-classico-contratocommand-dominio-puro`).
`contratoquery`, `autorizacaostatus-producer`, `eventos-consumer` e `temporiza-autorizacao` ainda
usam o layout anterior. **Código existente no layout anterior não é defeito** até ser migrado — o
alvo desta tabela é orientar a migração e impedir que aplicação nova nasça no formato antigo.

| Layout legado | Layout de referência |
|---|---|
| `entrypoint/` (controller, DTOs) | `infrastructure/web/` |
| `entrypoint/sqs/`, `entrypoint/kafka/` | `infrastructure/messaging/` |
| `application/<contexto>/*Service` | `application/usecase/` + interface em `domain/port/in/` |
| `application/<contexto>/*Repository` (JPA) | `domain/port/out/` + `infrastructure/persistence/` |
| `domain/entities/*` (entidade JPA no domínio) | `domain/model/` (puro) + `*JpaEntity` em `infrastructure/persistence/` |
| `domain/model/`, `domain/enums/` | inalterados |
| `shared/` exceções de negócio | `domain/exception/` |
| `shared/` handler de erro, interceptadores | `infrastructure/web/` |
| `shared/config/` | `infrastructure/config/` |
