---

name: revisao-de-codigo-java
description: "Checklist único de revisão de código Java/Spring Boot, organizado por severidade (Crítico / Importante / Menor) — consolida clean code, tratamento de erro, imutabilidade, testes, contrato de API, segurança e observabilidade. Use ao revisar diff, classe, PR ou logo após gerar código Java significativo. Uso: agents `java-revisor`/`projetista-api` ou `/revisao-de-codigo-java`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.3.0"
  domain: code-review
  triggers: revise, code review, está bom?, melhore este código, PR, checklist, severidade
  role: reviewer
  scope: code-review
  output-format: document
  related-skills: qualidade-codigo-java, padroes-de-projeto-java, java-moderno, monitoramento-java, persistencia-jpa, mensageria-sqs-kafka, seguranca-aplicacao-java
---

# Revisão de Código Java

## Visão geral

Checklist único de revisão de código Java/Spring Boot, organizado por severidade. Consolida três
fontes: princípios de clean code (DRY/KISS/YAGNI) e contrato de API, padrões de código Java
(nomenclatura, imutabilidade, `Optional`, streams) e categorias de revisão de um revisor genérico
(correção, testes, complexidade). Use sempre que for revisar um diff, uma classe, um PR, ou logo
depois de gerar código Java significativo.

**Quando NÃO usar:** para dúvida pontual sobre em qual camada um código deve viver, use
`arquitetura-limpa-java` diretamente (esta skill só referencia o checklist dela no grupo
"Arquitetura"). Para revisar somente o padrão de logs, use `monitoramento-java` (`references/logs-*.md`). Esta skill é a
fonte de verdade usada tanto para autorrevisão quanto pelos agents `java-revisor` e
`java-revisor` (modo `auditoria`) — veja "Quem revisa o quê" abaixo para saber qual agent invocar.

## Fluxo de revisão

1. **Entender a intenção da mudança** — antes de aplicar qualquer item do checklist, entenda o que o
   diff faz e por quê, para não reportar como "problema" uma decisão consciente do autor (ex.: um
   loop no lugar de stream porque é mais claro naquele caso específico).
2. **Passar o checklist** — aplique os 10 grupos da seção "Checklist" abaixo sobre o código/diff.
3. **Reportar achados agrupados por severidade** — cada achado leva `arquivo:linha` e uma explicação
   objetiva do porquê é um problema (siga o modelo em "Formato do relatório").
4. **Reconhecer o que está bom** — toda revisão termina com pontos positivos, não só críticas, para
   reforçar práticas corretas e manter a revisão construtiva.

## Severidades

| Severidade | Definição | Efeito |
|---|---|---|
| **Crítico** | Bug real, vazamento de recurso, falha de segurança, ou quebra de contrato (API, dados, retrocompatibilidade) | **Bloqueia** — não deve ir para produção/merge sem correção |
| **Importante** | Problema de manutenibilidade ou performance com impacto provável em produção, mas que não quebra nada hoje | Deveria ser corrigido antes do merge; pode virar débito técnico registrado se houver justificativa |
| **Menor** | Estilo, nomenclatura, preferência — não afeta comportamento nem manutenibilidade de forma relevante | Sugestão; não bloqueia |

## Checklist

> **Princípios que atravessam todos os itens abaixo (Clean Code for AI):** além de tornar o código
> correto e manutenível, cada item abaixo deve ser avaliado pelo impacto na **janela de contexto**
> do agente de IA que vai ler/manter esse código — nomes grepáveis, métodos curtos, arquivos
> pequenos, tipos explícitos e comentários "por que" reduzem alucinação. Veja
> `qualidade-codigo-java` para a versão "como aplicar" destes mesmos princípios.

### 1. Correção

**Null-safety** — métodos `find*` retornam `Optional`; nunca chame `Optional.get()` sem verificar
presença:

**[❌ Código Não Aderente]:**
```java
// Optional.get() sem verificar presenca, risco de NoSuchElementException
public Produto buscarPorId(Long id) {
    Optional<Produto> produto = produtoRepository.findById(id);
    return produto.get();
}
```

