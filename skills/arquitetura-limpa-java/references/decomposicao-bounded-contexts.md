Leia este arquivo quando a dúvida for "em qual serviço": identificar bounded contexts, critérios de fronteira, padrão de comunicação, resiliência mínima entre serviços e probes de health/readiness.

## Decomposição de monolito em bounded contexts (DDD aplicado)

Quando o problema deixa de ser "em qual camada" e passa a ser **"em qual serviço"**, aplique DDD antes
de partir para hexagonal:

1. **Identificar bounded contexts** — linguagem ubíqua própria por contexto (um `Pedido` em
   `contexto-vendas` não é o mesmo `Pedido` de `contexto-fulfillment`); identifique o subdomínio
   nuclear (vantagem competitiva real, fica na sua equipe) vs. subdomínios de suporte/genéricos;
   documente o context map (Shared Kernel, Customer/Supplier, Anti-Corruption Layer, Conformist).

2. **Critérios para uma nova fronteira de serviço** — antes de virar microsserviço, o candidato deve:
   ser dono **exclusivo** dos seus dados (database-per-service); ter **contrato público** versionado;
   ser **deployado independentemente**; ter **equipe dedicada** capaz de operar 24/7; tolerar
   **consistência eventual** (não vale a pena se exige ACID entre dois domínios).
   > **Regra prática:** comece com **monolito modular** e só extraia um microsserviço quando módulo,
   > release ou equipe precisarem de independência real — microsserviço prematuro é a causa #1 de
   > "distributed monolith".

3. **Communication pattern por fronteira**:

   | Relação | Padrão | Por quê |
   |---|---|---|
   | Query/command com SLA < 100 ms | Síncrono (REST/gRPC) | Coupling temporal curto é aceitável |
   | Operação cross-aggregate, demorado | **Assíncrono** (evento, fila) | Falha de um serviço não derruba o outro |
   | Replicação de dado para leitura | **Event-driven** (Kafka) | Cada lado tem sua cópia, evolui independente |
   | Tradução entre domínios legados | **Anti-Corruption Layer** | Impede vazamento de modelo antigo |

   > Toda comunicação externa atravessa uma porta: o contrato do outro serviço entra como `port/out`,
   > e o ACL é justamente o adapter que traduz o modelo alheio para o seu `domain/model`.

4. **Resiliência mínima por chamada síncrona entre serviços** (fonte: `resiliencia-controle-fluxo-java`):
   deadline da requisição propagado e timeout explícito no cliente (nunca o default infinito); retry só
   para falha transitória de operação idempotente, com backoff exponencial + jitter, dentro do deadline e
   com **uma** camada dona; bulkhead/limite de concorrência por dependência (circuit breaker não limita
   concorrência); circuit breaker quando o volume dá amostra; `Idempotency-Key` em POST sujeito a
   reentrega. Essas proteções moram no **adapter** de saída (`infrastructure`), não no domínio. Contexto de
   rastreamento propagado via W3C Trace Context: ver `monitoramento-java`.

5. **Health & readiness probe** — use os grupos do Actuator: `/actuator/health/liveness` (só o estado do
   processo; falha **reinicia** o pod — nunca inclua banco/broker) e `/actuator/health/readiness` (estado
   desta réplica; falha tira a réplica do balanceador, **não** reinicia; dependência compartilhada por todas as
   réplicas fica fora e a aplicação degrada explicitamente). Semântica,
   configuração dos grupos e exemplo testado: `monitoramento-java` (seção probes); manifests: `devops-cicd`.
   ```yaml
   livenessProbe:
     httpGet: { path: /actuator/health/liveness, port: 8080 }
     periodSeconds: 15
   readinessProbe:
     httpGet: { path: /actuator/health/readiness, port: 8080 }
     periodSeconds: 10
   ```
