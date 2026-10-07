Leia este arquivo quando for escrever, validar ou gerar código a partir do contrato OpenAPI 3.1.

## OpenAPI 3.1 (especificação)

A fonte da verdade do contrato. Gere o `openapi.yaml` **antes** de implementar o controller; o
controller é uma consequência do contrato, não o contrário.

```yaml
openapi: 3.1.0
info:
  title: Orders API
  version: 1.0.0
paths:
  /api/v1/orders/{id}:
    get:
      summary: Get order by id
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        '200':
          description: Order found
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Order' }
        '404':
          description: Not found
components:
  schemas:
    Order:
      type: object
      required: [id, status, customerId]
      properties:
        id: { type: string, format: uuid }
        status: { type: string, enum: [PENDING, APPROVED, CANCELLED] }
        customerId: { type: string, format: uuid }
```

Em vez de escrever DTO e controller à mão, gere-os a partir do `openapi.yaml` com
`openapi-generator-maven-plugin` — o contrato vira a fonte única de verdade.


## Exemplo antes/depois — contrato completo

Antes: contrato só com o caminho feliz (sem erro, sem paginação, sem limites).
Depois: use `assets/openapi-base.yaml` como ponto de partida — traz `ProblemDetail` (RFC 9457),
parâmetros `page`/`size`/`sort`, e respostas `429`/`503` com header `Retry-After`. Validação:

```bash
npx @redocly/cli lint assets/openapi-base.yaml
npx @stoplight/prism-cli mock assets/openapi-base.yaml
```

Geração de código a partir do contrato (trecho do `pom.xml`; `interfaceOnly` mantém o controller seu):

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <configuration>
    <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
    <generatorName>spring</generatorName>
    <configOptions>
      <interfaceOnly>true</interfaceOnly>
    </configOptions>
  </configuration>
</plugin>
```

Idempotência, 429 e 503 no contrato: ver [idempotencia-quotas-http.md](idempotencia-quotas-http.md).