**[🚨 Violação e Explicação]:** `Optional.get()` lança `NoSuchElementException` quando o valor
está ausente; sem `.orElse`/`.orElseThrow`/`.isPresent()`, a borda não tem como reagir.

**[✅ Exemplo de Refatoração]:**
```java
// metodos find* retornam Optional; a borda decide o que fazer na ausencia
public Produto buscarPorId(Long id) {
    return produtoRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Produto nao encontrado: " + id));
}
```

**Exceções com contexto** — preserve a causa original e diga o que estava sendo feito:

**[❌ Código Não Aderente]:**
```java
// perde a causa original (e) e nao diz o que estava sendo feito
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}
```

**[🚨 Violação e Explicação]:** perde a `Throwable cause` (stack trace original) e produz
mensagem genérica sem contexto da operação — investigação quase impossível depois.

**[✅ Exemplo de Refatoração]:**
```java
// preserva a causa (e) e adiciona contexto do que falhou
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}
```

**Recursos com try-with-resources** — `close()` manual não executa se o código anterior lançar:

**[❌ Código Não Aderente]:**
```java
// close() nao executa se ler() lancar excecao, vazando o recurso
InputStream in = new FileInputStream(arquivo);
String conteudo = ler(in);
in.close();
```

**[🚨 Violação e Explicação]:** se `ler(in)` lançar, `in.close()` na linha seguinte nunca é
chamado; o recurso vaza até o GC rodar (pode ser tarde para conexões/socket/arquivo).

**[✅ Exemplo de Refatoração]:**
```java
// try-with-resources garante o fechamento mesmo em caso de excecao
try (InputStream in = new FileInputStream(arquivo)) {
    return ler(in);
}
```

### 2. Contrato HTTP

Status correto por origem do erro (400 validação / 422 negócio / 500 técnico), e DTOs de borda
imutáveis:

**[❌ Código Não Aderente]:**
```java
// regra de negocio violada devolvendo excecao generica, que o handler mapeia como 500
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
    if (request.preco().signum() <= 0) {
        throw new RuntimeException("preco invalido"); // handler generico -> 500, deveria ser 422
    }
    Produto criado = service.criar(request);
    return ResponseEntity.ok(mapper.paraResposta(criado));
}
```

**[🚨 Violação e Explicação]:** validação de regra de negócio devolvendo `RuntimeException`
genérica faz o handler central mapear como 500 (erro técnico), quando o correto é 422
(recurso não processável por regra de negócio). Cliente recebe diagnóstico errado.

**[✅ Exemplo de Refatoração]:**
```java
// 400 (formato) via @Valid no record de request, 422 (negocio) via BusinessException
@PostMapping
public ResponseEntity<ProdutoResponse> criar(@RequestBody @Valid CriarProdutoRequest request) {
    // @NotNull/@DecimalMin no record cobrem o 400 (falha de validacao de formato)
    Produto criado = service.criar(request); // service delega a produto.validar(), que lanca
                                              // BusinessException (422) se a regra de negocio falhar
    return ResponseEntity.created(URI.create("/produtos/" + criado.getId()))
            .body(mapper.paraResposta(criado));
}
```

**[❌ Código Não Aderente]:**
```java
// DTO mutavel com setters: o contrato de borda pode ser alterado apos a criacao
public class ProdutoResponse {
    private Long id;
    private String nome;
    public void setId(Long id) { this.id = id; }
    public void setNome(String nome) { this.nome = nome; }
}
```

**[🚨 Violação e Explicação]:** DTO de borda com setters públicos permite que o chamador altere
o contrato de saída após a construção; o JSON serializado deixa de refletir o estado do recurso.

**[✅ Exemplo de Refatoração]:**
```java
// record imutavel: contrato de borda fixado na construcao, sem setters
public record ProdutoResponse(Long id, String nome, BigDecimal preco) {}
```

### 3. Imutabilidade

Records para dados, `final` em campos, sem setters desnecessários.

**[❌ Código Não Aderente]:**
```java
// campo mutavel com setter publico: qualquer chamador pode alterar o pedido apos criado
public class Pedido {
    private BigDecimal valor;
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public BigDecimal getValor() { return valor; }
}
```

