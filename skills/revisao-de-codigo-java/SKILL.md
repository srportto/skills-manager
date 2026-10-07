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

## Quando usar

Checklist único de revisão de código Java/Spring Boot, organizado por severidade. Consolida três
fontes: princípios de clean code (DRY/KISS/YAGNI) e contrato de API, padrões de código Java
(nomenclatura, imutabilidade, `Optional`, streams) e categorias de revisão de um revisor genérico
(correção, testes, complexidade). Use sempre que for revisar um diff, uma classe, um PR, ou logo
depois de gerar código Java significativo.

## Quando NÃO usar

Para dúvida pontual sobre em qual camada um código deve viver, use
`arquitetura-limpa-java` diretamente (esta skill só referencia o checklist dela no grupo
"Arquitetura"). Para revisar somente o padrão de logs, use `monitoramento-java` (`references/logs-*.md`). Esta skill é a
fonte de verdade usada tanto para autorrevisão quanto pelos agents `java-revisor` e
`java-revisor` (modo `auditoria`) — veja "Quem revisa o quê" abaixo para saber qual agent invocar.

## Entradas

- Escopo da revisão: diff, classe, PR ou arquivos listados.
- Intenção da mudança (o que faz e por quê) e, se existir, saída de build/testes já executados.
- Modo: autorrevisão, `tempestivo` ou `auditoria` (veja "Quem revisa o quê").

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

### Decisão: tabela-resumo dos 10 grupos

Cada linha resume o item e a severidade usual; o detalhe (regra + antes/depois) está na reference ligada.

| # | Grupo | O que verificar (uma linha) | Severidade usual | Detalhe |
|---|---|---|---|---|
| 1 | Correção | `Optional.get()` sem checagem, exceção sem causa/contexto, recurso sem try-with-resources | Crítico | [checklist-correcao](references/checklist-correcao.md) |
| 2 | Contrato HTTP | 400 validação / 422 negócio / 500 técnico; DTO de borda imutável | Crítico | [checklist-contrato-http](references/checklist-contrato-http.md) |
| 3 | Imutabilidade | records para dados, `final`, sem setters desnecessários | Menor (Importante se quebra invariante) | [checklist-estilo](references/checklist-estilo.md) |
| 4 | Streams | pipeline curto, sem efeito colateral; loop quando for mais claro | Menor | [checklist-estilo](references/checklist-estilo.md) |
| 5 | Nomenclatura | nomes grepáveis, sem `Handler`/`Util`/`Manager` genéricos, unidade no parâmetro | Menor | [checklist-estilo](references/checklist-estilo.md) |
| 6 | Complexidade | método longo, arquivo grande, aninhamento > 3 níveis | Menor (Importante se esconde bug) | [checklist-estilo](references/checklist-estilo.md) |
| 6.1 | Heurísticas de design | magic numbers, Tell Don't Ask, Primitive Obsession, Demeter | Menor | [checklist-estilo](references/checklist-estilo.md), [exemplos](references/exemplos-revisao-java.md) |
| 7 | DRY com bom senso | extrair na 3ª ocorrência; sem abstração especulativa | Menor | [checklist-estilo](references/checklist-estilo.md) |
| 8 | Testes | feliz + borda + erro, nomes descritivos, sem dependência de ordem; evidência executada | Importante / Crítico sem prova | [checklist-testes-resiliencia](references/checklist-testes-resiliencia.md) |
| 8.1 | Resiliência | fila/espera/retry sem limite, ack antes do efeito, sem idempotência, fallback mentiroso | Crítico | [checklist-testes-resiliencia](references/checklist-testes-resiliencia.md) |
| 9 | Logs | sem dado sensível, com contexto, nível coerente | Importante | [checklist-logs-arquitetura](references/checklist-logs-arquitetura.md) |
| 10 | Arquitetura | camada correta, dependência para dentro, recurso externo via `port/out` | Importante / Crítico | [checklist-logs-arquitetura](references/checklist-logs-arquitetura.md) |

## Passo a passo

