# Variante: rest-crud-banco

Leia este arquivo quando o pedido for **REST com persistência relacional** (CRUD). É a única variante da base
que passa a exigir um banco no ar — por isso o `readiness` ganha `db` e o contexto de teste precisa de um banco.

## O que adicionar sobre `assets/esqueleto`

**Dependências** (`pom.xml`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- mapeamento entidade <-> modelo de domínio; confira a versão estável vigente -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>
```

MapStruct também exige o processador de anotações no `maven-compiler-plugin`
(`<annotationProcessorPaths>` com `org.mapstruct:mapstruct-processor`). Migrations versionadas
(Flyway) seguem `../../persistencia-jpa/references/migrations-expand-contract.md`.

**Pacotes novos:** `domain/model/<Entidade>`, `domain/port/out/<Entidade>RepositoryPort`,
`application/usecase/*`, `infrastructure/persistence/{<Entidade>Entity, <Entidade>JpaRepository, <Entidade>RepositoryAdapter, <Entidade>Mapper}`.

**`application.yml`** (acrescente ao do esqueleto):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: ${DB_PASSWORD}          # nunca no repositório
    hikari:
      maximum-pool-size: 10           # dentro do orçamento: instâncias x pool <= max_connections do banco
      connection-timeout: 2000        # ms; falhar rápido em vez de empilhar requisições
  jpa:
    open-in-view: false               # sem sessão aberta até a view: evita LazyInitialization mascarado e N+1 na borda
    hibernate:
      ddl-auto: validate              # o schema é das migrations, nunca do Hibernate
  data:
    web:
      pageable:
        max-page-size: 100            # teto de página; cliente não pede 1 milhão de linhas

management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,db  # só o banco entra na readiness; liveness continua só o processo
```

## Componentes (da definição da skill)

| Variante | O que gerar | Skill de referência |
|----------|-------------|----------------------|
| **rest-crud-banco** | Modelo puro em `domain/model/`, `port/out` de repositório, use case em `application/usecase/`, e em `infrastructure/persistence/` a entidade JPA + Spring Data repo + adapter que implementa a porta (mapeamento via MapStruct). | `persistencia-jpa` |

## Proteções e provas

| Variante | Proteções obrigatórias | Prova mínima gerada |
|---|---|---|
| rest-crud-banco | Pool dentro do orçamento (`maximum-pool-size`, `connection-timeout`), timeout de consulta/transação, paginação | Teste de repositório + teste do limite de página |

## Antes / depois: porta de saída e listagem paginada

```java
// ANTES: use case injeta o JpaRepository, devolve a entidade e lista tudo
@Service
class ListarPedidos {
    private final PedidoJpaRepository repo;
    List<PedidoEntity> todos() { return repo.findAll(); }   // sem limite: derruba heap e banco
}
```

```java
// DEPOIS: o domínio declara a porta; o adapter conhece JPA e impõe o teto de página
// domain/port/out
public interface PedidoRepositoryPort {
    Pedido salvar(Pedido pedido);
    List<Pedido> listar(int pagina, int tamanho);
}

// infrastructure/persistence
@Repository
class PedidoRepositoryAdapter implements PedidoRepositoryPort {
    private static final int TAMANHO_MAXIMO = 100;
    private final PedidoJpaRepository jpa;
    private final PedidoMapper mapper;

    PedidoRepositoryAdapter(PedidoJpaRepository jpa, PedidoMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        return mapper.paraDominio(jpa.save(mapper.paraEntidade(pedido)));
    }

    @Override
    public List<Pedido> listar(int pagina, int tamanho) {
        var pageable = PageRequest.of(pagina, Math.min(tamanho, TAMANHO_MAXIMO));
        return jpa.findAll(pageable).map(mapper::paraDominio).getContent();
    }
}
```

O caso de uso depende só de `PedidoRepositoryPort`; `domain` continua sem `jakarta.persistence`.

## Fontes únicas e exemplos executáveis

- Problemas de JPA (N+1, transação, lock otimista, projeções): [persistencia-jpa](../../persistencia-jpa/SKILL.md),
  em especial [n-mais-um](../../persistencia-jpa/references/n-mais-um.md) e
  [transacoes](../../persistencia-jpa/references/transacoes.md).
- Idempotência transacional com constraint única contra Postgres real (Testcontainers):
  [ProcessadorIdempotente](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java) e
  [ProcessadorIdempotenteExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ProcessadorIdempotenteExternoIT.java)
  (perfil `integracao`, Docker obrigatório).
- Teste de repositório e limite de página: estratégia em [testes-sistemas-java](../../testes-sistemas-java/SKILL.md).
- Pool, timeout e orçamento de conexões: [resiliencia-controle-fluxo-java](../../resiliencia-controle-fluxo-java/SKILL.md).