**[🚨 Violação e Explicação]:** setter público expõe o estado interno mutável; sem disciplina, o
chamador pode alterar `valor` depois da construção, quebrando invariantes do domínio e abrindo
caminho para inconsistências em fluxos assíncronos/concorrentes.

**[✅ Exemplo de Refatoração]:**
```java
// record: imutavel por natureza, sem setter, igualdade/hashCode/toString gerados
public record Pedido(String id, BigDecimal valor) {
    public void validar() {
        if (valor == null || valor.signum() <= 0) {
            throw new BusinessException("Valor do pedido deve ser maior que zero");
        }
    }
}
```

### 4. Streams

Pipelines curtos, sem efeitos colaterais; loop quando for mais claro.

**[❌ Código Não Aderente]:**
```java
// forEach com efeito colateral (mutacao de lista externa): dificil de ler e testar
List<String> nomesAtivos = new ArrayList<>();
produtos.stream().forEach(p -> {
    if (p.ativo()) {
        nomesAtivos.add(p.nome().toUpperCase());
    }
});
```

**[🚨 Violação e Explicação]:** `forEach` com captura de variável externa é o anti-pattern
clássico de stream: força o agente a rastrear o efeito colateral linha a linha e quebra a
paralelização futura.

**[✅ Exemplo de Refatoração]:**
```java
// pipeline curto, sem efeito colateral, transformacao pura
List<String> nomesAtivos = produtos.stream()
        .filter(Produto::ativo)
        .map(p -> p.nome().toUpperCase())
        .toList();
```

Quando o pipeline exigiria múltiplos `flatMap`/estado acumulado só para simular um `for`, prefira o
loop explícito — clareza vale mais que "tudo em stream".

### 5. Nomenclatura

`PascalCase` para tipos, `camelCase` para métodos/campos, `UPPER_SNAKE_CASE` para constantes; nomes
que revelam intenção **e são grepáveis** (fáceis de localizar com `ripgrep`/`grep`). Use português
ou inglês, mas seja consistente com o restante do projeto — não misture os dois no mesmo
pacote/classe.

**[❌ Código Não Aderente]:**
```java
// nomes genericos nao sao grepaveis; uma busca por "Handler" traz 200 arquivos
public class Handler {
    public void handle(String p, String rng) { ... }
    public void process() { ... }
}
private static final int N = 100;
```

**[🚨 Violação e Explicação]:** nomes genéricos (`Handler`, `process`, `N`) poluem a busca
lexical e escondem a intenção; o agente tem que ler o corpo para descobrir o que o método faz.

**[✅ Exemplo de Refatoração]:**
```java
// nomes especificos; busca lexical (rg "AutorizacaoExpiradaHandler") cai direto
public class AutorizacaoExpiradaHandler {
    public void expirarAutorizacao(AutorizacaoId id, MotivoExpiracao motivo) { ... }
}
private static final int TAMANHO_MAXIMO_PAGINA = 100;
```

**Proibidos** (nomes genéricos que poluem a busca lexical e escondem a intenção):
`Handler`, `Manager`, `Helper`, `Util`, `Data`, `Process`, `Service` sem qualificador
de domínio, `Info`, `Common`, `Base`. Use nomes de domínio: `AutorizacaoCommandService`,
`PixBufferRingPartitionPurgeManager` é aceitável **quando** o domínio é esse, mas
`Manager<Algo>` sozinho não é.

**Parâmetros de método** seguem a mesma regra "Don't Abbreviate" — sem sigla, e com a unidade de
medida explícita no nome quando o valor for numérico ou temporal:

**[❌ Código Não Aderente]:**
```java
// parametros abreviados e sem unidade obrigam o agente a abrir o corpo do metodo
public void register(String fn, String ln, int age, double amt) { ... }
public void schedule(long timeout, long delay) { ... }
```

**[🚨 Violação e Explicação]:** `fn`, `ln`, `amt` escondem nome/sobrenome/valor; `timeout` e
`delay` sem unidade obrigam o caller a abrir a implementação para saber se é milissegundos ou
segundos — erro clássico de integração entre serviços.