- [ ] Entender a intenção da mudança (Fluxo, passo 1).
- [ ] Passar os 10 grupos da tabela, abrindo só a reference do grupo que o diff toca.
- [ ] Classificar cada achado por severidade, com `arquivo:linha` e o porquê.
- [ ] Conferir a evidência executada (compilação, unitários, integração, carga) — teste não executado é pendência.
- [ ] Montar o relatório no formato de [assets/relatorio-revisao.md](assets/relatorio-revisao.md).

## Quem revisa o quê

| Situação | Quem revisa | Papel |
|---|---|---|
| Diff pontual durante o desenvolvimento (uma classe, um método, um PR pequeno) | agent `java-revisor` | Revisão **tempestiva** — feedback rápido durante o trabalho, aplicando este checklist |
| Pré-merge, mudança grande, ou revisão do trabalho de outro agent (ex.: saída do `java-construtor`) | agent `java-revisor` (modo `auditoria`) | **Veredicto final** — achados Críticos bloqueiam o merge |

Esta skill é o checklist que ambos os agents aplicam — a diferença entre eles é o momento e o peso
do veredicto, não o critério de revisão.

## Saída

Relatório por severidade (Crítico / Importante / Menor), pontos positivos, tabela de evidência e, no modo
`auditoria`, veredicto APROVADO | REPROVADO | PENDENTE. Use o modelo de
[assets/relatorio-revisao.md](assets/relatorio-revisao.md) (achado individual `❌ / 🚨 / ✅` + agrupamento).

Omita uma seção inteira se não houver achados nela (não escreva "nenhum encontrado") — exceto
"Pontos positivos", que deve sempre trazer ao menos um item quando algo no código merece ser
reforçado.


## Validação

- Todo achado tem `arquivo:linha`, severidade e motivo; nenhum Crítico foi rebaixado sem justificativa.
- O veredicto só é APROVADO com evidência executada para os riscos relevantes; build com `-DskipTests` não aprova.
- Heurísticas de estilo foram reportadas como **Menor**, salvo risco concreto demonstrado.

## Gotchas

- Decisão consciente do autor (ex.: loop em vez de stream) não é achado: confirme a intenção antes.
- Não exija breaker, reatividade ou broker sem dependência/carga que justifique (proporcionalidade).
- `assertEquals`/`assertThrows` (JUnit 5 puro) não é achado só por não ser AssertJ.
- Omitir seção vazia do relatório; "Pontos positivos" sempre que houver algo a reforçar.

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [checklist-correcao](references/checklist-correcao.md) | Grupo 1: `Optional`, exceções, try-with-resources |
| [checklist-contrato-http](references/checklist-contrato-http.md) | Grupo 2: status por origem do erro, DTO de borda |
| [checklist-estilo](references/checklist-estilo.md) | Grupos 3–7: imutabilidade, streams, nomes, complexidade, heurísticas, DRY |
| [checklist-testes-resiliencia](references/checklist-testes-resiliencia.md) | Grupo 8 e 8.1: testes, evidência executada, resiliência e limites |
| [checklist-logs-arquitetura](references/checklist-logs-arquitetura.md) | Grupos 9–10: logs e camadas |
| [exemplos-revisao-java](references/exemplos-revisao-java.md) | Magic numbers, tipagem, complexidade e Object Calisthenics com antes/depois |
| [assets/relatorio-revisao.md](assets/relatorio-revisao.md) | Modelo do relatório final |

## Quem aplica o quê

Quem **revisa** (autorrevisão, `java-revisor` tempestivo ou em modo `auditoria`) está em "Quem revisa o quê"; quem **corrige** consulta `qualidade-codigo-java` e `refatorador-java`.
> **Coesão com `qualidade-codigo-java`:** esta skill é o "lado passivo" (o **que** revisar com
> checklist e severidades). A skill `qualidade-codigo-java` é o "lado ativo" (o **como**
> aplicar cada refactoring — Extract Method, Replace Magic Number, Tell Don't Ask, etc.).
> Mesmo formato de exemplo, mesmas terminologias (`Magic Number`, `Primitive Obsession`,
> `Guard Clause`, `Tell Don't Ask`), sem sobreposição: o revisor aponta o smell, o autor consulta
> `qualidade-codigo-java` para o passo-a-passo da correção.
