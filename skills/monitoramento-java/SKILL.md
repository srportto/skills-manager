---
name: monitoramento-java
description: "Configura os três pilares de observabilidade em Java/Spring Boot — padrão de logs (JSON, MDC, nível, o que logar por camada), métricas Micrometer + Prometheus (RED/USE), tracing distribuído com OpenTelemetry — e a stack ao redor (Grafana, alerting rules, SLO, probes liveness/readiness). Use ao instrumentar serviço, adicionar logs ou métricas, correlacionar requests via MDC, configurar alertas ou investigar incidente. Uso: agents `engenheiro-devops` (variante `k8s`)/`especialista-monitoramento`/`engenheiro-seguranca`/`java-revisor` ou `/monitoramento-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "2.0.0"
  domain: observability
  triggers: monitoramento, observabilidade, Prometheus, Grafana, OpenTelemetry, tracing distribuído, alert, MTTR, Micrometer, RED, USE, adicione logs, melhore os logs, log estruturado, traceId, correlação, MDC, JSON, SLF4J
  role: specialist
  scope: observability
  output-format: code
  related-skills: seguranca-aplicacao-java, revisao-de-codigo-java, arquitetura-limpa-java, devops-cicd
---

# Monitoramento de Aplicações Java

## Quando usar

Guia de observabilidade para aplicações Java/Spring Boot: as **três pillars** (logs estruturados,
métricas, tracing distribuído) e como instrumentá-las no stack deste catálogo (Spring Boot 4,
Micrometer, OpenTelemetry, Prometheus, Grafana). Inclui o **padrão único de logging**: SLF4J como única
API de log, JSON estruturado por padrão, correlação via MDC/`traceId`, critério objetivo de nível e de
"o que logar" por camada.

## Quando NÃO usar

- Checklist completo de revisão de código (logs é só um item): `revisao-de-codigo-java`, que referencia
  esta skill na seção "Logs".
- Em qual camada uma classe deve viver: `arquitetura-limpa-java` (esta skill assume o mesmo modelo
  `domain` / `application` / `infrastructure` só para decidir o que logar em cada uma).
- Auditoria de logging de uma aplicação inteira (não só um trecho): agent `java-revisor`.
- Correlação ponta a ponta entre microsserviços (correlation ID middleware, propagação de `X-Trace-Id`):
  `arquitetura-limpa-java`. Tuning de banco: `banco-de-dados-performance`.

## Entradas

- Serviço/trecho a instrumentar e seus caminhos críticos (SLIs candidatos, métricas de negócio).
- Stack de coleta disponível (Prometheus, log shipper, OTel Collector) e política de amostragem.
- Versão do Spring Boot (confira o nome das propriedades `management.*` e `logging.structured.*`).

## Decisão

| Pergunta | Pilar | Onde ler |
|---|---|---|
| Qual formato, nível e regra de ouro do log? | Logs | [logs-estruturados](references/logs-estruturados.md) |
| Como carregar `traceId`/contexto em cada linha de log? | Logs | [logs-mdc-correlacao](references/logs-mdc-correlacao.md) |
| O que logar em `domain`/`application`/`infrastructure`? | Logs | [logs-por-camada](references/logs-por-camada.md) |
| Qual tipo de métrica, quais labels, como medir saturação? | Métricas | [metricas-micrometer](references/metricas-micrometer.md) |
| Como seguir uma requisição entre serviços? | Tracing | [tracing-opentelemetry](references/tracing-opentelemetry.md) |
| Quais alertas, dashboards e probes? | Alertas | [alertas-dashboards-probes](references/alertas-dashboards-probes.md) |
| SLO, burn rate, runbook, recuperação? | SLO | [slo-saturacao-java](references/slo-saturacao-java.md) |

## Passo a passo (workflow de instrumentação)

1. **Avalie** — SLIs do serviço, caminhos críticos, métricas de negócio (não só técnicas).
2. **Instrumente** — adicione logs, métricas e traces (ver references acima).
3. **Colete** — agregação/storage (Prometheus scrape, log shipper, OTLP); **valide que o dado chega**.
4. **Visualize** — dashboards RED para serviços user-facing, USE para recursos.
5. **Alerte** — threshold + anomalia em caminhos críticos; **valide que não há falso positivo**.

Ponto de partida de configuração: copie de [assets/application-observabilidade.yml](assets/application-observabilidade.yml)
(logging estruturado, actuator, probes, Micrometer/Prometheus e tracing OTLP) e de
[assets/alertas-prometheus.yml](assets/alertas-prometheus.yml) (regras RED, SLO e saturação).

## Saída

