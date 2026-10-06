# Exemplos de revisão — nomenclatura, tipagem e Object Calisthenics

Exemplos movidos de `revisao-de-codigo-java` (SKILL mantém o checklist). Severidade padrão destes itens: **Menor** (estilo/heurística), salvo quando o achado demonstra um risco concreto (ex.: número mágico que diverge entre dois pontos de uma regra de negócio → Importante).

### 5.1. Magic Numbers (Replace Magic Number with Symbolic Constant)

Qualquer literal numérico ou `String` com significado de negócio é proibido no meio de validações,
fórmulas ou `switch`/`if`. Deve virar constante nomeada, enum ou value object.

**[❌ Código Não Aderente]:**
```java
// numeros magicos escondem regra de negocio; agente nao sabe o que 889 ou 50000 significam
if (status == 889) { ... }
if (amount > 50000) { ... }
if (tipoTransacao == 1) { ... }
```

**[🚨 Violação e Explicação]:** o significado de `889`, `50000` e `1` está oculto — o agente tem
que adivinhar a regra de negócio. Se o Banco Central alterar o limite, o agente tem que caçar
`50000` no projeto inteiro (e pode errar um).

**[✅ Exemplo de Refatoração]:**
```java
// constante nomeada ou enum; regra de negocio visivel e grepavel
private static final BigDecimal LIMITE_MAXIMO_PIX_AUTOMATICO = new BigDecimal("50000");
if (amount.compareTo(LIMITE_MAXIMO_PIX_AUTOMATICO) > 0) { ... }
if (tipoTransacao == TipoTransacao.SAQUE) { ... }
```

Quando o número for uma constante matemática universal (0, 1, 100 para percentual) **e** a
intenção for trivial, pode permanecer. A regra se aplica a literais com significado de domínio.

### 5.2. Tipagem explícita

Assinaturas devem ser fortemente tipadas. `Map`, `List`, `Set` sem tipo, `Object`, `String` para
tudo, ou raw types obrigam o agente a inferir tipos a cada leitura e desperdiçam janela de
contexto.

**[❌ Código Não Aderente]:**
```java
// raw types e String para tudo; agente precisa inferir
public Map buscar(String p) { ... }
public void executar(Object p) { ... }
```

**[🚨 Violação e Explicação]:** raw types e `Object` desativam o type checker; o agente precisa
inferir tipos a cada leitura e não recebe proteção contra `ClassCastException` em runtime.

**[✅ Exemplo de Refatoração]:**
```java
// tipos explicitos na assinatura
public Map<AutorizacaoId, Autorizacao> buscarPorFiltro(FiltroAutorizacao filtro) { ... }
public void executar(AutorizacaoParaExpirar autorizacao) { ... }
```

**Interface em vez de implementação concreta** — parâmetro (e retorno, quando fizer sentido) deve
usar o tipo mais genérico que atenda o contrato (`List`, `Map`, `Set`), nunca a implementação
concreta (`ArrayList`, `HashMap`, `HashSet`):

**[❌ Código Não Aderente]:**
```java
// amarra o caller a ArrayList; List.of(...) (imutavel) ou LinkedList exigiriam copia so pra chamar
public void processarClientes(ArrayList<String> customerNames) { ... }
```

**[🚨 Violação e Explicação]:** o método não deveria se importar com a implementação, só com o
contrato (`List`); um caller com `List.of(...)` ou `LinkedList` precisa copiar a coleção só para
satisfazer a assinatura.

**[✅ Exemplo de Refatoração]:**
```java
// aceita qualquer List; caller escolhe a implementacao que fizer sentido
public void processarClientes(List<String> customerNames) { ... }
```

### 5.3. Comentários "por que", não "o que"

Eliminar comentários redundantes que gastam tokens. Preservar (e exigir) comentários de
**proveniência** que explicam a decisão não-óbvia.

**[❌ Código Não Aderente]:**
```java
// ruido que come janela de contexto
// incrementa i
i++;
// verifica se o saldo e maior que zero
if (saldo.compareTo(BigDecimal.ZERO) > 0) { ... }
```

**[🚨 Violação e Explicação]:** `// incrementa i` e `// verifica se o saldo e maior que zero`
são traduções literais do código — `git blame` + nome do método já respondem "o que". Gastam
tokens e atrapalham a leitura do agente.

**[✅ Exemplo de Refatoração]:**
```java
// comentario de proveniencia: explica decisao nao-obvia
// Limite regulado pelo Banco Central na resolucao BCB 123/2024, art. 7o §2o.
// Nao alterar sem alinhamento com compliance.
private static final BigDecimal LIMITE_MAXIMO_PIX_AUTOMATICO = new BigDecimal("50000");
```

> **Regra:** se o `git blame` + nome do método já respondem "o que", o comentário é redundante.
> Se a regra veio de um ofício, um ADR, ou um workaround de bug antigo, **esse** comentário
> precisa existir.

