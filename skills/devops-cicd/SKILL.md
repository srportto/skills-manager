---

name: devops-cicd
description: "Cadeia de deploy de app Java — pipeline CI/CD (GitHub Actions, GitLab CI, Jenkins), Dockerfile multi-stage e manifests Kubernetes (Deployment, Service, ConfigMap, probes, graceful shutdown). Foco no caminho do código ao app rodando, não em provisionamento de cluster/Terraform. Uso: agent `engenheiro-devops` (variantes `pipeline`/`docker`/`k8s`/`all`) ou `/devops-cicd`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.1.0"
  domain: devops
  triggers: GitHub Actions, pipeline CI, Dockerfile, Kubernetes deployment, k8s manifest, rolling update, graceful shutdown, Maven
  role: devops
  scope: application-deploy
  output-format: code
  related-skills: monitoramento-java, cloud-architect, seguranca-aplicacao-java
---

# DevOps & CI/CD (Java/Maven, Docker, Kubernetes)

Guia de DevOps focado no **caminho do código até a aplicação rodando em produção** em stack
Java/Maven, limitado a **CI/CD + containerização + deployment da aplicação** — não cobre Terraform
de cluster inteiro, rede, IAM de provedor cloud, ou administração de cluster.

## Quando usar / Quando NÃO usar

Use para criar ou ajustar pipeline CI, Dockerfile e manifests Kubernetes de uma app Java.

**Quando NÃO usar:** código de aplicação → `java-construtor`. Auditoria completa de segurança →
`seguranca-aplicacao-java` + agent `engenheiro-seguranca`. Tuning de banco →
`banco-de-dados-performance`. Observabilidade pós-deploy → `monitoramento-java`.

## Entradas

Repositório e artefatos existentes (`.github/workflows/`, `Dockerfile`, `k8s/`), versão do JDK/Boot, porta da
aplicação, dependências externas e como a aplicação degrada sem elas (dependência compartilhada fica fora da
readiness), tempo de shutdown e limites de recursos.

## Decisão — variante e reference

| Variante do agent | O que o pedido cobre | Leia | Asset de partida |
|---|---|---|---|
| `pipeline` | CI/CD, quality gates, versionamento, estratégia de deploy | [pipeline-ci](references/pipeline-ci.md) | `assets/ci.yml` |
| `docker` | Dockerfile multi-stage, usuário não-root, HEALTHCHECK | [dockerfile-jvm](references/dockerfile-jvm.md) | `assets/Dockerfile`, `assets/.dockerignore` |
| `k8s` | Deployment, Service, ConfigMap, Ingress | [kubernetes-manifests](references/kubernetes-manifests.md) e [probes-graceful-shutdown](references/probes-graceful-shutdown.md) | `assets/k8s-deployment.yaml`, `assets/k8s-service.yaml` |
| `all` | Os três, nesta ordem | todas | todos |

## Passo a passo

1. **Confirme o que já existe** — verifique `.github/workflows/`, `Dockerfile`, `k8s/`,
   `docker-compose.yml` (dependência AWS local no compose: serviço `floci/floci:2.2.0` na porta 4566). Ajuste em vez
   de recriar.
2. **Defina os estágios necessários** — build → test → package → (push) → (deploy).
3. **Escreva o YAML** com quality gates apropriados ao projeto, partindo dos assets (copie e ajuste nomes, imagem e recursos).
4. **Valide** — `docker build`, `kubectl apply --dry-run=client` (se o cluster estiver acessível),
   `mvn clean verify` localmente.
5. **Reporte** o que foi criado/alterado e quais gates foram configurados.

## Saída

Arquivos criados/alterados (pipeline, Dockerfile, manifests), gates configurados, comandos de validação com
resultado e pendências (Secrets, Ingress, credenciais, valores assumidos).

## Validação

```bash
mvn -B verify                                    # testes vermelhos derrubam o build
docker build -t minha-app:test . && docker run --rm -p 8080:8080 minha-app:test
curl http://localhost:8080/actuator/health/readiness
kubectl apply --dry-run=client -f k8s/
```

O que não puder rodar (sem Docker, sem cluster) fica **pendente** na entrega, nunca verde.

## Gotchas

- `-DskipTests` no `RUN mvn package` do Dockerfile só vale porque o CI já rodou os testes antes; nunca como aprovação.
- A variante `-jre` (Debian/Ubuntu) não traz `wget`/`curl`: o HEALTHCHECK quebra só em runtime. Use `-jre-alpine`.
- Em Kubernetes o HEALTHCHECK do Docker é ignorado; valem as probes.
- Liveness nunca depende de banco/broker/API externa; `/disponibilidade` do esqueleto é smoke test, não probe.
- `terminationGracePeriodSeconds` precisa ser maior que `preStop` + `timeout-per-shutdown-phase`.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [pipeline-ci](references/pipeline-ci.md) | Montar/ajustar pipeline GitHub Actions, gates, cache, versionamento, estratégias de deploy |
| [dockerfile-jvm](references/dockerfile-jvm.md) | Escrever/revisar Dockerfile multi-stage, `.dockerignore`, flags de memória |
| [kubernetes-manifests](references/kubernetes-manifests.md) | Escrever/revisar Deployment, Service, ConfigMap, Ingress |
| [probes-graceful-shutdown](references/probes-graceful-shutdown.md) | Memória da JVM, orçamento de shutdown, semântica das probes |

Assets prontos: `assets/ci.yml`, `assets/Dockerfile`, `assets/.dockerignore`, `assets/k8s-deployment.yaml`,
`assets/k8s-service.yaml`. O esqueleto de `criar-aplicacao-java` carrega uma cópia do Dockerfile.

## Constraints

### MUST DO
- Use infrastructure as code (nunca mudanças manuais em produção).
- Implemente health checks e readiness probes em **toda** aplicação.
- Armazene segredos em secret manager (não em env files ou configmaps).
- Habilite container scanning no CI (Trivy, Snyk).
- Documente procedimentos de rollback.
- Configure `terminationGracePeriodSeconds` + `server.shutdown: graceful` juntos.
- Configure **liveness e readiness probes** separados.
- Limite de memória do container sempre maior que o heap configurado via `MaxRAMPercentage`.

### MUST NOT DO
- Faça deploy em produção sem aprovação explícita.
- Armazene segredos em código ou variáveis de CI/CD.
- Pule testes em pipeline de CI (`-DskipTests`).
- Ignore limites de recursos em containers.
- Use tag `latest` em produção.
- Faça deploy na sexta sem monitoramento no fim de semana.
- **Deployment sem probe de saúde** — achado crítico.

## Quem aplica o quê

| Situação | Quem | Skill/agent |
|---|---|---|
| Montar/ajustar pipeline CI/CD | session principal ou `engenheiro-devops` | esta skill |
| Escrever Dockerfile | session principal ou `engenheiro-devops` (variante `docker`) | esta skill |
| Escrever manifest Kubernetes | session principal ou `engenheiro-devops` (variante `k8s`) | esta skill |
| Configurar observabilidade pós-deploy | session principal | `monitoramento-java` |
| Validar trabalho DevOps antes de merge | `java-revisor` (modo `auditoria`) | `revisao-de-codigo-java` |
