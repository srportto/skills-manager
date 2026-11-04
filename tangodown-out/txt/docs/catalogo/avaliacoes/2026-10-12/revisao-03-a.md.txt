# revisao-de-codigo-java eval 3 — execução A

Executor independente: subagente `java-revisor`, modo `tempestivo` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.

Prompt: "Revise: a interface MeioPagamento tem autorizar, estornar, parcelar e agendar; Pix implementa parcelar lançando UnsupportedOperationException e o ParcelamentoService recebe MeioPagamento."

## Saída bruta

### Revisão (modo tempestivo): `MeioPagamento`, `Pix` e `ParcelamentoService`

Não alterei nenhum arquivo. O código não veio anexado, então os caminhos e as linhas abaixo são estimativas a partir da sua descrição. A severidade e as correções seguem o item 6.7 de `skills/revisao-de-codigo-java/references/checklist-estilo.md` (contrato de subtipo, LSP e ISP) e as seções LSP e ISP de `skills/qualidade-codigo-java/references/solid.md`.

#### Crítico

##### 1. `Pix.parcelar` lança `UnsupportedOperationException` e o `ParcelamentoService` consegue chamar esse método
- **Onde:** `domain/model/Pix.java:~30`, no `parcelar(...)` que faz `throw new UnsupportedOperationException()`, combinado com `application/usecase/ParcelamentoService.java:~15`, no campo ou parâmetro do tipo `MeioPagamento`.
- **Risco:** a interface `MeioPagamento` promete parcelamento a todos os meios de pagamento, mas o Pix não cumpre. Isso viola o LSP. A causa é uma interface "gorda", o que viola o ISP: o Pix é obrigado a implementar um método que não suporta.
- **Por que é Crítico e não Importante:** o checklist sobe para Crítico quando um caminho real de produção alcança a chamada. É o seu caso. Como `ParcelamentoService` recebe `MeioPagamento`, o compilador aceita um Pix ali, e nada no tipo impede isso.
- **Cenário que quebra:** um checkout chega com `meio = PIX` e `parcelas = 3`. O serviço resolve o `MeioPagamento` e chama `parcelar(...)`. A `UnsupportedOperationException`, que é uma `RuntimeException` técnica, sobe até o `ApiExceptionHandler` e o cliente recebe 500. Pelo contrato HTTP, uma regra de negócio violada deveria responder 422 com o layout de erro de negócio. Além disso, o erro só aparece em runtime: nenhum teste de compilação ou de tipo pega o problema.
- **Correção esperada (`java-construtor` ou `refatorador-java`):**
  1. Trocar a interface única por interfaces de capacidade: `Autorizavel`, `Estornavel`, `Parcelavel` e `Agendavel`.
  2. `Pix implements Autorizavel, Estornavel, Agendavel`, sem `parcelar`.
  3. `Cartao implements Autorizavel, Estornavel, Parcelavel` (e o que mais ele suportar).
  4. O `ParcelamentoService` passa a depender de `Parcelavel`. Assim o tipo garante a capacidade e não sobra caminho para o erro.
  5. Se o meio é escolhido em runtime a partir da requisição (Strategy por lista injetada), injetar `List<Parcelavel>`. Quando nenhum candidato servir, lançar uma exceção de negócio com contexto, por exemplo `MeioPagamentoNaoParcelavelException(tipo)`, que vira 422. Não usar `orElseThrow()` sem mensagem.
- **Correções que não aceito:**
  - Trocar o `throw` por um override vazio ou que devolva `List.of()`. A falha deixa de ser ruidosa e passa a ser silenciosa: um parcelamento "concluído" que não aconteceu.
  - Colocar `if (meio instanceof Pix)` no `ParcelamentoService`. Isso espalha o conhecimento do subtipo e quebra o OCP.

#### Importante

##### 2. Verificar se os outros métodos da interface têm o mesmo defeito
- **Onde:** as outras implementações de `MeioPagamento` (por exemplo `Boleto.java` e `Cartao.java`), nos métodos `estornar` e `agendar`.
- **Risco:** se uma interface obrigou o Pix a lançar exceção, é provável que outro meio tenha `estornar` ou `agendar` lançando exceção ou vazio. O caso mais grave seria um `estornar` vazio, que engole um efeito de negócio: o checklist trata override vazio em estorno como Crítico.
- **Correção:** fazer um grep nas implementações por `UnsupportedOperationException` e por overrides com corpo vazio, e aplicar a mesma separação por capacidade.

##### 3. Falta um teste que prove o caminho de erro
- **Onde:** `ParcelamentoServiceTest.java`, que hoje não existe ou cobre só o caminho feliz.
- **Risco:** nenhum teste falharia se o Pix voltasse a chegar no parcelamento.
- **Correção:**
  - Depois da refatoração, o compilador já é a prova principal: um Pix não compila como `Parcelavel`.
  - Se houver seleção em runtime, incluir um teste em que a requisição com Pix recebe `MeioPagamentoNaoParcelavelException` (ou 422 no teste de contrato HTTP), além do caminho feliz com Cartão.
  - O código já existe, então quebre o invariante de propósito e confirme que o teste falha.

#### Menor

##### 4. Não fatiar mais do que o necessário
A divisão em quatro capacidades se justifica porque há variação real entre os meios: o Pix não parcela e o Cartão parcela. Uma interface coesa, que todos os clientes usam por inteiro, não deve ser fatiada só por tamanho. O ISP resolve dependência de método que o cliente não usa, não o tamanho da interface. Também não precisa de factory dedicada: Strategy por lista injetada ou `sealed` com `switch` resolve, conforme o conjunto de meios seja aberto ou fechado.

#### Pontos positivos
- O Pix falha alto com uma exceção em vez de um override vazio. Isso deixou o defeito visível e é melhor do que uma falha silenciosa.
- O `ParcelamentoService` já depende de uma abstração e não do tipo concreto. Falta só a abstração ser a certa (`Parcelavel`).
- O conjunto de operações (autorizar, estornar, parcelar, agendar) está bem nomeado e já mostra as capacidades em que a interface deve ser dividida.

#### Evidência

| Item | Situação |
|---|---|
| Compilação | **Pendente**: não recebi a saída do build |
| Testes unitários | **Pendente**: falta o teste do caminho Pix no parcelamento (achado 3) |
| Integração | Não se aplica: a mudança não envolve broker, banco ou cache |
| Carga | Não se aplica |

O achado 1 é Crítico e bloqueia a mudança. Depois que o `java-construtor` ou o `refatorador-java` corrigir, chame este agent de novo no mesmo modo.

**Referências usadas:**
- `skills/revisao-de-codigo-java/references/checklist-estilo.md` (item 6.7)
- `skills/qualidade-codigo-java/references/solid.md` (seções L e I)
