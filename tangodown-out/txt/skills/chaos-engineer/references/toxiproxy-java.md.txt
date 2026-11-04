# Toxiproxy em Java (Testcontainers)

Leia este arquivo quando precisar injetar falha de rede (latência, timeout, conexão derrubada) entre uma aplicação Java e uma dependência em teste local/CI, com hipótese, baseline, critério de abort e recuperação verificados por código. Complementa [experiment-design](experiment-design.md) (desenho do experimento) e [chaos-tools](chaos-tools.md) (comparação de ferramentas). Para registrar o resultado, use o template [relatorio-experimento](../assets/relatorio-experimento.md).

## Experimento executável de referência

[ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java)
(perfil `integracao`, requer Docker) coloca o Valkey atrás de um Toxiproxy na mesma rede Docker e exercita o
`LimiteDistribuido` (limitador de quotas com coordenador remoto e fallback local). Rodar:

```bash
mvn -f examples/java/pom.xml -Pintegracao verify
```

| Etapa | Como o teste a executa |
|---|---|
| Hipótese | Com o coordenador mais lento que o timeout do cliente (100 ms), cada decisão sai em menos de 300 ms, o serviço passa ao limite **local degradado** (nunca ilimitado) e volta ao coordenador quando a latência some |
| Baseline | `rodada(limite, 20)` antes da falha: as 20 decisões são `PERMITIDO`, todas pelo coordenador |
| Injeção | `proxy.toxics().latency("valkey-lento", ToxicDirection.DOWNSTREAM, 500)` — 500 ms entre aplicação e Valkey; variável única |
| Critério de abort | Qualquer decisão acima de 2 s chama `fail("ABORT ...")`; acima de 300 ms falha a hipótese. O toxic é removido em `finally` |
| Resultado esperado durante a falha | Nenhuma decisão `PERMITIDO`; no máximo 6 `PERMITIDO_DEGRADADO` (burst local) e pelo menos 14 `NEGADO_DEGRADADO`; `falhasCoordenador() >= 20` |
| Recuperação | Após remover o toxic, polling de até 10 s até voltar `PERMITIDO`; depois 10 decisões, todas `PERMITIDO` |
| Resultado | O teste passa só se a degradação for limitada **e** a recuperação ocorrer no prazo. Trocar o fallback para *fail-open* faz o experimento falhar: é isso que o torna uma prova |

## Anatomia: antes/depois

```java
// ANTES — falha sem rollback garantido e sem abort: se uma asserção falhar, o toxic fica ligado
proxy.toxics().latency("valkey-lento", ToxicDirection.DOWNSTREAM, 500);
var durante = rodada(limite, 20);
assertEquals(0, durante.getOrDefault(Decisao.PERMITIDO, 0));
proxy.toxics().get("valkey-lento").remove();      // nunca executa se a linha acima lançar
```

```java
// DEPOIS — baseline medido, uma única falha, rollback em finally e recuperação com prazo
var baseline = rodada(limite, 20);                // estado estável antes de injetar
assertEquals(Map.of(Decisao.PERMITIDO, 20), baseline);
try {
    proxy.toxics().latency("valkey-lento", ToxicDirection.DOWNSTREAM, 500);
    var durante = rodada(limite, 20);             // rodada() aborta se uma decisão passar de 2 s
    assertTrue(durante.getOrDefault(Decisao.PERMITIDO_DEGRADADO, 0) <= 6);
} finally {
    proxy.toxics().get("valkey-lento").remove();  // rollback da falha, sempre
}
long prazo = System.nanoTime() + Duration.ofSeconds(10).toNanos();
while (limite.avaliar("tenant") != Decisao.PERMITIDO) {
    if (System.nanoTime() > prazo) fail("Não recuperou em 10 s");
    Thread.sleep(100);
}
```

## Setup mínimo (Testcontainers + cliente Java)

```java
static final Network REDE = Network.newNetwork();
static final ToxiproxyContainer TOXIPROXY =
        new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.12.0")).withNetwork(REDE);

var controle = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
Proxy proxy = controle.createProxy("valkey", "0.0.0.0:8666", "valkey:6379");  // upstream pelo alias da rede
// A aplicação fala com a porta mapeada do proxy, com timeouts curtos para a falha não prender threads:
var config = DefaultJedisClientConfig.builder().connectionTimeoutMillis(100).socketTimeoutMillis(100).build();
var redis = new JedisPooled(new HostAndPort(TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(8666)), config);
```

## Outros toxics úteis

| Toxic | Simula | Chamada |
|---|---|---|
| `latency` | Dependência lenta | `proxy.toxics().latency(nome, ToxicDirection.DOWNSTREAM, 500)` |
| `timeout` | Conexão pendurada | `proxy.toxics().timeout(nome, ToxicDirection.DOWNSTREAM, 0)` |
| `resetPeer` | Conexão derrubada | `proxy.toxics().resetPeer(nome, ToxicDirection.DOWNSTREAM, 0)` |
| `bandwidth` | Rede lenta | `proxy.toxics().bandwidth(nome, ToxicDirection.DOWNSTREAM, 1024)` |
| desligar o proxy | Dependência fora | `proxy.disable()` e, no `finally`, `proxy.enable()` |

Aplique um toxic por experimento (variável única) e dê nome único para removê-lo no `finally`.
Para dependência HTTP, um servidor Java local que atrasa ou falha sob comando basta — ver
[ChamadaComDeadlineTest](../../../examples/java/fundamentos/src/test/java/br/com/srportto/exemplos/ChamadaComDeadlineTest.java).
