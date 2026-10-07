# SLO, saturação, alertas e recuperação

## 1. SLI a partir das métricas da aplicação

Com as métricas de [MetricasProtecao](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/MetricasProtecao.java)
(`app.requisicoes` → `app_requisicoes_seconds_*` no Prometheus):

```promql
# Disponibilidade: sucesso ÷ todas as requisições lógicas (rejeição e erro contam como falha)
sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido",resultado="sucesso"}[5m]))
/
sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido"}[5m]))

# Latência: fração de requisições bem-sucedidas abaixo de 300 ms (SLI de latência por limiar)
sum(rate(app_requisicoes_seconds_bucket{operacao="criar-pedido",resultado="sucesso",le="0.3"}[5m]))
/
sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido",resultado="sucesso"}[5m]))
```

- **Agregue certo:** `rate` por série e **depois** `sum`; percentis entre instâncias via
  `histogram_quantile(0.99, sum by (le) (rate(..._bucket[5m])))` — nunca média de p99 de cada instância.
- **Excluir do denominador** só o que não é responsabilidade do serviço e está documentado (ex.: 4xx de validação).
  429/503 do próprio serviço **ficam** no denominador.
- O bucket `le="0.3"` só existe se o histograma tiver esse limite: configure SLO buckets
  (`management.metrics.distribution.slo.app.requisicoes=300ms,1s`) ou use histograma percentil.

## 2. Orçamento de erro e alertas por burn rate

SLO de 99,9% em 30 dias → orçamento de 0,1% das requisições. *Burn rate* = taxa de erro observada ÷ taxa de erro
permitida (0,001). Burn rate 1 consome o orçamento exatamente em 30 dias; 14,4 o consome em ~2 dias.

| Severidade | Janela longa | Janela curta | Burn rate | Orçamento consumido ao disparar |
|---|---|---|---|---|
| Página (urgente) | 1 h | 5 min | 14,4 | 2% |
| Página | 6 h | 30 min | 6 | 5% |
| Ticket | 3 dias | 6 h | 1 | 10% |

A janela curta faz o alerta **parar** logo após a recuperação; a longa evita disparo por ruído.

```yaml
groups:
  - name: checkout-slo
    rules:
      - record: checkout:erro_ratio:rate5m
        expr: |
          1 - (sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido",resultado="sucesso"}[5m]))
               / sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido"}[5m])))
      - record: checkout:erro_ratio:rate1h
        expr: |
          1 - (sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido",resultado="sucesso"}[1h]))
               / sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido"}[1h])))
      - alert: CheckoutConsumoRapidoDoOrcamento
        expr: checkout:erro_ratio:rate1h > (14.4 * 0.001) and checkout:erro_ratio:rate5m > (14.4 * 0.001)
        labels:
          severity: page
        annotations:
          summary: "Checkout consumindo o orçamento de erro 14x mais rápido que o sustentável"
          runbook_url: "https://runbooks.exemplo.com/checkout/orcamento-de-erro"
```

Tráfego muito baixo torna razões instáveis (1 erro em 10 requisições = 10%): use janelas maiores, mínimo de
requisições na expressão ou SLO por tempo.

## 3. Saturação: o que medir e onde alertar

| Sinal | Métrica | Alerta típico (ajuste ao sistema) |
|---|---|---|
| Fila em memória | itens, bytes, **idade** do mais antigo, capacidade | ocupação > 80% por 10 min ou idade > deadline |
| Trabalho ativo | ativos vs limite (admissão/bulkhead) | ativos = limite por 5 min + rejeições crescentes |
| Rejeições | `resultado="rejeitada"` por operação | taxa > orçamento de rejeição definido |
| Pool de conexões | `hikaricp_connections_pending`, tempo de aquisição p99, timeouts | pending > 0 por 5 min |
| Consumo assíncrono | lag por partição, idade do backlog (SQS `ApproximateAgeOfOldestMessage`) | idade > SLO de processamento |
| Dependências | tentativas por resultado, estado do breaker, uso de fallback | breaker aberto > 5 min; fallback > X% |
| DLQ/DLT | mensagens e taxa de entrada | qualquer crescimento contínuo |
| JVM | heap após GC, pausas, threads de plataforma | heap pós-GC > 85% |

Saturação vira **alerta e load shedding**, não readiness (ver [probes](alertas-dashboards-probes.md#health--readiness-probes)).

## 4. Logs, traces e correlação

- Propague **W3C Trace Context** (`traceparent`/`tracestate`); clientes HTTP e Kafka instrumentados pelo
  Micrometer/OTel fazem isso. Em mensageria, o contexto viaja em headers da mensagem; no consumo, o span do
  processamento é ligado ao produtor.
- `traceId`, ids de negócio e mensagens de erro ficam em **logs estruturados e spans**; nunca em labels.
- Exemplars ligam um ponto do histograma ao trace que o gerou — o caminho da métrica até o caso concreto.

## 5. Runbook mínimo (por alerta)

A degradação citada no runbook é decidida **antes** do incidente, junto com as probes: para cada dependência
compartilhada, o que a rota responde quando ela cai (503 + `Retry-After`/Problem Details, fallback honesto ou
200 sem ela) e o teste que prova isso. A regra e o exemplo testado estão em
[alertas-dashboards-probes](alertas-dashboards-probes.md#health--readiness-probes). Readiness não substitui
essa decisão: com dependência compartilhada nela, o Service fica vazio.

```markdown
## CheckoutConsumoRapidoDoOrcamento
- Impacto: clientes não conseguem finalizar pedidos (erro ou 503).
- Primeiros passos: dashboard Checkout → separar rejeitada × erro; ver saturação (pool, admissão, breaker).
- Se rejeitada domina: carga acima da capacidade → escalar (respeitando orçamento do banco), ativar
  degradação de funcionalidades não críticas; NÃO aumentar limites às cegas.
- Se erro domina: dependência? deploy recente? → rollback/feature flag.
- Dependência compartilhada fora (banco, broker, API): a degradação já está no código — rotas que precisam
  dela respondem 503 + `Retry-After` rápido; as demais seguem 200. Pods continuam ready e vivos (não reinicie
  nem tire todos do Service); acompanhe o grupo `dependencias` e o estado do breaker.
- Recuperação: confirmar drenagem (fila/lag caindo), retries/replay em taxa limitada, burn rate < 1.
- Escalonamento: time de plantão do banco se pool/locks; dono da dependência se breaker aberto.
```

## 6. Recuperação observada

Depois de um incidente, meça: tempo até drenar backlog (lag/idade de volta ao normal), pico de retries no
retorno, taxa de replay da DLQ, aquecimento do cache (acerto subindo sem pico no banco). Um retorno saudável
mostra a taxa de rejeição caindo **antes** da latência explodir de novo — se a latência dispara no retorno,
falta limite de taxa em replay/retry.

## 7. Autoscaling e capacidade downstream

Escalar por CPU não enxerga backlog nem espera por I/O. Para consumidores, escale por idade/lag; para APIs, por
requisições em voo ou latência — sempre com **teto** que respeite o orçamento de conexões e quotas do downstream.
Novas réplicas levam minutos (imagem, JVM, aquecimento): a admissão/load shedding protege o sistema durante a
subida; o replay acumulado é liberado em rampa.