`application.yaml` com logging estruturado, actuator e tracing; métricas custom com labels de conjunto
fechado; MDC com `traceId`; regras de alerta com `for` e runbook; probes separadas (liveness/readiness).

## Validação

- `/actuator/prometheus` expõe as métricas esperadas e `rate()` funciona (counter, não gauge).
- Cada linha de log é JSON com `traceId`; nenhum dado sensível (senha, token, PII) aparece.
- O trace atravessa os serviços (`traceparent`) e o mesmo `traceId` aparece nos logs.
- Alerta testado contra falso positivo; probes: banco fora → liveness 200, readiness 200, rota que usa o banco
  503 + `Retry-After` (degradação explícita), grupo `dependencias` 503 para alerta.

## Gotchas

- **Não confunda "campo no JSON" com "placeholder no log".** O que vira campo JSON de verdade é o que
  está no MDC (ver [logs-mdc-correlacao](references/logs-mdc-correlacao.md)) — placeholders `{}` viram
  texto do campo `message`, não campos separados.
- Gauge no lugar de Counter quebra `rate()`; `traceId`/id de pedido como label derruba o Prometheus.
- Liveness com dependência externa transforma queda do banco em reinício de todos os pods.

### MUST DO
- Use logs estruturados (JSON) — texto livre é parseável, mas estruturado é **filtrável**.
- Inclua `traceId` em logs e traces; em métricas, só via exemplars (nunca como label).
- Defina SLI/SLO por operação e alerte por consumo do orçamento de erro (burn rate), com runbook.
- Meça saturação (fila/idade, tarefas ativas, espera de pool, lag) e rejeições.
- Configure alertas em caminhos críticos (latência, taxa de erro, saturação).
- Monitore **métricas de negócio**, não só técnicas (`pedidos_criados_total` > `jvm_memory_used`).
- Use o tipo de métrica correto (counter/gauge/histogram/timer).
- Implemente endpoints de health check (liveness **e** readiness separados).
- Propague `traceparent` entre microsserviços (W3C Trace Context).

### MUST NOT DO
- Logar dados sensíveis (senhas, tokens, PII) — ver [logs-estruturados](references/logs-estruturados.md), "Regras de ouro".
- Alertar em todo erro (alert fatigue) — defina threshold + `for` duration para evitar flapping.
- Usar Gauge onde Counter é o correto (quebra `rate()` no PromQL).
- Pular correlation ID em sistemas distribuídos.
- Escolher taxa de sampling sem considerar volume, custo e necessidade de diagnóstico (prefira tail
  sampling para manter erros e lentidão).
- Usar id, path dinâmico, `traceId` ou mensagem de erro como label de métrica.
- Acoplar liveness a dependências externas, readiness a métricas de carga ou a dependência compartilhada
  por todas as réplicas (esvazia o Service; degrade na aplicação).
- Misturar dashboards RED e USE sem critério — defina por serviço qual faz sentido.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [logs-estruturados](references/logs-estruturados.md) | Adicionar/revisar logs: regras de ouro, JSON estruturado, níveis |
| [logs-mdc-correlacao](references/logs-mdc-correlacao.md) | Propagar `traceId` e contexto via MDC (HTTP, consumers, threads) |
| [logs-por-camada](references/logs-por-camada.md) | Decidir o que logar por camada hexagonal e evitar erros comuns |
| [metricas-micrometer](references/metricas-micrometer.md) | Instrumentar métricas, escolher tipo, cardinalidade e saturação |
| [tracing-opentelemetry](references/tracing-opentelemetry.md) | Tracing distribuído, amostragem, spans e propagação |
| [alertas-dashboards-probes](references/alertas-dashboards-probes.md) | Regras de alerta, dashboards RED/USE e probes liveness/readiness |
| [slo-saturacao-java](references/slo-saturacao-java.md) | SLO, burn rate, saturação, runbook e recuperação |

Assets: [application-observabilidade.yml](assets/application-observabilidade.yml) e
[alertas-prometheus.yml](assets/alertas-prometheus.yml).

## Quem aplica o quê

| Situação | Quem | Skill |
|---|---|---|
| Adicionar métrica Micrometer em código | sessão principal | esta skill |
| Configurar stack Prometheus + Grafana + OTel Collector | sessão principal | esta skill |
| Padronizar formato de log + MDC | sessão principal | esta skill (references `logs-*`) |
| Auditar instrumentação/logging existente de um serviço | agent `java-revisor` (modo `auditoria`) | esta skill |
| Definir alertas de SLO | sessão principal | esta skill |
| Checklist completo de revisão de código (logs é um item entre vários) | sessão principal | `revisao-de-codigo-java` |
| Dúvida sobre em qual camada uma classe/log deve viver | sessão principal | `arquitetura-limpa-java` |
