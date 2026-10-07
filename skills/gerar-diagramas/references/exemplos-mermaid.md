# Exemplos Mermaid do domínio de checkout

Leia este arquivo quando for escrever um diagrama novo e quiser um modelo válido (renderiza no GitHub) para
cada tipo: visão de containers, sequência, máquina de estados e modelo de dados. Todos usam o domínio de
checkout executável em
[`CheckoutApplication`](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java)
e
[`ProcessadorIdempotente`](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java);
os nomes de componentes, rotas, cabeçalhos e tabelas vêm de lá. Mermaid não tem sintaxe C4 estável no GitHub:
use `flowchart` com `subgraph` para representar containers.

## 1. Visão de containers (flowchart com subgraphs)

```mermaid
flowchart LR
    cliente["Cliente HTTP<br/>repete após timeout"]

    subgraph checkout["Aplicação checkout (Spring Boot)"]
        ctrl["PedidosController<br/>POST /pedidos, GET /disponibilidade"]
        adm["AdmissaoPorPrioridade<br/>capacidade por instância"]
        met["MetricasProtecao"]
        proc["ProcessadorIdempotente"]
    end

    prov["ProvedorPagamento<br/>(porta de saída)"]
    banco[("Banco relacional<br/>pedidos, idempotencia, outbox")]

    cliente -->|"Idempotency-Key + X-Tenant"| ctrl
    ctrl --> adm
    ctrl --> met
    ctrl -->|"autorizar()"| prov
    ctrl --> proc
    proc -->|"1 transação"| banco
```

## 2. Sequência: criar pedido com Idempotency-Key e caminho 503

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant P as PedidosController
    participant A as AdmissaoPorPrioridade
    participant G as ProvedorPagamento
    participant I as ProcessadorIdempotente
    participant B as Banco

    C->>P: POST /pedidos (Idempotency-Key, X-Tenant, centavos)
    P->>A: admitir(CRITICA, deadline 2s)
    alt sem capacidade
        A-->>P: Rejeitada
        P-->>C: 503 ProblemDetail + Retry-After: 1
        Note over C: espera e repete com a mesma chave
    else admitido
        A-->>P: permissão
        P->>G: autorizar()
        G-->>P: ok
        P->>I: processar(tenant, chave, centavos)
        I->>B: INSERT idempotencia, pedidos, outbox (1 transação)
        alt chave já usada com outro payload
            B-->>I: conflito
            I-->>P: ConflitoDeChave
            P-->>C: 409 ProblemDetail
        else nova ou repetida com mesmo payload
            I-->>P: id do pedido
            P-->>C: 201 {"id": "..."}
        end
    end
```

## 3. Máquina de estados: evento da outbox

O checkout não persiste coluna de status no pedido; o ciclo de vida real está no evento da `outbox`
(coluna `publicado`). O diagrama abaixo modela esse ciclo e a decisão de admissão.

```mermaid
stateDiagram-v2
    [*] --> Recebido: POST /pedidos
    Recebido --> Rejeitado: sem capacidade (503)
    Recebido --> Autorizado: ProvedorPagamento.autorizar()
    Autorizado --> Persistido: transação (pedidos + idempotencia + outbox)
    Persistido --> EventoPendente: outbox.publicado = false
    EventoPendente --> EventoPublicado: publicador confirma
    EventoPendente --> EventoPendente: falha ao publicar, tenta novamente
    Rejeitado --> [*]
    EventoPublicado --> [*]
```

Se o domínio ganhar status de pedido/autorização persistido, desenhe cada aresta com o motivo e a checagem
explícita de origem (ver `refinamento-de-historias`, eixo "Máquina de estados").

## 4. Modelo de dados (erDiagram)

```mermaid
erDiagram
    PEDIDOS {
        varchar id PK
        bigint centavos
    }
    IDEMPOTENCIA {
        varchar tenant PK
        varchar chave PK
        bigint centavos
        varchar resultado FK
    }
    OUTBOX {
        bigint seq PK
        varchar id FK
        boolean publicado
    }
    IDEMPOTENCIA }o--|| PEDIDOS : "resultado = id"
    OUTBOX |o--|| PEDIDOS : "id"
```

## Regras rápidas de validade

- Rótulo com `(`, `:` ou `/` vai entre aspas duplas; use `<br/>` para quebra de linha em nó.
- Um diagrama por bloco, pequeno; se passar de ~15 nós, divida por fluxo.
- Nomeie nós com componentes reais (classe, tabela, cabeçalho), nunca "Serviço A".
