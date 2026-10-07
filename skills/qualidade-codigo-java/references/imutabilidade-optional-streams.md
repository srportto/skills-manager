# Imutabilidade, Optional e Streams

Leia este arquivo ao modelar DTO/value object (setters publicos, estado mutavel), ao usar `Optional.get()` ou ao escrever pipeline de stream com `forEach` mutando lista externa.

## Imutabilidade

**[Codigo Nao Aderente]:**
```java
// classe com setters publicos: estado mutavel depois da construcao
public class Market {
    private Long id;
    private String name;
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}
```

**[Violacao e Explicacao]:** setters publicos expoem o estado interno mutavel; o chamador
pode alterar `id`/`name` apos a construcao, quebrando invariantes do dominio e criando bugs
sutis em fluxos assincronos.

**[Exemplo de Refatoracao]:**
```java
// record para DTOs e value objects (imutavel, equals/hashCode/toString gerados)
public record MarketDto(Long id, String name, MarketStatus status) {}

// ou classe com final fields e getters only
public class Market {
    private final Long id;
    private final String name;
    // getters only, no setters
}
```

Records sao o padrao deste catalogo (ver `java-moderno`): use para DTOs, value objects, chaves
compostas (`IdAutorizacao`). Nao use records quando precisar de mutabilidade ou heranca.

## Optional - uso correto

**[Codigo Nao Aderente]:**
```java
// get() sem verificar presenca
public Market buscarPorSlug(String slug) {
    Optional<Market> market = marketRepository.findBySlug(slug);
    return market.get();   // NoSuchElementException se vazio
}
```

**[Violacao e Explicacao]:** `Optional.get()` sem `.orElse`/`.orElseThrow`/`.isPresent()` joga
a decisao para o `NoSuchElementException` em runtime; o caller nao tem como reagir.

**[Exemplo de Refatoracao]:**
```java
// retorne Optional de metodos find*, use map/flatMap em vez de get() direto
public Market buscarPorSlug(String slug) {
    return marketRepository.findBySlug(slug)
        .orElseThrow(() -> new EntityNotFoundException("Market not found: " + slug));
}
```

## Streams - pipelines curtos, sem efeito colateral

**[Codigo Nao Aderente]:**
```java
// forEach com mutacao de lista externa
List<String> nomesAtivos = new ArrayList<>();
markets.stream().forEach(m -> {
    if (m.isAtivo()) {
        nomesAtivos.add(m.name().toUpperCase());
    }
});
```

**[Violacao e Explicacao]:** `forEach` capturando variavel externa e o anti-pattern classico
de stream; forca o agente a rastrear o efeito colateral e quebra paralelizacao futura.

**[Exemplo de Refatoracao]:**
```java
// pipeline curto, transformacao pura
List<String> names = markets.stream()
    .filter(Market::isAtivo)
    .map(m -> m.name().toUpperCase())
    .toList();
```

Quando o pipeline exigiria multiplos `flatMap`/estado acumulado so para simular um `for`, prefira o
loop explicito - clareza vale mais que "tudo em stream".

