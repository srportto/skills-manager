# Pipeline CI/CD (GitHub Actions)

Leia este arquivo quando for montar ou ajustar o pipeline de CI/CD de uma app Java/Maven (variante `pipeline` do agent `engenheiro-devops`): estágios, quality gates, versionamento, cache e estratégias de deployment. Pipeline pronto para copiar: [ci.yml](../assets/ci.yml).

## Pipeline mínimo (build → test → package)

```yaml
name: ci
on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configurar JDK 25 (Temurin)
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build, testes e empacotamento
        run: mvn clean verify

      - name: Publicar artefato
        uses: actions/upload-artifact@v4
        with:
          name: app-jar
          path: target/*.jar
```

## Quality gates

- **Testes**: o build **deve falhar** se qualquer teste falhar — `mvn clean verify` já falha com
  testes vermelhos. **Nunca** usar `-DskipTests` em pipeline de CI.
- **Cobertura**: se o projeto tiver JaCoCo configurado, o gate barra merge abaixo do limiar (ex.: 80%)
  com `mvn jacoco:check -Djacoco.minimum.coverage=0.80`.
- **Dependências vulneráveis**: varredura de CVE no PR com
  `mvn org.owasp:dependency-check-maven:check` — ver `seguranca-aplicacao-java`.

## Versionamento e cache

- Versione o artefato com `${project.version}` do Maven, tag Git ou `${{ github.sha }}`
  (`mvn clean package -Drevision=${{ github.sha }}`); **evite** publicar sempre `app.jar` sem versão
  em ambientes não-efêmeros.
- Cache: `actions/setup-java` com `cache: 'maven'` resolve a maioria dos casos; para cache custom,
  `actions/cache@v4` com chave baseada em hash do `pom.xml`.

## Estratégias de deployment (acoplado à pipeline)

| Estratégia | Mecanismo | Quando usar |
|---|---|---|
| Rolling update (default K8s) | `RollingUpdate` com `maxUnavailable`/`maxSurge` | Padrão — substitui réplicas gradualmente |
| Blue-Green | Deploy em slot paralelo (`myapp-blue`), depois `kubectl patch service` troca o seletor | Rollback instantâneo, mas exige 2x recursos durante o switch |
| Canary (Flagger) | CRD `Canary` desloca tráfego em passos (`stepWeight`) monitorando métricas | Validação gradual com rollback automático por métrica |

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

## Exemplo antes/depois: gate que não gate

```yaml
# ANTES — pipeline "verde" sem evidência: testes pulados, JDK defasado, sem cache
- uses: actions/setup-java@v4
  with: { java-version: '17', distribution: 'temurin' }
- run: mvn clean package -DskipTests
```

```yaml
# DEPOIS — teste vermelho derruba o build; JDK 25 com cache do Maven
- uses: actions/setup-java@v4
  with: { java-version: '25', distribution: 'temurin', cache: 'maven' }
- run: mvn -B verify
```

Quando a mudança toca broker, banco ou cache, acrescente um job com o perfil de integração (Testcontainers);
teste pulado aparece como pendente, nunca como verde. O pipeline completo está em [ci.yml](../assets/ci.yml).

## Pipeline real deste repositório

O catálogo valida a si mesmo com o mesmo desenho — jobs encadeados por `needs`, perfil `integracao` condicionado
a mudanças relevantes e carga só em execução agendada/manual:
[validar-catalogo.yml](../../../.github/workflows/validar-catalogo.yml). As provas executadas por esse
pipeline vivem em [examples/java](../../../examples/java/pom.xml) (`mvn -f examples/java/pom.xml verify`).
