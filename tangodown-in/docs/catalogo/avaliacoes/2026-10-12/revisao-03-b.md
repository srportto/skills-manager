# revisao-de-codigo-java eval 3 — execução B

Executor independente: subagente `java-revisor`, modo `tempestivo` (sessão isolada, sem escrita em arquivo). Avaliador: agente principal.

Prompt: "Revise: a interface MeioPagamento tem autorizar, estornar, parcelar e agendar; Pix implementa parcelar lançando UnsupportedOperationException e o ParcelamentoService recebe MeioPagamento."

## Saída bruta

### Revisão tempestiva: `MeioPagamento` / `Pix` / `ParcelamentoService`

**Escopo:** só o que foi descrito. O código não veio anexado, então os nomes de arquivo e os números de linha abaixo são suposições. Antes de agir, confira-os no diff real.

**Critério usado:** grupo 6.7 do checklist (contrato de subtipo LSP/ISP, em `skills/revisao-de-codigo-java/references/checklist-estilo.md`) e a correção descrita em `skills/qualidade-codigo-java/references/solid.md` (seções L e I). Não alterei nenhum arquivo.

#### Crítico (condicional)

❌ **`ParcelamentoService` aceita um tipo que não garante a capacidade de parcelar.**
`application/usecase/ParcelamentoService.java:~15` (campo ou parâmetro `MeioPagamento`) combinado com `infrastructure/external/pix/Pix.java:~40` (`parcelar` → `throw new UnsupportedOperationException()`).

- **Risco:** o compilador aceita entregar `Pix` ao `ParcelamentoService`. O erro só aparece em runtime.
- **Quando isso acontece:** o meio de pagamento é escolhido a partir da requisição (por exemplo, `formaPagamento=PIX` com `parcelas=3`), ou vem de uma Strategy por lista injetada que resolve `MeioPagamento` por tipo. Nesse caso `parcelar` estoura. A `UnsupportedOperationException` não é exceção de negócio, então o `ApiExceptionHandler` devolve **500** em vez de **422**. O resultado é contrato HTTP quebrado, alerta de erro técnico disparando e nenhuma mensagem útil para o cliente.
- **Severidade:** é **Crítico** se existir caminho real de produção em que um `Pix` chega ao `ParcelamentoService` (regra do 6.7). Se toda chamada filtrar PIX antes, cai para **Importante**: hoje funciona, mas o filtro é a única proteção e o tipo não o garante. Verifique quem constrói ou resolve o `MeioPagamento` passado ao serviço.

🚨 **Correção esperada (ISP → elimina a violação de LSP):**
```java
// interface de capacidade: só quem parcela implementa
public interface Parcelavel {
    List<Parcela> parcelar(Pagamento pagamento, int quantidadeParcelas);
}

public final class Cartao implements MeioPagamento, Parcelavel { /* ... */ }
public final class Pix implements MeioPagamento { /* sem parcelar */ }

// o tipo do campo garante a capacidade; não sobra caminho até o erro
public class ParcelamentoService {
    private final Parcelavel parcelavel;
}
```
- Na borda, "PIX parcelado" vira **regra de negócio explícita**: uma `BusinessException` (por exemplo, `MeioPagamentoNaoParcelavelException`) que o handler central converte em **422**. Não deixe uma exceção técnica vazar.
- Se `MeioPagamento` é uma `port/out`, a porta deve expressar o que o caso de uso **precisa**, não tudo o que o adapter sabe fazer.

**Correções que não devem ser aceitas:**
- **Trocar o `throw` por override vazio ou `List.of()`.** Vira falha silenciosa: um pagamento "parcelado" que não foi parcelado.
- **Pôr `if (meio instanceof Pix)` no `ParcelamentoService`.** Espalha o conhecimento do subtipo e quebra OCP. Cada meio novo que não parcela exige editar o serviço.
- **Capturar `UnsupportedOperationException` no serviço para traduzir o erro.** Isso trata o sintoma, e o tipo continua mentindo.

Quem corrige: `refatorador-java`, ou `java-construtor` se fizer parte de uma feature em andamento.

#### Importante

❌ **Interface "gorda" com quatro capacidades independentes.** `domain/port/out/MeioPagamento.java:~8-14`.
- **Risco:** todo meio novo (boleto, por exemplo) é obrigado a implementar `parcelar` e `agendar`, mesmo que não os suporte. Isso repete o padrão do Pix. Os mocks de teste ficam grandes, e mudar a assinatura de `parcelar` mexe em quem só usa PIX.
- 🚨 **Proporcionalidade:** extraia só as capacidades que de fato variam entre os meios. Hoje é `Parcelavel`. Extraia `Agendavel` apenas se algum meio não agendar. Se todos os meios autorizam e estornam, `autorizar` e `estornar` podem ficar juntos em `MeioPagamento`. ISP resolve dependência de método inútil, não tamanho de interface. Não fatie em quatro interfaces sem uma segunda variação à vista.
- **Alternativa válida:** se o conjunto de meios é fechado e estável, um `sealed interface MeioPagamento permits Pix, Cartao, ...` com `switch` exaustivo sem `default` faz o compilador apontar cada ponto a ajustar quando entrar um meio novo (`java-moderno/references/sealed-e-switch.md`). Escolha um dos dois caminhos (interfaces de capacidade ou sealed) e não misture.

❌ **Falta prova da regra "PIX não parcela".** Teste do serviço ou da borda (local a confirmar).
- Depois da correção, `ParcelamentoService` recebendo `Pix` deixa de compilar, e essa é a prova estrutural. Ainda falta provar o comportamento na borda: um teste de contrato (`@WebMvcTest` ou o `HttpClient` do teste de contrato) com "PIX + parcelas > 1" deve responder **422** com o corpo de erro de negócio, e não 500. Hoje, sem esse teste, uma regressão para 500 passaria em silêncio.

#### Menor

- **Nomenclatura:** se `parcelar(..., int quantidadeParcelas)` aceita 0 ou negativo, valide no construtor compacto de um value object (`QuantidadeParcelas`) ou com uma guard clause. É uma heurística (Primitive Obsession), não um bug.

#### Pontos positivos

- ✅ O `Pix` falha de forma **ruidosa** (`throw`), e não silenciosa (override vazio). O defeito aparece em vez de esconder um pagamento não parcelado. Isso torna a refatoração segura.
- ✅ O serviço depende de uma abstração (`MeioPagamento`), não de classes concretas (DIP correto). O ajuste é só estreitar essa abstração para a capacidade que o serviço usa.

#### Evidência

| Item | Status |
|---|---|
| Compilação | **pendente** (nenhuma saída de build informada) |
| Unitários | **pendente** |
| Contrato de borda (PIX parcelado → 422) | **pendente**: o teste não existe |
| Integração / carga | não se aplica a esta mudança |

Sugestão de comando depois da correção: `mvn clean verify`, com `Skipped: 0` no relatório. No modo tempestivo não há veredicto formal. Se quiser o bloqueio de merge, reinvoque em modo `auditoria` com o diff real.