### 6. Complexidade

**Tamanho de método:** 4-20 linhas. Acima disso, extrair (`Extract Method`) — métodos longos
escondem a lógica, são a raiz de todo mal em revisão, e fazem o agente perder o fio da execução
entre cláusulas.

**Tamanho de arquivo:** 300-500 linhas no máximo. Acima disso, dividir por responsabilidade.
Arquivos muito grandes são truncados em diffs e na leitura do agente, perdendo contexto de borda
(imports, anotações, assinaturas).

**Aninhamento máximo:** 3 níveis. Acima disso, usar **guard clauses** (early return). O `else` é
**proibido** — use a forma positiva da guarda (`if (!condicao) return;`) para manter o corpo do
método no mesmo nível de indentação.

> **Impacto na janela de contexto do LLM:** cada nível de indentação extra multiplica o
> custo cognitivo de rastrear o estado da execução. Métodos longos + aninhamento profundo são
> a principal causa de alucinação do agente em revisão ("achou que o código entrava no `else` mas
> entrou no `if` interno").

**[❌ Código Não Aderente]:**
```java
// aninhamento profundo (4 niveis), else explicito, metodo longo
public void processar(Pedido pedido) {
    if (pedido != null) {
        if (pedido.itens() != null) {
            if (!pedido.itens().isEmpty()) {
                if (pedido.valor().signum() > 0) {
                    executar(pedido);
                } else {
                    throw new BusinessException("valor invalido");
                }
            } else {
                throw new BusinessException("sem itens");
            }
        } else {
            throw new BusinessException("itens nulos");
        }
    }
}
```

**[🚨 Violação e Explicação]:**
1. **Aninhamento profundo (4 níveis)** — pirâmide de `if/else` torna impossível seguir o fluxo
   principal sem perder o estado.
2. **Else explícito** — quando o `if` retorna/throw, o `else` é ruído.
3. **Método longo** — excede 20 linhas; viola regra de janela de contexto.

**[✅ Exemplo de Refatoração]:**
```java
// guard clauses, corpo achatado, metodo cabe em uma "respira"
public void processar(Pedido pedido) {
    if (pedido == null || pedido.itens() == null || pedido.itens().isEmpty()) {
        return;
    }
    if (pedido.valor().signum() <= 0) {
        throw new BusinessException("valor invalido");
    }
    executar(pedido);
}
```

### 6.1. Tell, Don't Ask (sem getters + setters para o dominio)

O código cliente **não deve** perguntar o estado interno de um objeto para tomar uma decisão
por ele — a própria classe deve expor métodos comportamentais que realizam a ação com seus
próprios dados. Getters/setters em objetos de domínio (não DTOs de borda) produzem **domínio
anêmico** e regras de negócio espalhadas.

**[❌ Código Não Aderente]:**
```java
// servico puxa saldo, faz matematica externa, e devolve o resultado
public void processarSaque(Conta conta, BigDecimal valor) {
    if (conta.getSaldo().compareTo(valor) >= 0) {
        conta.setSaldo(conta.getSaldo().subtract(valor));
    } else {
        throw new BusinessException("saldo insuficiente");
    }
}
```

**[🚨 Violação e Explicação]:** a classe `Conta` é um saco de dados; a regra "saque" vive no
Service, espalhada. Concorrência: dois saques simultâneos podem ler o mesmo saldo e causar
lost update. Agente precisa ler 2 arquivos para entender uma única regra.

**[✅ Exemplo de Refatoração]:**
```java
// Conta encapsula a regra; o servico apenas delega
public void processarSaque(Conta conta, BigDecimal valor) {
    conta.sacar(valor);   // lanca BusinessException internamente se a regra falhar
}
```

> **Exceção (não marque como achado):** DTOs de borda (request/response) **precisam** de getters
> para serialização. A regra vale para entidades e value objects de domínio. Ver
> `qualidade-codigo-java` seção "Tell, Don't Ask" para o passo-a-passo.

### 6.2. Primitive Obsession (encapsular em value objects/records)

Não use tipos primitivos para representar conceitos com comportamento ou validação próprios:
`double`/`BigDecimal` solto para dinheiro, `long`/`int` para documentos, `int`/`long` para
ranges, `String` para "0-889". Cada um desses merece um value object (record) que carrega a
validação e o comportamento.

**[❌ Código Não Aderente]:**
```java
// primitivo carrega semantica; agente nao sabe o que validar
public void purgarParticao(String particaoStr, String rangeStr) {
    int inicio = Integer.parseInt(rangeStr.split("-")[0]);
    int fim = Integer.parseInt(rangeStr.split("-")[1]);
    int part = Integer.parseInt(particaoStr);
    if (part >= inicio && part <= fim) { ... }
}
```

**[🚨 Violação e Explicação]:** `String` carregando semântica de range; parsing repetido em todo
lugar; validação frágil (`split("-")` quebra com `"900-999-1000"`). Agente precisa inferir
formato e regra a cada leitura.

**[✅ Exemplo de Refatoração]:**
```java
// value objects carregam a regra e o parsing
public void purgarParticao(PartitionId partition, PurgeRange range) {
    if (range.contains(partition)) { ... }
}
```

> **Exemplos de encapsulamento obrigatório neste catálogo:** `Money` (valor + moeda), `Cpf`,
> `Cnpj`, `AutorizacaoId`, `IdContrato`, `PartitionId`, `PurgeRange`,
> `PeriodoVigencia` (início + fim).

### 6.3. Bloaters e Change Preventers (centralização de mudança)

Se uma única alteração de regra de negócio exige editar 20 arquivos diferentes, o código está
mal distribuído. Centralize configurações e isole escopo por injeção de dependência.

- **Primitive Obsession** (ver 6.2) — quando a regra de domínio está no `if` solto, mudar a regra
  exige varrer o projeto.
- **Shotgun Surgery** — uma feature nova precisa tocar 5 classes? Falta um *aggregate root* ou
  um *use case* que concentre a operação.
- **Divergent Change** — uma classe muda por motivos não-relacionados? Extrair por
  responsabilidade (SRP).
- **Configuração dispersa** — `@Value("${limite.pix}")` espalhado por 10 arquivos? Mover para
  um `@ConfigurationProperties` único.

### 6.4. First Class Collections (Object Calisthenics)

Classe que contém uma coleção não deve conter **outras** variáveis de membro; extraia uma classe
dedicada para agrupar comportamento de filtro/agrupamento sobre essa coleção.

**[❌ Código Não Aderente]:**
```java
// colecao misturada com outro atributo: comportamento de filtro polui a classe hospedeira
public class Empresa {
    private String razaoSocial;
    private List<Funcionario> funcionarios;

    public List<Funcionario> buscarContadores() {
        return funcionarios.stream()
                .filter(f -> f.getCargo().equals("contador"))
                .toList();
    }
}
```

**[🚨 Violação e Explicação]:** os comportamentos de filtro/agrupamento de `funcionarios` poluem
`Empresa`; o acoplamento entre a coleção e a classe hospedeira dificulta evolução e teste isolado.

**[✅ Exemplo de Refatoração]:**
```java
// First Class Collection: comportamento sobre a colecao tem um lar proprio
public class QuadroFuncionarios {
    private final List<Funcionario> funcionarios;

    public QuadroFuncionarios(List<Funcionario> funcionarios) {
        this.funcionarios = funcionarios;
    }

    public List<Funcionario> buscarContadores() {
        return funcionarios.stream()
                .filter(f -> f.getCargo().equals("contador"))
                .toList();
    }
}
```

### 6.5. One Dot Per Line / Law of Demeter (Object Calisthenics)

Evite cadeias de chamadas que atravessam vários objetos (`a.getB().getC().getD()`) — o objeto
intermediário passa a saber demais sobre a estrutura interna dos outros.

**[❌ Código Não Aderente]:**
```java
// navegando a estrutura interna: quebra de encapsulamento
String nomeChefe = funcionario.getDepartamento().getChefe().getNome();
```

**[🚨 Violação e Explicação]:** a estrutura interna `Funcionario -> Departamento -> Chefe` fica
exposta; qualquer mudança nessa hierarquia quebra todos os call sites.

**[✅ Exemplo de Refatoração]:**
```java
// o objeto expressa a intencao via metodo comportamental direto
String nomeChefe = funcionario.getNomeChefeDepartamento();
```

> **Exceção (não marque como achado):** DTOs flattenizados de borda (`endereco.cidade.uf`) e
> fluent builders encadeados são aceitáveis — a regra vale para chamadas de **comportamento**
> que atravessam domínios.

### 6.6. No Classes With More Than Two Instance Variables (Object Calisthenics)

Classes de domínio com mais de duas variáveis de instância geralmente acumulam mais de uma razão
para mudar (SRP ferida); agrupe os campos relacionados em objetos menores.

**[❌ Código Não Aderente]:**
```java
public class Funcionario {
    private String nome;
    private int idade;
    private String cargo;
    private String departamento;
}
```

**[🚨 Violação e Explicação]:** a classe mistura informações pessoais e contratuais; mudar uma
exige mexer na mesma classe que cuida da outra.

**[✅ Exemplo de Refatoração]:**
```java
public class Funcionario {
    private InformacoesPessoais dadosPessoais;   // agrupa nome e idade
    private InformacoesTrabalho dadosTrabalho;   // agrupa cargo e departamento
}
```

> **Exceção (não marque como achado):** entidades JPA e DTOs de borda naturalmente carregam
> vários campos — a regra vale para agregados, value objects e serviços de domínio.

