# Desenho de experimentos de chaos (Java)

Um experimento é um **teste de hipótese** sobre o comportamento sob falha: estado estável medido, falha
injetada com escopo controlado, observação, critério de abort, remoção da falha e verificação da recuperação.
Sem baseline e sem abort, é só quebrar coisas.

## Template

```yaml
nome: "Coordenador de quotas lento"
hipotese: >
  Se o Valkey de quotas ficar 500 ms mais lento que o normal, cada decisão de admissão continua saindo em
  < 300 ms, o serviço usa o limite local degradado (nunca ilimitado) e volta ao coordenador em < 10 s
  após a falha ser removida.
estado_estavel:                     # medido ANTES da injeção, na mesma janela de tempo
  - metrica: "taxa de sucesso de criar-pedido"
    limiar: ">= 99,9%"
    consulta: 'sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido",resultado="sucesso"}[5m])) / sum(rate(app_requisicoes_seconds_count{operacao="criar-pedido"}[5m]))'
  - metrica: "p99 de criar-pedido"
    limiar: "< 300 ms"
raio_de_impacto:
  ambiente: "staging"               # produção só com aprovação explícita e progressão
  alvo: "1 réplica do checkout"
  duracao_maxima: "5 min"
injecao:
  ferramenta: "Toxiproxy"
  tipo: "latência"
  parametros: { latencia_ms: 500, direcao: downstream }
abort:                              # qualquer um encerra o experimento e remove a falha
  - "taxa de sucesso < 99%"
  - "p99 > 2 s"
  - "kill switch manual"
  - "duração máxima atingida"
recuperacao:
  - "decisões voltam ao coordenador em < 10 s"
  - "sem pico de retries (tentativas/s < 2× baseline)"
responsaveis: { executor: "...", observador: "...", aprovador: "..." }
```

## Hipótese bem formulada

"**Dado** o estado estável (números), **quando** a falha X ocorre no escopo Y, **então** o comportamento Z
acontece, **medido por** métricas M, e o sistema **recupera** em T." Hipóteses úteis para este catálogo:

| Falha | Comportamento esperado | Proteção exercitada |
|---|---|---|
| Consumidor lento (processamento 10× mais lento) | Lag cresce no broker, memória do consumidor estável, poll mantido, nada perdido | Pausa por partição, trabalho em voo limitado |
| Dependência com latência acima do timeout | Requisições terminam no deadline, threads/conexões não acumulam, breaker abre | Deadline, bulkhead, circuit breaker |
| Cache indisponível | Banco recebe no máximo o orçamento de recomputação; excedente rejeitado/degradado | Limite de recomputação, fallback limitado |
| Carga sustentada acima da capacidade | Rejeição 503 rápida e medida, filas limitadas, SLO dos que entram preservado | Admissão / load shedding |
| Banco indisponível | Liveness continua UP (sem reinício em massa), readiness DOWN, recuperação sem tempestade | Semântica de probes, backoff com jitter |
| Retorno após falha | Replay/retries em taxa limitada; latência não explode de novo | Replay controlado, orçamento de retry |

## Controle de falha em Java (Testcontainers + Toxiproxy)

Para experimentos locais/CI, a falha é injetada por código, com remoção garantida em `finally`:

```java
// Valkey atrás do Toxiproxy na mesma rede Docker; a aplicação fala com a porta do proxy.
var controle = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
Proxy proxy = controle.createProxy("valkey", "0.0.0.0:8666", "valkey:6379");

var baseline = rodada(limite, 20);                         // estado estável medido
try {
    proxy.toxics().latency("valkey-lento", ToxicDirection.DOWNSTREAM, 500);   // injeção
    var durante = rodada(limite, 20);                      // rodada() aborta se uma decisão passar de 2 s
    // ... asserções da hipótese
} finally {
    proxy.toxics().get("valkey-lento").remove();           // rollback da falha, sempre
}
// ... verificação da recuperação com prazo
```

