# Checklist 3–7 — Estilo (imutabilidade, streams, nomenclatura, complexidade, heurísticas, DRY)

Leia este arquivo ao revisar estilo e manutenibilidade: setters e estado mutável, pipelines de stream, nomes, método longo, heurísticas de design e duplicação. Severidade padrão: **Menor** (veja as exceções em cada item). Os exemplos antes/depois completos vivem em `qualidade-codigo-java`; aqui cada item traz a regra, a severidade e o link.

### 3. Imutabilidade

Records para dados, `final` em campos, sem setters desnecessários.

Exemplo antes/depois (setters públicos vs. record/campos `final`): [imutabilidade](../../qualidade-codigo-java/references/imutabilidade-optional-streams.md) (seção "Imutabilidade"). Fonte executável: [Pagamento](../../../examples/java/linguagem/src/main/java/br/com/srportto/exemplos/Pagamento.java) e [PagamentoTest](../../../examples/java/linguagem/src/test/java/br/com/srportto/exemplos/PagamentoTest.java).

### 4. Streams

Pipelines curtos, sem efeitos colaterais; loop quando for mais claro.

Exemplo antes/depois (`forEach` com efeito colateral vs. pipeline puro): [imutabilidade, Optional e streams](../../qualidade-codigo-java/references/imutabilidade-optional-streams.md) (seção "Streams").

Quando o pipeline exigiria múltiplos `flatMap`/estado acumulado só para simular um `for`, prefira o
loop explícito — clareza vale mais que "tudo em stream".

### 5. Nomenclatura

`PascalCase` para tipos, `camelCase` para métodos/campos, `UPPER_SNAKE_CASE` para constantes; nomes
que revelam intenção **e são grepáveis** (fáceis de localizar com `ripgrep`/`grep`). Use português
ou inglês, mas seja consistente com o restante do projeto — não misture os dois no mesmo
pacote/classe.

Exemplo antes/depois (`Handler`/`N` vs. `AutorizacaoExpiradaHandler`/`TAMANHO_MAXIMO_PAGINA`): [nomenclatura](../../qualidade-codigo-java/references/nomenclatura.md).

**Proibidos** (nomes genéricos que poluem a busca lexical e escondem a intenção):
`Handler`, `Manager`, `Helper`, `Util`, `Data`, `Process`, `Service` sem qualificador
de domínio, `Info`, `Common`, `Base`. Use nomes de domínio: `AutorizacaoCommandService`,
`PixBufferRingPartitionPurgeManager` é aceitável **quando** o domínio é esse, mas
`Manager<Algo>` sozinho não é.

**Parâmetros de método** seguem a mesma regra "Don't Abbreviate" — sem sigla, e com a unidade de
medida explícita no nome quando o valor for numérico ou temporal:

Exemplo antes/depois (`fn`/`amt`/`timeout` vs. `firstName`/`transactionAmount`/`timeoutInMilliseconds`): [nomenclatura](../../qualidade-codigo-java/references/nomenclatura.md) (seção "Parâmetros de método ricos e não abreviados").

> Grupo de parâmetros que sempre viaja junto (ex.: `latitude`/`longitude`) vira value object
> (`Coordinate`) — ver 6.2 (Primitive Obsession) e `qualidade-codigo-java` seção "Introduce
> Parameter Object". Parâmetro restrito a um conjunto conhecido de valores (`String status`,
> `int tipo`) vira `enum` — ver 5.1 (Magic Numbers).

### 6. Complexidade

Método longo (referência: 4–20 linhas), arquivo grande (300–500 linhas) e aninhamento acima de 3 níveis
dificultam leitura humana e do agente: sugira `Extract Method` e guard clauses. São **Importante** quando a
complexidade esconde um bug ou impede testar o fluxo; caso contrário, **Menor**. Exemplos em
[exemplos de revisão](exemplos-revisao-java.md#6-complexidade).

### 6.1. Heurísticas de design (Menor por padrão)

Magic numbers, tipagem explícita, comentários "por que", Tell Don't Ask, Primitive Obsession, Bloaters,
First Class Collections, Lei de Demeter e "no máximo dois atributos" são **heurísticas**: reporte como
**Menor**, salvo quando o achado mostra um risco concreto (ex.: o mesmo limite de negócio escrito com valores
diferentes em dois lugares → Importante). Quantidade de atributos, estilo de asserção ou ausência de um
pattern **não** são falha funcional. Exemplos e explicações completas:
[exemplos de revisão](exemplos-revisao-java.md); como aplicar: `qualidade-codigo-java`.

### 7. DRY com bom senso

Extraia duplicação real; não abstraia prematuramente — regra das 3 ocorrências (na 1ª e 2ª vez,
duplicar pode ser mais barato que a abstração errada; extraia quando a 3ª ocorrência confirmar o
padrão). Não crie uma `EmailValidator` com interface e implementação única "para o futuro" — isso é
abstração especulativa (veja `padroes-de-projeto-java`, seção "Quando NÃO aplicar pattern"); um
método privado já resolve a duplicação real.


Exemplo antes/depois (validação de e-mail triplicada vs. método privado único): [princípios de clean code](../../qualidade-codigo-java/references/clean-code-principios.md) (seção DRY).
