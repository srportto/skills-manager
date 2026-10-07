---

name: cloud-architect
description: "Desenho e auditoria de topologia de nuvem (AWS/Azure/GCP) — VPC, subnets, IAM least-privilege, FinOps, disaster recovery (RTO/RPO), landing zone, Well-Architected Framework. Use para topologia cloud, migração, otimização de custo ou DR. Uso: agent `cloud-architect` ou `/cloud-architect`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.1.0"
  domain: infrastructure
  triggers: AWS, Azure, GCP, Google Cloud, cloud migration, cloud architecture, multi-cloud, cloud cost, Well-Architected, landing zone, cloud security, disaster recovery, cloud native, serverless architecture
  role: architect
  scope: infrastructure
  output-format: architecture
  related-skills: devops-cicd, design-system-architecture, seguranca-aplicacao-java, monitoramento-java
---

# Cloud Architect

Referência para desenhar topologias de nuvem (AWS, Azure, GCP), planejar migrações,
otimizar custo (FinOps) e estruturar disaster recovery (RTO/RPO). Cobre seleção de
serviços gerenciados, rede (VPC, peering, subnets), IAM com least-privilege, e o
Well-Architected Framework.

## Quando usar

- Desenhar topologia de nuvem para workload novo (VPC, subnets, IAM, networking).
- Planejar migração on-premises → cloud aplicando o framework 6Rs.
- Implementar disaster recovery com RTO/RPO definidos.
- Otimizar custo (right-sizing, reserved capacity, spot, FinOps).
- Aplicar Well-Architected Framework em arquitetura existente.
- Configurar landing zone multi-conta.

## Quando NÃO usar

- Para design de **sistemas** (escolha entre monolito e microsserviços, ADRs,
  topologia de aplicação), use `design-system-architecture`.
- Para deploy de uma aplicação Java específica (Dockerfile, manifest K8s, pipeline
  CI), use `devops-cicd` (via `engenheiro-devops`).
- Para segurança de aplicação (OWASP, JWT, headers), use `seguranca-aplicacao-java`.
- Para observabilidade de aplicação (Prometheus, OTel, Grafana), use
  `monitoramento-java`.

## Entradas

- Workload e requisitos: SLO/disponibilidade-alvo, RTO/RPO, volume e picos, dados sensíveis.
- Restrições: compliance (LGPD, PCI, SOC2), provedor já adotado, orçamento, equipe disponível.
- Estado atual (quando migração ou auditoria): inventário, topologia, contas/projetos existentes.

## Decisão

| Pergunta | Se sim | Onde aprofundar |
|---|---|---|
| Provedor já definido? | Seguir o guia do provedor | `references/aws.md`, `references/azure.md`, `references/gcp.md` |
| Exige mais de um provedor? | Avaliar custo de portabilidade antes de abstrair | `references/multi-cloud.md` |
| Foco é reduzir gasto? | Right-sizing, reserved/spot, tags | `references/cost.md` |
| Precisa de IAM, VPC ou auto-scaling prontos? | Partir dos exemplos | `references/padroes-cloud.md` |
| RTO/RPO exige multi-região? | Só se o SLO justificar, com custo explícito | seção Disaster Recovery do guia do provedor |

## Passo a passo

1. **Discovery** — levantar estado atual, requisitos, restrições, compliance.
2. **Design** — selecionar serviços, topologia, arquitetura de dados.
3. **Security** — zero-trust, identity federation, encryption (at rest e in transit).
4. **Cost Model** — right-sizing, reserved/spot, auto-scaling, cost allocation tags.
5. **Migration** — framework 6Rs, waves, validar conectividade antes do cutover.
6. **Operate** — monitoramento, automação, otimização contínua.

Checklist copiável:

- [ ] Requisitos, SLO e compliance levantados
- [ ] Topologia sem single point of failure
- [ ] IAM least-privilege e encryption at rest/in transit
- [ ] Modelo de custo com tags de alocação
- [ ] Plano de migração em waves (se aplicável) e plano de DR com RTO/RPO
- [ ] Entrega montada com `assets/topologia-template.md`

## Saída

Toda entrega deve conter:

1. Diagrama de arquitetura com serviços e fluxo de dados.
2. Justificativa de seleção de serviços (compute, storage, database, networking).
3. Arquitetura de segurança (IAM, segmentação de rede, encryption).
4. Estimativa de custo e estratégia de otimização.
5. Plano de deploy e rollback.

Esqueleto pronto para preencher: [`assets/topologia-template.md`](assets/topologia-template.md).

## Validação

**Após Design:** confirmar redundância em todo componente — sem single point of
failure.

**Antes do cutover de migração:** validar peering/connectivity estabelecido:

```bash
# AWS: confirmar peering connection Active antes de prosseguir
aws ec2 describe-vpc-peering-connections \
  --filters "Name=status-code,Values=active"

# Azure: confirmar VNet peering state
az network vnet peering list \
  --resource-group myRG --vnet-name myVNet \
  --query "[].{Name:name,State:peeringState}"
```

