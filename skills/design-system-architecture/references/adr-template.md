# ADR — registro de decisão arquitetural

Preencha apenas com informação real; dado não medido entra como **hipótese** explícita, com dono e forma de
validar. Uma ADR por decisão relevante (tecnologia, topologia, consistência, proteção ou contrato).

## Template

```markdown
# ADR-{número}: {Título no imperativo — ex.: "Persistir pedidos e outbox no PostgreSQL"}

- **Status:** Proposta | Aceita | Substituída por ADR-XXX | Descontinuada
- **Data:** AAAA-MM-DD
- **Responsáveis:** {quem decide / quem opera}

## Contexto
- Operação(ões) afetada(s) e efeito de negócio.
- Requisitos funcionais e não funcionais pertinentes (SLI/SLO, janela, denominador).
- Carga: taxa média e pico (com duração), tamanho de item, leitura/escrita, retenção e crescimento.
- Restrições: equipe, prazo, custo, compliance, dependências existentes.
- Hipóteses ainda não medidas: {hipótese} — validar por {medição/teste} até {marco}.

## Decisão
O que será feito, em qual escopo (por instância, serviço, tenant, região) e o que fica explicitamente fora.

## Alternativas consideradas
| Alternativa | Ganho | Custo / risco | Por que foi descartada |
|---|---|---|---|
| {Solução mais simples} | | | |
| {Alternativa 2} | | | |

## Consequências
- **Ganhos:**
- **Perdas aceitas:** (ex.: consistência eventual na leitura de catálogo por até 30 s)
- **Riscos e dependências novas:**
- **Complexidade operacional:** on-call, runbook, migração, custo mensal estimado e premissas.

## Proteção e falhas
| Fluxo | Falha | Limite (unidade, escopo, motivo) | Rejeição / degradação | Retry e idempotência | Recuperação |
|---|---|---|---|---|---|

## Observabilidade
Métricas (com unidade), SLI/SLO, alertas por consumo de orçamento de erro, dashboard e runbook.

## Evidência
| Afirmação | Teste/medição | Esperado | Observado | Limitações |
|---|---|---|---|---|

## Gatilhos de revisão
Indicador e limiar mensuráveis que reabrem a decisão (ex.: "p99 de escrita > 200 ms por 3 dias com
CPU do primário > 70%" ou "custo mensal > R$ X").

## Migração e rollback
Compatibilidade (expand/contract), convivência de versões, como reverter sem perder efeitos.

## Referências
Links para documentação oficial, discussões e ADRs relacionadas.
```

## Exemplo resumido

```markdown
# ADR-007: Persistir pedidos e outbox no PostgreSQL

- **Status:** Aceita — **Data:** 2026-10-06 — **Responsáveis:** time Checkout

## Contexto
Checkout cria pedido e precisa publicar `PedidoCriado` sem perder evento nem duplicar efeito.
Carga: 50 pedidos/s em média, pico de 400/s por 15 min (Black Friday 2025), item ~2 KiB, retenção de
pedidos 5 anos. SLO: 99,9% das criações bem-sucedidas em 30 dias (denominador: requisições válidas).
Hipótese: o primário suporta 400 escritas/s com p99 < 50 ms — validar com ensaio de carga antes do evento.

## Decisão
Pedido, registro de idempotência e evento de outbox na mesma transação PostgreSQL; relay publica no Kafka
com taxa limitada. Fora do escopo: leitura de catálogo.

## Alternativas consideradas
| Alternativa | Ganho | Custo / risco | Por que foi descartada |
|---|---|---|---|
| Publicar no Kafka após o commit, sem outbox | Menos componentes | Perde evento se o processo cair entre commit e publicação | Viola "não perder evento" |
| Documento (MongoDB) com transação multi-documento | Esquema flexível | Transações têm custo e limites próprios; consultas relacionais de conciliação ficam mais caras | Consultas deste caso são relacionais; ganho não compensa |
| Transação distribuída banco+broker (XA) | Atomicidade aparente | Operação complexa; brokers usuais não participam | Não suportado pela stack |

## Consequências
- **Ganhos:** atomicidade local entre efeito e intenção de publicar.
- **Perdas aceitas:** publicação at-least-once; consumidores deduplicam por `eventId`.
- **Riscos:** tabela de outbox cresce — limpeza com retenção de 7 dias; relay vira dependência operacional.

## Proteção e falhas
| Fluxo | Falha | Limite | Rejeição / degradação | Retry e idempotência | Recuperação |
|---|---|---|---|---|---|
| POST /pedidos | Banco lento | pool 20 conexões/instância × 6 réplicas = 120 ≤ orçamento de 150; espera de pool 200 ms | 503 + métrica de rejeição | Chave idempotente por tenant | Clientes repetem com backoff exponencial + jitter |
| Relay | Broker indisponível | lote 100, 200 eventos/s | Eventos permanecem na outbox | Reenvio at-least-once | Drenagem limitada após retorno |

## Observabilidade
`checkout_pedidos_total{resultado}`, `outbox_pendentes`, `outbox_idade_segundos`; alerta de burn rate 2%/1 h.

## Evidência
| Afirmação | Teste/medição | Esperado | Observado | Limitações |
|---|---|---|---|---|
| Duplicata concorrente não cria dois pedidos | `ProcessadorIdempotenteTest` | 1 pedido | 1 pedido | H2 local; repetir em PostgreSQL (perfil integracao) |

## Gatilhos de revisão
`outbox_idade_segundos` p99 > 60 s por 1 h ou escrita > 70% da capacidade medida.

## Migração e rollback
Tabela nova (expand); relay ativado por flag; rollback desliga relay sem apagar a outbox.
```

## Convenção de arquivos

ADRs ficam em `docs/adr/NNNN-titulo-em-kebab-case.md`, numeração crescente e nunca reutilizada. Uma ADR
substituída continua no repositório com status atualizado e link para a substituta.

## Verificação rápida

| Seção | Pergunta |
|---|---|
| Contexto | Os números têm unidade, fonte ou estão marcados como hipótese? |
| Alternativas | A solução mais simples foi avaliada? |
| Consequências | A perda aceita está escrita? |
| Proteção | Todo limite tem unidade, escopo e motivo? Todo excesso tem destino? |
| Evidência | Separa esperado de observado? |
| Gatilhos | Existe um limiar mensurável para rever a decisão? |
