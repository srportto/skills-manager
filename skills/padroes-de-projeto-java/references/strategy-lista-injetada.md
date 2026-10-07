# Strategy por lista injetada

Leia este arquivo quando o problema for "escolher um serviço/produto entre vários candidatos em runtime" em código Spring: ele mostra a forma preferida do projeto (Strategy com `List<Interface>` injetada, sem factory dedicada), um antes/depois e quando preferir `sealed` + `switch` no lugar.

## O pattern preferido do projeto: Strategy por lista injetada

Quando o problema é "escolher um serviço/produto entre vários candidatos em runtime", o padrão deste
repositório **não** usa uma factory dedicada — usa `List<Interface>` injetada pelo Spring, com cada
implementação se autodeclarando capaz (ou não) de tratar a requisição. Fonte:
`docs/based-java-aplication.md` ("Strategy Pattern para Múltiplos Produtos") — reconstrução
ilustrativa, não existe `.java` literal em `docs/` para copiar:

```java
// Cada produto se autodeclara capaz de tratar a requisição — sem factory dedicada
public interface ContratacaoService {
    boolean validaContratacaoSuportada(CriarAutorizacaoRequest request);
    AutorizacaoResponse contratar(CriarAutorizacaoRequest request);
}

@Service
public class PixAutoService implements ContratacaoService {
    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return TipoProduto.PIX_AUTO.equals(request.tipoProduto());
    }
    // ...
}
// DdaAutoService, BoletoAutoService etc. seguem o mesmo formato — um @Service por produto

// Spring injeta TODAS as implementações de ContratacaoService automaticamente; sem factory,
// sem if/switch por tipo — adicionar produto novo = criar um @Service novo, nada existente muda
@Service
public class ContratacaoOrquestradorService {
    private final List<ContratacaoService> servicos;

    public ContratacaoOrquestradorService(List<ContratacaoService> servicos) { this.servicos = servicos; }

    public AutorizacaoResponse contratar(CriarAutorizacaoRequest request) {
        return servicos.stream()
                .filter(servico -> servico.validaContratacaoSuportada(request))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Produto não suportado"))
                .contratar(request);
    }
}
```

Prefira esta forma (Strategy + lista injetada) a uma `ProdutoStrategyFactory` sempre que as
implementações já são beans do Spring e a seleção pode virar um predicado simples
(`validaXSuportada(request)`).


## Antes → depois (Java 25)

**Antes:** `if/switch` por tipo dentro do orquestrador — todo produto novo mexe em código existente.

```java
@Service
public class ContratacaoOrquestradorService {
    public AutorizacaoResponse contratar(CriarAutorizacaoRequest request) {
        // Cada produto novo exige editar este método (viola aberto/fechado)
        return switch (request.tipoProduto()) {
            case PIX_AUTO -> pixAutoService.contratar(request);
            case DDA_AUTO -> ddaAutoService.contratar(request);
            default -> throw new BusinessException("Produto não suportado");
        };
    }
}
```

**Depois:** a lista injetada acima — adicionar produto = criar um `@Service` novo; o orquestrador não muda.

## Alternativa sem Spring: `sealed` + `switch` exaustivo

Quando o conjunto de variantes é **fechado e conhecido** (meios de pagamento, por exemplo), um tipo selado com
`switch` exaustivo e sem `default` é mais simples que Strategy: um subtipo novo quebra a compilação onde falta
tratar. Exemplo executável:
[`Pagamento`](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java) (hierarquia
selada) e [`Tarifacao`](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Tarifacao.java)
(`switch` exaustivo com padrões de record), provados em
[`TarifacaoTest`](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/TarifacaoTest.java).
Prefira Strategy por lista injetada quando as implementações são beans independentes e abertas a extensão.
