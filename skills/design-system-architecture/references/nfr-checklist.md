# Checklist de requisitos não funcionais

NFR não é lista de tecnologias. Escreva cada requisito como **critério observável**: cenário, carga,
fronteira medida, resultado esperado e como medir. Hipótese sem medição fica rotulada, com dono.

| Área | Perguntas | Evidência |
|---|---|---|
| Performance | Taxa média/pico **e duração**, distribuição de latência por operação, payload típico e máximo? | Perfil de carga; p50/p95/p99 sob carga representativa |
| Capacidade | CPU/memória, concorrência (Lei de Little), pool somado das réplicas, fila por itens/bytes/idade? | Orçamento por instância e agregado; teste de saturação |
| Sobrecarga | Quem reduz a produção? Quando rejeitar (429/503), pausar, persistir ou degradar? | Teste com carga acima da capacidade: limites respeitados, rejeição visível |
| Disponibilidade | SLI (numerador/denominador), SLO, janela, exclusões; por tempo ou por requisição? | Orçamento de erro e alerta por burn rate |
| Latência/deadline | Deadline ponta a ponta e por salto; quem é dono do retry? | Orçamento de tempo documentado; teste de dependência lenta |
| Confiabilidade | Efeito desconhecido, duplicidade, ordem, retenção, DLQ, RTO/RPO? | Teste de duplicata, replay, restore e reconciliação |
| Segurança | Identidade, autorização, tenant, TLS/mTLS, segredos, limites por identidade, abuso de recursos? | Modelo de ameaça; teste de fronteira e de abuso |
| Dados | Consistência por operação, staleness tolerado, atraso de réplica, migração, privacidade? | Contrato de dados; teste transacional e de migração |
| Operação | On-call, runbook, probes, drenagem, rollout/rollback, observabilidade? | Exercício de incidente e recuperação |
| Custo | Orçamento mensal, custo por operação, custo do pico e do retry? | Estimativa com premissas explícitas |
| Manutenção | Equipe, entrega, testes, contratos, compatibilidade e reversibilidade? | Decisão arquitetural (ADR) e plano de migração |

## Como escrever

| Ruim | Bom |
|---|---|
| "O sistema deve ser rápido" | "`POST /pedidos`: p99 < 300 ms com 400 req/s sustentados por 15 min, medido no LB" |
| "Alta disponibilidade" | "99,9% de criações bem-sucedidas em 30 dias; 429/503 do serviço contam como falha" |
| "Usar Kafka para escalar" | "Absorver pico de 15 min a 400/s com lag < 5 min e drenagem < 30 min após o pico" |
| "Resiliente a falhas do provedor" | "Provedor lento (> 2 s) não ocupa mais de 10 conexões; pedidos ficam `PENDENTE` e são reconciliados" |

Cálculos de capacidade e SLO: [capacidade e SLOs](capacidade-slos.md). Refinamento de histórias com esses
critérios: `refinamento-de-historias`.
