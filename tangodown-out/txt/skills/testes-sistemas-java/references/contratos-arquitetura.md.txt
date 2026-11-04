# Contratos, arquitetura e catálogo

Leia este arquivo quando precisar testar contrato HTTP, compatibilidade de schema, fronteiras de arquitetura (ArchUnit) ou a validação do catálogo.

Contrato HTTP: métodos, códigos, payload, validação, erros, idempotência, 429/503 e headers. Prefira cliente HTTP Java contra servidor em porta efêmera quando testar o comportamento de borda.

Compatibilidade: campo opcional/aditivo, significado do enum, schema antigo/novo e leitor antigo. Alteração de banco usa expand/contract com janela de convivência e rollback documentado. Ordem de eventos e dados stale fazem parte do contrato.

Arquitetura: domínio puro não depende de Spring/JPA/adapters; dependências atravessam portas. Use ArchUnit quando existir estrutura de camadas; não aplique regra hexagonal a todo exemplo JDK.

Catálogo: parser YAML para identificadores, parser Markdown para links reais (ignorar blocos de exemplo), existência de referências e inventário. Testes não julgam redação por frases exatas. Casos realistas aplicados por outro agente medem qualidade de instruções; registrar saída bruta e rubrica separadamente.

[Validação Java do catálogo](../../../validation/java/pom.xml) não requer containers nem os insumos locais ignorados em .docs.


## Exemplos em Java 25

### Regra de arquitetura hexagonal com ArchUnit

Dependência de teste: `com.tngtech.archunit:archunit-junit5` (escopo `test`; use a versão corrente no `pom.xml` do
projeto). O domínio não pode depender de infraestrutura nem de Spring/JPA; a aplicação não pode depender de adapters.

```java
// ERRADO: a regra existe só em wiki/PR - ninguém percebe quando um import de Spring entra no domínio
// (domain/Pedido.java)  import org.springframework.stereotype.Component;  // compila e passa na revisão cansada

// CERTO: o build quebra quando a fronteira é violada
@AnalyzeClasses(packages = "br.com.srportto.pedidos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaHexagonalTest {

    @ArchTest
    static final ArchRule dominioNaoDependeDeFramework =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..", "..infrastructure..", "org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule camadasRespeitamAsPortas =
            layeredArchitecture().consideringOnlyDependenciesInLayers()
                    .layer("domain").definedBy("..domain..")
                    .layer("application").definedBy("..application..")
                    .layer("infrastructure").definedBy("..infrastructure..")
                    .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "infrastructure")
                    .whereLayer("application").mayOnlyBeAccessedByLayers("infrastructure")
                    .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer();
}
```

Prove que a regra realmente falha: introduza temporariamente o import proibido e confirme o erro antes de confiar
nela. Estrutura de pacotes e regras de camada: `arquitetura-limpa-java`. Não aplique a regra hexagonal aos
exemplos JDK de `examples/java`, que não têm camadas.

### Contrato HTTP contra servidor em porta efêmera

```java
// Servidor do próprio JDK na porta 0 (efêmera): sem porta fixa, sem conflito em CI
@Test
void deveRetornar429ComRetryAfterQuandoSaturado() throws Exception {
    var servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    servidor.createContext("/pedidos", troca -> {
        troca.getResponseHeaders().add("Retry-After", "1");
        troca.sendResponseHeaders(429, -1);
        troca.close();
    });
    servidor.start();
    try {
        var cliente = HttpClient.newHttpClient();
        var requisicao = HttpRequest.newBuilder(
                URI.create("http://localhost:" + servidor.getAddress().getPort() + "/pedidos"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        var resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, resposta.statusCode());
        assertEquals("1", resposta.headers().firstValue("Retry-After").orElseThrow());
    } finally {
        servidor.stop(0);
    }
}
```

Aplicação Spring Boot de exemplo com teste de contexto e endpoints:
[CheckoutApplicationTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java).
Slice `@WebMvcTest` e validação de borda: [testes-slice-spring](testes-slice-spring.md).
