# Padrões arquiteturais e evolução

Decida por requisito, capacidade, equipe e custo. Cite sempre a alternativa mais simples, a falha esperada, a
proteção e o **gatilho mensurável** que justificaria evoluir.

## Estilos

| Opção | Ganho | Custo | Gatilho legítimo |
|---|---|---|---|
| Monólito modular | Transações locais, um deploy, depuração simples | Deploy compartilhado; exige disciplina de módulos | Padrão inicial quando atende requisitos |
| Microsserviços | Escala, release e propriedade independentes | Rede, dados distribuídos, contratos, observabilidade e on-call | Fronteira de domínio estável **e** necessidade operacional comprovada (times bloqueando uns aos outros, escala muito diferente por módulo) |
| Serverless (FaaS) | Elasticidade, pagar por uso, sem host | Cold start, duração máxima, quotas de concorrência, lock-in, conexões ao banco por instância | Carga em rajadas/eventos, tarefas curtas, custo medido menor |
| Event-driven | Desacoplamento temporal, replay, múltiplos consumidores | Duplicatas, ordem parcial, lag, evolução de schema | Consumidores independentes que toleram estado pendente |
| CQRS | Leitura especializada e escala seletiva | Projeções, reconciliação, consistência eventual | Modelo de leitura diverge de verdade do de escrita |
| Saga/outbox | Recuperação de efeitos entre fronteiras | Estados intermediários, compensação, duplicatas | Transação local não cobre a operação |

Hexagonal/camadas organizam o **interior** de uma aplicação; não são alternativa a microsserviços. DDD estratégico
define contextos e o mapa entre eles; DDD tático, os modelos internos (`arquitetura-limpa-java`).

### Monólito modular na prática

Módulos por contexto de negócio, cada um com API interna explícita (porta), dados próprios (schemas ou tabelas
com dono) e proibição de acesso direto às tabelas de outro módulo — verificada por teste de arquitetura
(ArchUnit). Comunicação síncrona por interface ou assíncrona por eventos internos + outbox. Extrair um módulo
para serviço vira mudança de **implantação**, não de modelo.

### Serverless com Java

Cold start da JVM: use SnapStart/CRaC ou imagem nativa quando a latência de inicialização importar; meça.
Concorrência da função × conexões ao banco: 1.000 execuções simultâneas com 1 conexão cada esgotam o banco —
use proxy de conexões (ex.: RDS Proxy) ou limite a concorrência reservada da função. Duração máxima e
reprocessamento por evento exigem idempotência.

## Estado

- **Stateless** facilita escala horizontal e substituição, mas sessões, caches locais, uploads temporários e
  conexões longas (WebSocket) reintroduzem estado.
- **Stateful** exige afinidade (sticky) ou replicação, dono do estado, recuperação após queda e plano de
  rebalanceamento. Prefira empurrar estado para armazenamento gerenciado e manter réplicas descartáveis.

## Escala

| Tipo | Quando | Limites |
|---|---|---|
| Vertical | Simples, sem mudança de código | Teto do hardware, downtime para trocar, custo não linear |
| Horizontal | Carga paralelizável, réplicas sem estado | Coordenação, recurso compartilhado (banco) vira gargalo; somar conexões |
| Particionamento | Dados/carga excedem um nó | Chave de partição, hot keys, consultas cruzadas, resharding |

## Isolamento por tenant

| Modelo | Isolamento | Custo | Uso típico |
|---|---|---|---|
| Pool (tudo compartilhado) | Lógico (coluna `tenant_id`, quotas por tenant) | Menor | Muitos tenants pequenos |
| Bridge (parcial) | Recursos críticos dedicados (schema, fila, pool) | Médio | Tenants grandes junto a pequenos |
| Silo (dedicado) | Físico (conta/cluster/banco por tenant) | Maior | Compliance, contrato, tenant ruidoso extremo |

Em pool, um tenant ruidoso esgota recursos dos demais sem **quota por tenant** e **bulkhead** por classe de
cliente. Autorização sempre filtra por tenant no servidor; nunca confie em tenant enviado pelo cliente.

## Redundância e SPOF

Redundância só ajuda se as falhas forem **independentes**. Duas réplicas na mesma zona, com a mesma credencial
expirada ou o mesmo deploy defeituoso, caem juntas. Procure SPOF lógico: banco primário, DNS, provedor de
identidade, certificado, região, pipeline, um único time que sabe operar. Para cada um: impacto, detecção,
mitigação (réplica, failover, cache de tokens, rollout progressivo) e tempo de recuperação medido.

## Gatilhos de evolução (exemplos mensuráveis)

| Sinal | Limiar ilustrativo | Evolução candidata |
|---|---|---|
| CPU/latência do primário de banco sob pico | > 70% CPU por 1 h com p99 de escrita fora do SLO | Réplicas de leitura, cache, depois particionamento |
| Fila de deploy entre times | > 2 times bloqueados por release comum toda semana | Extrair módulo com fronteira estável |
| Escala desigual | Um módulo exige 10× réplicas dos demais | Separar implantação desse módulo |
| Lag de consumo | Idade do backlog > SLO por 3 dias | Mais partições/consumidores, processamento mais barato |
| Custo | Custo por transação cresce > 20% por trimestre | Rever modelo de execução (reservas, serverless, batch) |

Limiares são ilustrativos: derive os seus do SLO e do custo do sistema real. Evite microsserviço por entidade e
reatividade por moda.