Experimento completo e executável (hipótese, baseline, injeção, abort, rollback e recuperação):
[ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java)
(perfil `integracao`). O mesmo experimento **falha** se o fallback do limitador for trocado por *fail-open* —
é isso que o torna uma prova, não uma demonstração.

Outras falhas do Toxiproxy úteis: `timeout` (conexão pendurada), `resetPeer` (conexão derrubada),
`bandwidth` (rede lenta), `slicer` (pacotes fragmentados). Para dependência HTTP, um servidor Java local que
atrasa ou falha sob comando (`com.sun.net.httpserver.HttpServer`) é suficiente — ver
[ChamadaComDeadlineTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ChamadaComDeadlineTest.java).

## Controle do raio de impacto

```java
// Regras de segurança aplicadas antes de iniciar — qualquer violação impede o experimento.
enum Ambiente { DEV, STAGING, PRODUCAO }

record RaioDeImpacto(Ambiente ambiente, double percentualTrafego, Duration duracaoMaxima,
                     boolean rollbackAutomatico, String aprovador) {
    RaioDeImpacto {
        if (percentualTrafego <= 0 || percentualTrafego > 100) throw new IllegalArgumentException("Percentual inválido");
        if (duracaoMaxima.compareTo(Duration.ofMinutes(10)) > 0 && aprovador == null) {
            throw new IllegalArgumentException("Mais de 10 min exige aprovação explícita");
        }
        if (ambiente == Ambiente.PRODUCAO && (aprovador == null || !rollbackAutomatico)) {
            throw new IllegalArgumentException("Produção exige aprovador e rollback automático");
        }
        if (ambiente == Ambiente.PRODUCAO && percentualTrafego > 10) {
            throw new IllegalArgumentException("Produção acima de 10% do tráfego só após rodadas menores bem-sucedidas");
        }
    }
}

// Progressão: dev (100%) → staging (100%) → produção canário (1%) → ampliação gradual.
```

## Guarda de abort

```java
// Executa a injeção enquanto um monitor verifica os gatilhos; o primeiro gatilho encerra e remove a falha.
static Resultado executarComGuarda(Runnable injetar, Runnable remover, Supplier<Optional<String>> gatilhoDeAbort,
                                   Duration duracaoMaxima, Duration intervalo) throws InterruptedException {
    long fim = System.nanoTime() + duracaoMaxima.toNanos();
    injetar.run();
    try {
        while (System.nanoTime() < fim) {
            Optional<String> motivo = gatilhoDeAbort.get();     // consulta métricas reais (SLI, p99, kill switch)
            if (motivo.isPresent()) return Resultado.abortado(motivo.get());
            Thread.sleep(intervalo);
        }
        return Resultado.concluido();
    } finally {
        remover.run();                                         // rollback mesmo em exceção/interrupção
    }
}
```

O gatilho de abort lê as **mesmas métricas do SLO** (`monitoramento-java`), não uma métrica criada só para o
experimento. Antes de iniciar: confirme o estado estável; se o sistema já está fora do normal, **não comece**.

## Relatório

| Campo | Conteúdo |
|---|---|
| Hipótese | Texto original |
| Estado estável | Valores medidos (com janela e consulta) |
| Injeção | Ferramenta, parâmetros, horário início/fim, escopo |
| Observado | Métricas durante a falha, comparadas aos limiares |
| Abort | Disparou? Qual gatilho, em quanto tempo |
| Recuperação | Tempo até o estado estável; pico de retries/replay |
| Resultado | Hipótese confirmada / refutada |
| Ações | Correções com dono e prazo; próximo experimento |

## Referência rápida

| Fase | Ações-chave | Duração típica |
|---|---|---|
| Desenho | Hipótese, métricas, raio de impacto, abort | 1 h |
| Revisão | Revisão com o time, aprovação | 30 min |
| Preparação | Dashboards, kill switch, rollback testado | 1 h |
| Execução | Injeção monitorada | 5–10 min |
| Rollback | Remover falha, confirmar estado estável | < 30 s para remover a falha |
| Aprendizado | Relatório e ações | 2 h |
