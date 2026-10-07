# Probes, graceful shutdown e memória da JVM

Leia este arquivo quando precisar fechar o ciclo de vida do pod: limites de memória da JVM, ordem de encerramento, orçamento de tempo e semântica das probes (variante `k8s`).

## Recursos e memória JVM

- **Limite de memória do container** deve considerar **heap + metaspace + overhead da JVM**, não só
  o heap.
- Use `-XX:MaxRAMPercentage=75` (via `JAVA_TOOL_OPTIONS` ou `JAVA_OPTS`) para que a JVM dimensione o
  heap como fração do limite do container — evita OOMKill por heap sub/superdimensionado.
- Regra prática: `requests.memory` = `limits.memory` (classe QoS Guaranteed) para workloads
  previsíveis; ajuste conforme a criticidade.

## Graceful shutdown

- Sequência: o pod entra em *Terminating* → `preStop` (alguns segundos para o endpoint sair do Service/LB) →
  SIGTERM → Spring marca readiness como `REFUSING_TRAFFIC`, para de aceitar requisições e espera as em andamento
  → consumidores param de buscar mensagens e **não confirmam** trabalho não concluído.
- Orçamento: `preStop` + `timeout-per-shutdown-phase` < `terminationGracePeriodSeconds` (ex.: 5 s + 20 s < 30 s).
  O deregistration delay do load balancer externo também precisa caber.
- Spring Boot: `server.shutdown: graceful` (padrão nas versões recentes; deixe explícito) +
  `spring.lifecycle.timeout-per-shutdown-phase`.
- Rolling update com `maxUnavailable: 0` + readiness correta evita perder capacidade durante o deploy; um
  `PodDisruptionBudget` protege contra drenagem de nós derrubando réplicas demais.

```yaml
# application.yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 25s
```

## Probes — por que sempre configurar

- `readinessProbe` — diz ao Service se **esta réplica** pode receber tráfego; sem ele, tráfego vai para
  réplicas ainda subindo ou em shutdown. Dependência **compartilhada** por todas as réplicas (o mesmo banco)
  fica fora: na queda dela, todas sairiam do Service ao mesmo tempo. A aplicação degrada explicitamente
  (503 + `Retry-After` nas rotas que precisam dela), conforme `monitoramento-java` (seção probes).
- `livenessProbe` — diz ao kubelet se o processo travou; reinicia se falhar. **Nunca** inclua banco,
  broker ou API externa: uma queda da dependência reiniciaria todos os pods ao mesmo tempo.
- `startupProbe` — cobre a subida da JVM sem precisar de `initialDelaySeconds` grande na liveness.
- Semântica única dos grupos de health: `monitoramento-java` (seção probes). `/disponibilidade` do esqueleto
  continua como smoke test funcional, não como probe.

## Validação

```bash
kubectl apply --dry-run=client -f deployment.yaml
kubectl apply -f deployment.yaml
kubectl rollout status deployment/minha-app
```

## Exemplo antes/depois: grupos de health no Spring

```yaml
# ANTES — liveness inclui o banco: indisponibilidade do banco reinicia todas as réplicas
management:
  endpoint:
    health:
      group:
        liveness:
          include: livenessState, db
```

```yaml
# DEPOIS — liveness só do processo; banco compartilhado fora das probes (grupo de diagnóstico + alerta)
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState
        dependencias:
          include: db
```

## Provas executáveis

- [SaudeAplicacaoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/SaudeAplicacaoTest.java)
  — banco fora: liveness e readiness seguem 200 (sem reinício em massa nem Service vazio), grupo
  `dependencias` 503 e rota que usa o banco 503 + `Retry-After`.
- [EncerramentoControlado](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/EncerramentoControlado.java)
  — encerramento que espera o trabalho em voo dentro de um prazo.
- Experimento de falha que valida a recuperação: [toxiproxy-java](../../chaos-engineer/references/toxiproxy-java.md).
