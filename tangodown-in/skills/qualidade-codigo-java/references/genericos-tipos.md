# Genericos e tipos explicitos

Leia este arquivo ao ver raw types, assinaturas que exigem implementacao concreta (`ArrayList`) ou tipos inferidos que o leitor (humano ou LLM) precisa adivinhar.

## Genericos e type safety (tipos explicitos para IA)

**[Codigo Nao Aderente]:**
```java
// raw type; agente precisa inferir
public Map indexById(Collection items) { ... }   // sem type safety
```

**[Violacao e Explicacao]:** codigo sem anotacoes de tipo ou com raw types obriga agentes e
humanos a inferirem o que entra e sai, gerando falhas. O agente poupa trabalho de descoberta em
codigos tipados.

**[Exemplo de Refatoracao]:**
```java
// generic explicito
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }
```

### Tipo de parametro: prefira interface a implementacao concreta

Assinatura de metodo deve receber (e retornar, quando fizer sentido) o tipo mais generico que
atenda o contrato - normalmente uma interface (`List`, `Map`, `Set`) - nunca a implementacao
concreta (`ArrayList`, `HashMap`, `HashSet`). Isso desacopla o chamador da escolha de estrutura
interna e permite trocar a implementacao sem quebrar callers.

**[Codigo Nao Aderente]:**
```java
// amarra o caller a ArrayList; List.of(...) (imutavel) ou LinkedList exigiriam copia so pra chamar
public void processarClientes(ArrayList<String> customerNames) { ... }
```

**[Violacao e Explicacao]:** o parametro exige especificamente `ArrayList`; o metodo nao deveria
se importar com a implementacao, so com o contrato (`List`). Um caller com `List.of(...)` ou
`LinkedList` precisa copiar a colecao so para satisfazer a assinatura.

**[Exemplo de Refatoracao]:**
```java
// aceita qualquer List; caller escolhe a implementacao que fizer sentido
public void processarClientes(List<String> customerNames) { ... }
```


