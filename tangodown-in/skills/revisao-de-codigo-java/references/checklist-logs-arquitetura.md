# Checklist 9–10 — Logs e arquitetura

Leia este arquivo ao revisar logs (dado sensível, contexto, nível) e a camada onde a mudança caiu (domain/application/infrastructure).

### 9. Logs

Siga `monitoramento-java/references/logs-*.md` para o formato e a estrutura completos. Nesta revisão, verifique:

- nenhum dado sensível (senha, token, CPF, cartão) aparece em log, nem mesmo em nível `debug`;
- a mensagem carrega contexto suficiente para investigar sem precisar reproduzir (IDs de negócio,
  não só "erro ao processar");
- o nível (`info`/`warn`/`error`) condiz com a severidade real do evento.

### 10. Arquitetura

Siga a skill `arquitetura-limpa-java` para o mapa completo de camadas e o checklist arquitetural.
Nesta revisão, verifique pelo menos:

- a camada onde a mudança caiu é a correta (regra de negócio em `domain`, orquestração em
  `application/usecase`, adaptador em `infrastructure`);
- nenhuma dependência aponta "para fora" (`domain` não importa `org.springframework.*` nem
  `jakarta.persistence.*`; `application` não importa `jakarta.servlet.*` nem Spring Data);
- todo acesso a recurso externo passa por uma `port/out` do `domain`, implementada por um adapter.


## Exemplo antes/depois — log com dado sensível e sem contexto

**[❌ Código Não Aderente]:**
```java
// vaza CPF e token, e nao diz qual pedido falhou
log.error("Erro ao processar: " + e.getMessage() + " cpf=" + cliente.cpf() + " token=" + token);
```

**[✅ Exemplo de Refatoração]:**
```java
// id de negocio como contexto, dado sensivel fora do log, causa preservada no ultimo argumento
log.error("Falha ao processar pedido pedidoId={} etapa=cobranca", pedido.id(), e);
```

## Exemplo antes/depois — dependência apontando "para fora"

**[❌ Código Não Aderente]:**
```java
package br.com.srportto.pedidos.domain;

import jakarta.persistence.Entity; // domain acoplado a JPA
import org.springframework.stereotype.Component; // domain acoplado a Spring
```

**[✅ Exemplo de Refatoração]:**
```java
package br.com.srportto.pedidos.domain.port.out;

// domain declara a necessidade; o adapter em infrastructure implementa com JPA
public interface PedidoRepositoryPort {
    Optional<Pedido> buscarPorId(PedidoId id);
    void salvar(Pedido pedido);
}
```