**[✅ Exemplo de Refatoração]:**
```java
// nomes completos, com a unidade explicita no proprio nome
public void registerUser(String firstName, String lastName, int ageInYears, double transactionAmount) { ... }
public void schedule(long timeoutInMilliseconds, long delayInSeconds) { ... }
```

> Grupo de parâmetros que sempre viaja junto (ex.: `latitude`/`longitude`) vira value object
> (`Coordinate`) — ver 6.2 (Primitive Obsession) e `qualidade-codigo-java` seção "Introduce
> Parameter Object". Parâmetro restrito a um conjunto conhecido de valores (`String status`,
> `int tipo`) vira `enum` — ver 5.1 (Magic Numbers).

### 6. Complexidade

Método longo (referência: 4–20 linhas), arquivo grande (300–500 linhas) e aninhamento acima de 3 níveis
dificultam leitura humana e do agente: sugira `Extract Method` e guard clauses. São **Importante** quando a
complexidade esconde um bug ou impede testar o fluxo; caso contrário, **Menor**. Exemplos em
[exemplos de revisão](references/exemplos-revisao-java.md#6-complexidade).

### 6.1. Heurísticas de design (Menor por padrão)

Magic numbers, tipagem explícita, comentários "por que", Tell Don't Ask, Primitive Obsession, Bloaters,
First Class Collections, Lei de Demeter e "no máximo dois atributos" são **heurísticas**: reporte como
**Menor**, salvo quando o achado mostra um risco concreto (ex.: o mesmo limite de negócio escrito com valores
diferentes em dois lugares → Importante). Quantidade de atributos, estilo de asserção ou ausência de um
pattern **não** são falha funcional. Exemplos e explicações completas:
[exemplos de revisão](references/exemplos-revisao-java.md); como aplicar: `qualidade-codigo-java`.

### 7. DRY com bom senso

Extraia duplicação real; não abstraia prematuramente — regra das 3 ocorrências (na 1ª e 2ª vez,
duplicar pode ser mais barato que a abstração errada; extraia quando a 3ª ocorrência confirmar o
padrão). Não crie uma `EmailValidator` com interface e implementação única "para o futuro" — isso é
abstração especulativa (veja `padroes-de-projeto-java`, seção "Quando NÃO aplicar pattern"); um
método privado já resolve a duplicação real.

**[❌ Código Não Aderente]:**
```java
// mesma validacao de email duplicada em tres pontos (criar, atualizar, importar em lote)
public void criar(UsuarioRequest req) {
    if (req.email() == null || !req.email().contains("@")) {
        throw new BusinessException("Email invalido");
    }
}

public void atualizar(UsuarioRequest req) {
    if (req.email() == null || !req.email().contains("@")) {
        throw new BusinessException("Email invalido");
    }
}

public void importarEmLote(List<UsuarioRequest> reqs) {
    for (UsuarioRequest req : reqs) {
        if (req.email() == null || !req.email().contains("@")) {
            throw new BusinessException("Email invalido");
        }
    }
}
```

**[🚨 Violação e Explicação]:** a 3ª ocorrência confirma o padrão; manter a duplicação
significa 3 lugares para corrigir quando a regra de email mudar. Mas a duplicação aqui é
**sintoma** — o próximo passo é extrair (não criar uma `EmailValidator` com interface + impl
+ factory "para o futuro").

**[✅ Exemplo de Refatoração]:**
```java
// 3a ocorrencia confirma o padrao: extrai um metodo unico, sem criar interface/factory
private void validarEmail(String email) {
    if (email == null || !email.contains("@")) {
        throw new BusinessException("Email invalido");
    }
}
```

### 8. Testes

Caso feliz + bordas + erro; nomes descritivos; sem dependência de ordem.

**[❌ Código Não Aderente]:**
```java
// nome nao descreve cenario, sem cobertura de borda/erro, so caminho feliz
@Test
void test1() {
    Produto produto = service.criar(request);
    assertEquals(produto.nome(), "Mouse");
}
```

**[🚨 Violação e Explicação]:** nome `test1` não diz o que está sendo testado; não há cobertura
de borda ou erro — quando o build quebrar, o agente tem que abrir o teste para entender o que
deveria estar passando.

**[✅ Exemplo de Refatoração]:**
```java
// nomes descritivos (deveXQuandoY), casos feliz + erro independentes
// (exemplo com AssertJ fluente; JUnit 5 puro tambem e valido, ver observacao abaixo)
@Test
void deveCriarProdutoQuandoDadosValidos() {
    Produto produto = service.criar(requestValido());

    assertThat(produto.nome()).isEqualTo("Mouse");
}

@Test
void deveLancarBusinessExceptionQuandoPrecoForZero() {
    assertThatThrownBy(() -> service.criar(requestComPrecoZero()))
            .isInstanceOf(BusinessException.class);
}
```

Use JUnit 5. AssertJ (`assertThat(...)`) é bem-vindo quando já está disponível no projeto — confira o
`pom.xml` antes de sugeri-lo em uma revisão — mas JUnit 5 puro (`assertEquals`, `assertThrows`,
`assertDoesNotThrow`) também é válido e é o padrão usado nos assets deste catálogo (`ProdutoTest`,
`PedidoTest`, `PublicarEventoServiceTest`, nenhum dos quais depende de AssertJ). Não marque
`assertEquals`/`assertThrows` como achado de revisão só por não ser AssertJ. Independente da
biblioteca de asserção, cada teste deve poder rodar sozinho (sem depender de estado deixado por um
teste anterior) e cobrir pelo menos um caso de borda (lista vazia, valor limite) além do feliz e do
erro.

**Evidência executada (vale para todo veredicto):** relate separadamente **compilação**, **testes unitários**,
**integração** (serviços reais) e **carga**, cada um com comando e resultado (passou/falhou/pulado e
contagem). Teste não executado é **pendência**, nunca aprovação; build com `-DskipTests` não sustenta
aprovação de regra de negócio nem de resiliência. Se o código toca concorrência, mensageria ou limites, exija a
prova correspondente (ver `testes-sistemas-java`) — "compila e o caminho feliz passa" não prova limite.

### 8.1. Resiliência, efeitos e limites

| Achado | Severidade | Por quê |
|---|---|---|
| Fila/buffer/executor **sem limite** (`new LinkedBlockingQueue<>()`, `Executors.newFixedThreadPool` com fila ilimitada, `onBackpressureBuffer()` sem capacidade) | **Crítico** em fluxo com carga externa | Pico vira `OutOfMemoryError` e latência ilimitada |
| Espera sem limite (`acquire()`, `get()`, `join()`, cliente HTTP/JDBC sem timeout, pool sem `connection-timeout`) | **Crítico** em chamada remota | Dependência lenta prende threads/conexões até esgotar |
| Retry infinito, sem backoff/jitter, de erro permanente, ou em várias camadas | **Crítico** | Amplifica a carga sobre a dependência que já está falhando |
| Ack/commit **antes** do efeito durável; descarte de mensagem de negócio sem quarentena | **Crítico** | Perda silenciosa de dados |
| Efeito sujeito a repetição sem idempotência persistida (cobrança, pedido, evento) | **Crítico** | Retry/reentrega duplica efeito financeiro |
| Fallback que inventa sucesso (pagamento "aprovado", estoque "reservado") | **Crítico** | Mentira de negócio |
| Permissão/recurso liberado antes do fim do trabalho assíncrono | **Crítico** | Limite de concorrência deixa de valer |
| Cache indisponível enviando todo o tráfego ao banco | **Importante** | Cascata sob falha |
| Limite sem unidade/escopo/motivo; rejeição sem métrica | **Importante** | Proteção não verificável nem operável |
| Liveness dependente de serviço externo | **Importante** | Reinício em massa na queda da dependência |
| `traceId`/id de negócio como label de métrica | **Importante** | Explosão de cardinalidade |

Referência dos mecanismos: `resiliencia-controle-fluxo-java`; ack/offset/DLQ: `mensageria-sqs-kafka`. Não
exija breaker, reatividade ou broker onde não há dependência/carga que justifique (proporcionalidade).

### 9. Logs

Siga `monitoramento-java/references/logs-*.md` para o formato e a estrutura completos. Nesta revisão, verifique:

- nenhum dado sensível (senha, token, CPF, cartão) aparece em log, nem mesmo em nível `debug`;
- a mensagem carrega contexto suficiente para investigar sem precisar reproduzir (IDs de negócio,
  não só "erro ao processar");
- o nível (`info`/`warn`/`error`) condiz com a severidade real do evento.

### 10. Arquitetura

Siga a skill `arquitetura-limpa-java` para o mapa completo de camadas e o checklist arquitetural.
Nesta revisão, verifique pelo menos:

- a camada onde a mudança caiu é a correta (regra de negócio em `domain`, orquestração em
  `application/usecase`, adaptador em `infrastructure`);
- nenhuma dependência aponta "para fora" (`domain` não importa `org.springframework.*` nem
  `jakarta.persistence.*`; `application` não importa `jakarta.servlet.*` nem Spring Data);
- todo acesso a recurso externo passa por uma `port/out` do `domain`, implementada por um adapter.

## Quem revisa o quê

| Situação | Quem revisa | Papel |
|---|---|---|
| Diff pontual durante o desenvolvimento (uma classe, um método, um PR pequeno) | agent `java-revisor` | Revisão **tempestiva** — feedback rápido durante o trabalho, aplicando este checklist |
| Pré-merge, mudança grande, ou revisão do trabalho de outro agent (ex.: saída do `java-construtor`) | agent `java-revisor` (modo `auditoria`) | **Veredicto final** — achados Críticos bloqueiam o merge |

Esta skill é o checklist que ambos os agents aplicam — a diferença entre eles é o momento e o peso
do veredicto, não o critério de revisão.

## Formato do relatório

**Achados individuais** (um bloco por smell) seguem o padrão abaixo — mesmo formato usado pela
skill `qualidade-codigo-java`, garantindo leitura uniforme entre autor e revisor:

````markdown
**[❌ Código Não Aderente]:**
```java
// (trecho original contendo o code smell)
```

**[🚨 Violação e Explicação]:**
- qual principio foi quebrado (Refactoring Guru, Object Calisthenics, Clean Code for AI)
- impacto tecnico/financeiro (custo de janela de contexto do LLM, manutencao, etc.)

**[✅ Exemplo de Refatoração]:**
```java
// (versao corrigida, aplicando Extract Method / Replace Magic Number / Guard Clauses / etc.)
```
````

**Agrupamento por severidade** (o relatório final entrega os achados acima dentro desta
estrutura):

````markdown
## Revisão de Código: <componente/PR>

### Crítico
- **<título curto>** (`Arquivo.java:42`) — descrição do problema e por que bloqueia o merge.

### Importante
- **<título curto>** (`Arquivo.java:17`) — descrição e sugestão objetiva de correção.

### Menor
- **<título curto>** (`Arquivo.java:5`) — descrição (estilo/nomenclatura/preferência).

### Pontos positivos
- <prática correta observada, para reforçar>

### Evidência
| Verificação | Comando | Resultado |
|---|---|---|
| Compilação | `mvn -q compile` | ok / falhou |
| Testes unitários | `mvn test` | N passaram, M falharam, K pulados |
| Integração | `mvn -Pintegracao verify` | executado / **pendente** (motivo) |
| Carga | `mvn -Pcarga verify` | executado / **pendente** / não aplicável |

### Veredicto (modo auditoria)
APROVADO | REPROVADO (há Crítico) | **PENDENTE** (falta evidência executada para um risco relevante)
````

Omita uma seção inteira se não houver achados nela (não escreva "nenhum encontrado") — exceto
"Pontos positivos", que deve sempre trazer ao menos um item quando algo no código merece ser
reforçado.

> **Coesão com `qualidade-codigo-java`:** esta skill é o "lado passivo" (o **que** revisar com
> checklist e severidades). A skill `qualidade-codigo-java` é o "lado ativo" (o **como**
> aplicar cada refactoring — Extract Method, Replace Magic Number, Tell Don't Ask, etc.).
> Mesmo formato de exemplo, mesmas terminologias (`Magic Number`, `Primitive Obsession`,
> `Guard Clause`, `Tell Don't Ask`), sem sobreposição: o revisor aponta o smell, o autor consulta
> `qualidade-codigo-java` para o passo-a-passo da correção.
