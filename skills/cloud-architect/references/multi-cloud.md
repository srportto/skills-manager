# Multi-Cloud Architecture Reference

Comprehensive guide for multi-cloud strategies, abstraction layers, portability patterns, and vendor lock-in mitigation.

## Sumário

- [Multi-Cloud Strategy](#multi-cloud-strategy)
- [Abstraction Layers](#abstraction-layers)
- [Data Synchronization](#data-synchronization)
- [Vendor Lock-In Mitigation](#vendor-lock-in-mitigation)
- [Network Connectivity](#network-connectivity)
- [Cost Management](#cost-management)
- [Observability](#observability)
- [Security Considerations](#security-considerations)

## Multi-Cloud Strategy

### When to Use Multi-Cloud

**Valid Use Cases**
- Regulatory compliance requiring data residency in specific regions
- Best-of-breed service selection (BigQuery for analytics, AWS for ML)
- Acquisition integration (different clouds in merged organizations)
- Disaster recovery with cloud provider as failure domain
- Negotiating leverage with cloud vendors

**Poor Reasons for Multi-Cloud**
- "Avoiding vendor lock-in" without specific exit scenario
- Assuming portability is free (it has significant costs)
- Political decisions without technical justification
- Spreading workloads arbitrarily across providers

### Multi-Cloud Patterns

**Active-Active**
```
Users -> Global Load Balancer
              |
    +---------+---------+
    |                   |
  AWS Region        GCP Region
    |                   |
    +----> Data Sync <--+
```
- Highest complexity and cost
- Best for global latency optimization
- Requires robust data synchronization

**Active-Passive (DR)**
```
Users -> Primary (AWS)
              |
         [Failover]
              |
         Secondary (Azure)
```
- Lower complexity than active-active
- Cloud provider becomes failure domain
- Cold or warm standby in secondary cloud

**Segmented by Workload**
```
Analytics -> GCP (BigQuery)
Core App  -> AWS (ECS, RDS)
Office    -> Azure (M365 integration)
```
- Each workload on best-fit cloud
- No cross-cloud data synchronization
- Simplest multi-cloud pattern

## Abstraction Layers

### Infrastructure Abstraction

**Terraform (Recommended)**
```hcl
# Provider-agnostic module structure
module "compute" {
  source = "./modules/compute"

  provider_type = var.cloud_provider  # aws, azure, gcp
  instance_type = var.instance_size
  region        = var.region
}

# Provider-specific implementations
# modules/compute/aws.tf
resource "aws_instance" "main" {
  count         = var.provider_type == "aws" ? 1 : 0
  ami           = data.aws_ami.latest.id
  instance_type = local.aws_instance_map[var.instance_size]
}

# modules/compute/azure.tf
resource "azurerm_virtual_machine" "main" {
  count    = var.provider_type == "azure" ? 1 : 0
  vm_size  = local.azure_vm_map[var.instance_size]
}
```

**Pulumi (Code-First, SDK Java)**
```java
// Abstrai o tamanho da máquina; cada provedor traduz para o seu catálogo.
enum Tamanho { PEQUENO, MEDIO, GRANDE }

record ConfigCompute(Tamanho tamanho, String regiao) {}

static final Map<Tamanho, String> TIPOS_AWS = Map.of(
        Tamanho.PEQUENO, "t3.small", Tamanho.MEDIO, "m6i.large", Tamanho.GRANDE, "m6i.2xlarge");
static final Map<Tamanho, String> TIPOS_GCP = Map.of(
        Tamanho.PEQUENO, "e2-small", Tamanho.MEDIO, "n2-standard-2", Tamanho.GRANDE, "n2-standard-8");

static void criarAws(ConfigCompute config) {
    new com.pulumi.aws.ec2.Instance("web", com.pulumi.aws.ec2.InstanceArgs.builder()
            .instanceType(TIPOS_AWS.get(config.tamanho()))
            // ami, subnet, tags...
            .build());
}

static void criarGcp(ConfigCompute config) {
    new com.pulumi.gcp.compute.Instance("web", com.pulumi.gcp.compute.InstanceArgs.builder()
            .machineType(TIPOS_GCP.get(config.tamanho()))
            .zone(config.regiao() + "-a")
            // bootDisk, networkInterfaces...
            .build());
}
```

### Container Orchestration (Kubernetes)

**Portable Kubernetes Deployment**
```yaml
# Same manifests work across EKS, AKS, GKE
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    spec:
      containers:
      - name: web
        image: myregistry/web:v1
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

**Cloud-Specific Considerations**
| Feature | EKS | AKS | GKE |
|---------|-----|-----|-----|
| Load Balancer | ALB/NLB annotations | Azure LB | GCP LB |
| Storage Class | gp3, io2 | managed-premium | pd-ssd |
| IAM Integration | IRSA | Workload Identity | Workload Identity |
| Ingress | AWS ALB Controller | AGIC | GKE Ingress |

### Application Abstraction

**Database Abstraction**
```java
// Protocolo padrão (JDBC/PostgreSQL): o mesmo código funciona com RDS, Azure Database for PostgreSQL,
// Cloud SQL ou PostgreSQL próprio. Só a URL e as credenciais mudam (configuração, não código).
var config = new HikariConfig();
config.setJdbcUrl(System.getenv("DATABASE_URL"));
config.setUsername(System.getenv("DATABASE_USER"));
config.setPassword(System.getenv("DATABASE_PASSWORD"));
config.setMaximumPoolSize(20);          // dentro do orçamento somado das réplicas
config.setConnectionTimeout(2_000);     // espera limitada pelo pool (ms)
DataSource banco = new HikariDataSource(config);
```

**Object Storage Abstraction**
```java
// API compatível com S3 (AWS S3, GCS em modo interoperável, MinIO, Cloudflare R2): só o endpoint muda.
var construtor = S3Client.builder()
        .region(Region.of(System.getenv().getOrDefault("S3_REGION", "us-east-1")))
        .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(System.getenv("ACCESS_KEY"), System.getenv("SECRET_KEY"))))
        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10)));
String endpoint = System.getenv("S3_ENDPOINT");
if (endpoint != null) {
    // Provedores não AWS normalmente exigem path-style.
    construtor.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
}
S3Client s3 = construtor.build();
```

## Data Synchronization

### Database Replication

**Cross-Cloud PostgreSQL**
```
AWS RDS Primary
      |
      | Logical Replication
      v
GCP Cloud SQL Replica (read-only)
```

Configuration:
```sql
-- On primary (AWS RDS)
CREATE PUBLICATION my_publication FOR ALL TABLES;

-- On replica (GCP Cloud SQL)
CREATE SUBSCRIPTION my_subscription
  CONNECTION 'host=aws-rds-endpoint dbname=mydb user=repl'
  PUBLICATION my_publication;
```

**Conflict Resolution Strategies**
- Last-write-wins (timestamp-based)
- Application-level conflict resolution
- CRDT data structures for eventually consistent data
- Avoid multi-master for transactional data

### Object Storage Sync

**Rclone for Cross-Cloud Sync**
```bash
# Sync S3 to GCS
rclone sync s3:my-bucket gcs:my-bucket \
  --transfers 32 \
  --checkers 16 \
  --s3-upload-concurrency 8

# Bidirectional sync with conflict handling
rclone bisync s3:bucket gcs:bucket \
  --conflict-resolve newer
```

**Event-Driven Replication**
```
S3 Bucket -> S3 Event -> Lambda -> GCS Upload
                              |
                              v
                       Consistency Check
```

## Vendor Lock-In Mitigation

### Lock-In Risk Assessment

| Service Type | Lock-In Risk | Mitigation Strategy |
|--------------|--------------|---------------------|
| Compute (VMs) | Low | Standard OS images, IaC |
| Kubernetes | Low | Portable manifests, avoid proprietary add-ons |
| Object Storage | Low | S3-compatible API, standard formats |
| Managed Databases | Medium | Standard SQL, logical backups |
| Serverless Functions | High | Abstraction layers, containers |
| Proprietary AI/ML | High | Open-source alternatives, ONNX models |
| Managed Services | High | Evaluate portability before adoption |

### Mitigation Strategies

**1. Use Open Standards**
- SQL databases over proprietary NoSQL
- Kubernetes over ECS/Cloud Run
- S3 API for object storage
- OpenTelemetry for observability
- OIDC for authentication

**2. Abstract Proprietary Services**
```java
// Porta do domínio: o caso de uso não conhece o provedor (adapter de saída por nuvem).
public interface PublicadorMensagens {
    void publicar(String mensagem);
}

final class PublicadorSqs implements PublicadorMensagens {
    private final SqsClient sqs;
    private final String filaUrl;
    PublicadorSqs(SqsClient sqs, String filaUrl) { this.sqs = sqs; this.filaUrl = filaUrl; }

    @Override public void publicar(String mensagem) {
        sqs.sendMessage(r -> r.queueUrl(filaUrl).messageBody(mensagem));
    }
}

final class PublicadorPubSub implements PublicadorMensagens {
    private final Publisher publisher; // com.google.cloud.pubsub.v1.Publisher, um por tópico
    PublicadorPubSub(Publisher publisher) { this.publisher = publisher; }

    @Override public void publicar(String mensagem) {
        try {
            // Espera a confirmação com prazo: publicação sem confirmação não é publicação.
            publisher.publish(PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(mensagem)).build())
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação interrompida", erro);
        } catch (ExecutionException | TimeoutException erro) {
            throw new IllegalStateException("Falha ao publicar no Pub/Sub", erro);
        }
    }
}

// A escolha do provedor é configuração de montagem (ex.: @ConditionalOnProperty), não switch no domínio.
```

A abstração esconde o SDK, **não** as diferenças semânticas: SQS e Pub/Sub diferem em ordem, deduplicação,
retenção e limites. Documente quais garantias a porta promete (normalmente at-least-once sem ordem) e teste
cada adapter contra o serviço real ou emulador.

**3. Maintain Exit Capability**
- Regular data export testing
- Document cloud-specific dependencies
- Keep IaC portable across providers
- Estimate migration effort annually

**4. Containerize Everything**
```dockerfile
# Portable container runs anywhere
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 3000
CMD ["node", "server.js"]
```

## Network Connectivity

### Cross-Cloud Networking

**VPN Interconnect**
```
AWS VPC                          GCP VPC
   |                                |
   +---> AWS VPN Gateway            |
              |                     |
              | IPsec tunnel        |
              |                     |
              +---> GCP Cloud VPN <-+
```

**Dedicated Interconnect (Enterprise)**
```
On-Premises Data Center
         |
    +----+----+
    |         |
AWS Direct  GCP Cloud
Connect     Interconnect
    |         |
    v         v
AWS VPC    GCP VPC
    |         |
    +----+----+
         |
    Transit Hub (e.g., Megaport, Equinix)
```

### Service Mesh Across Clouds

**Istio Multi-Cluster**
```yaml
# Primary cluster (AWS EKS)
apiVersion: install.istio.io/v1alpha1
kind: IstioOperator
spec:
  values:
    global:
      meshID: multi-cloud-mesh
      multiCluster:
        clusterName: eks-primary
      network: aws-network

# Remote cluster (GCP GKE)
spec:
  values:
    global:
      meshID: multi-cloud-mesh
      multiCluster:
        clusterName: gke-secondary
      network: gcp-network
```

## Cost Management

### Cross-Cloud Cost Visibility

**FinOps Tools**
- CloudHealth by VMware
- Apptio Cloudability
- Spot.io (now part of NetApp)
- Kubecost for Kubernetes

**Unified Tagging Strategy**
```
Required Tags (all clouds):
- environment: prod/staging/dev
- cost-center: engineering/marketing/sales
- owner: team-name
- project: project-code
- managed-by: terraform/manual
```

### Cost Comparison Framework

```
| Workload Type | AWS | Azure | GCP | Decision |
|---------------|-----|-------|-----|----------|
| General Compute | EC2 m5 | D-series | n2-standard | Compare $/vCPU/hour |
| GPU Training | p4d | NC-series | A2 | GCP often cheaper |
| Object Storage | S3 | Blob | GCS | Similar, check egress |
| Analytics | Redshift | Synapse | BigQuery | BigQuery for ad-hoc |
| Kubernetes | EKS | AKS | GKE | GKE Autopilot simplest |
```

## Observability

### Unified Monitoring Stack

**OpenTelemetry Collector**
```yaml
# Collect from all clouds, export to single backend
receivers:
  otlp:
    protocols:
      grpc:
      http:

processors:
  batch:
  attributes:
    actions:
      - key: cloud.provider
        action: upsert
        value: ${CLOUD_PROVIDER}

exporters:
  prometheus:
    endpoint: "0.0.0.0:8889"
  jaeger:
    endpoint: jaeger:14250

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, attributes]
      exporters: [jaeger]
    metrics:
      receivers: [otlp]
      processors: [batch, attributes]
      exporters: [prometheus]
```

**Grafana for Unified Dashboards**
- AWS CloudWatch data source
- Azure Monitor data source
- GCP Cloud Monitoring data source
- Single pane of glass across all clouds

## Security Considerations

### Identity Federation

**Cross-Cloud Identity**
```
Corporate IdP (Okta/Azure AD)
         |
    SAML/OIDC
         |
    +----+----+----+
    |    |    |    |
  AWS  Azure  GCP  K8s
  IAM   AD   IAM  RBAC
```

### Secrets Management

**HashiCorp Vault (Cloud-Agnostic)**
```hcl
# Single secrets management across clouds
resource "vault_aws_secret_backend_role" "aws_role" {
  backend = vault_aws_secret_backend.aws.path
  name    = "app-role"
  credential_type = "iam_user"
}

resource "vault_gcp_secret_roleset" "gcp_role" {
  backend     = vault_gcp_secret_backend.gcp.path
  roleset     = "app-role"
  project     = var.gcp_project
  token_scopes = ["https://www.googleapis.com/auth/cloud-platform"]
}
```

### Network Security

**Zero-Trust Across Clouds**
- mTLS between all services (service mesh)
- No implicit trust based on network location
- Identity-based access control
- Encrypted transit between clouds (VPN/interconnect)