**Após migração:** verificar saúde da aplicação e roteamento:

```bash
# AWS: checar target group health no ALB
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:...
```

**Após teste de DR:** confirmar RTO/RPO atingidos; documentar tempos reais.

## Regras (MUST / MUST NOT)

### MUST DO

- Derivar a disponibilidade-alvo do SLO de cada workload (`design-system-architecture` → capacidade e SLOs) e
  justificar zonas/regiões por ele — não aplicar "99,9%+" ou multi-região como regra universal.
- Somar limites e quotas de serviço (conexões de banco, quotas de API, IPs, throughput de fila) entre todas as
  réplicas e ambientes que compartilham a conta; autoscaling tem teto coerente com o downstream.
- Security by design (zero-trust, least-privilege).
- Infrastructure as code (Terraform, CloudFormation).
- Cost allocation tags e monitoramento de gasto habilitados.
- DR com RTO/RPO definidos e testados periodicamente.
- Multi-região quando RTO/RPO e o SLO exigirem, com custo e consistência de dados explícitos.
- Preferir serviços gerenciados (reduz complexidade operacional).
- Documentar decisões arquiteturais (ADR — ver `design-system-architecture`).

### MUST NOT DO

- Guardar credenciais em código ou repositórios públicos.
- Pular encryption (at rest e in transit).
- Criar single point of failure.
- Ignorar oportunidades de cost optimization.
- Deploy sem monitoramento.
- Arquiteturas desnecessariamente complexas (YAGNI).
- Ignorar compliance (LGPD, PCI, SOC2 quando aplicável).
- Pular teste de DR.

## Tráfego, limites e capacidade downstream

Ao desenhar a topologia, registre para cada salto (DNS → CDN/WAF → LB → gateway → serviço → dependências):

| Salto | Decisão obrigatória |
|---|---|
| DNS | TTL e tempo real de failover (caches de resolvers/clientes ignoram TTL baixo); health check e política (latência, geo, failover) |
| CDN/edge | Chave de cache (inclui tenant/idioma quando aplicável), conteúdo autenticado nunca compartilhado, proteção de origem |
| WAF/borda | Rate limit por IP/identidade na borda contra abuso volumétrico; a quota de aplicação é complementar |
| Load balancer | L4 × L7, algoritmo, health check, **timeout ocioso** compatível com conexões longas, drenagem (deregistration delay) ≥ tempo de encerramento da aplicação |
| Gateway/proxy | Timeout menor que o do cliente e maior que o do serviço; **um único dono do retry**; limites de body/header |
| Serviço | Autoscaling com teto; métricas de saturação; readiness que reflete capacidade de atender |
| Dependências gerenciadas | Quotas do provedor (ex.: conexões RDS, TPS de API), limites por conta/região e o que acontece ao atingi-los |

Detalhes conceituais: `design-system-architecture` → [rede e tráfego](../design-system-architecture/references/rede-trafego.md);
proteções na aplicação: `resiliencia-controle-fluxo-java`. Isolamento: separe contas/projetos por ambiente e,
quando a criticidade exigir, por tenant/carga (bulkhead de infraestrutura) — quota compartilhada é ponto de falha
comum.

## Gotchas

- TTL baixo de DNS não garante failover rápido: caches de resolvers e clientes o ignoram.
- Autoscaling sem teto coerente com o downstream derruba o banco ou estoura quota de API.
- Multi-região sem custo e consistência de dados explícitos é complexidade sem ganho (YAGNI).
- Peering/conectividade precisa estar `Active` antes do cutover; validar, não presumir.
- Exemplos de `references/padroes-cloud.md` são ponto de partida: CIDRs e escopos de IAM devem ser ajustados.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| `references/aws.md` | EC2, S3, Lambda, RDS, Well-Architected Framework, landing zone, DR na AWS |
| `references/azure.md` | VMs, Storage, Functions, SQL, Cloud Adoption Framework |
| `references/gcp.md` | Compute Engine, Cloud Storage, BigQuery |
| `references/multi-cloud.md` | Camadas de abstração, portabilidade, lock-in |
| `references/cost.md` | Reserved/spot, right-sizing, FinOps |
| `references/padroes-cloud.md` | Exemplos prontos: IAM least-privilege, VPC pública/privada, auto-scaling, análise de custo |

## Quem aplica o quê

| Cenário | Agent / Modo | Skills complementares |
|---|---|---|
| Desenhar topologia cloud nova | `cloud-architect` (sessão dedicada) | `design-system-architecture`, `devops-cicd` |
| Auditar arquitetura cloud existente | `cloud-architect` (modo `auditoria`) | `seguranca-aplicacao-java` |
| Plano de migração | `cloud-architect` (sessão dedicada) | `devops-cicd`, `design-system-architecture` |
| Otimização FinOps | `cloud-architect` (sessão dedicada) | `monitoramento-java` |
| Deploy de app Java específica | `engenheiro-devops` (variante `k8s`) | `devops-cicd` |

[Documentação base](https://jeffallan.github.io/claude-skills/skills/infrastructure/cloud-architect/)
