# Avaliação dos casos de SOLID — 2026-10-12

Casos avaliados: `qualidade-codigo-java` 4 e 5 e `revisao-de-codigo-java` 3, criados com a reference
`skills/qualidade-codigo-java/references/solid.md` e o item 6.7 do checklist de revisão. Cada caso teve duas
execuções independentes. Os executores foram subagentes `refatorador-java` (qualidade) e `java-revisor` em modo
`tempestivo` (revisão), sem escrita em arquivo. A pontuação foi feita pelo agente principal, que não produziu as
respostas. Rubrica de [avaliacoes-agents.md](../../avaliacoes-agents.md).

| Caso | Decisão verificada | A | B |
|---|---|---:|---:|
| qualidade 4 — subtipo que lança `UnsupportedOperationException` | Nomeia LSP; separa `Conta`/`ContaMovimentavel`; `pagarFatura` recebe o tipo que garante `sacar`; rejeita override vazio e `instanceof`; testes antes/depois | 2 | 2 |
| qualidade 5 — "aplique SOLID" mecanicamente (rodada inicial) | Recusa interface por classe e classe por método, só aceita porta na fronteira e pede o sintoma; **não explicita** que SRP mede motivo de mudança | 1* | 1* |
| qualidade 5 — reteste após ajuste do `SKILL.md` | Mesma decisão, agora com "SRP significa uma razão para mudar, não um método por classe" | 2 | 2 |
| revisão 3 — interface gorda + `Pix.parcelar` lançando exceção | Identifica ISP + LSP; Crítico porque `ParcelamentoService` alcança a chamada (B: Crítico condicional, com a verificação que decide); interfaces de capacidade; rejeita override vazio, `instanceof` e captura da exceção | 2 | 2 |
| **Total (com reteste)** |  | **6/6** | **6/6** |

\* Substituída no total pelo reteste, como no A01 de 2026-10-07.

**Resultado:** 12/12 pontos com o reteste, sem notas 0.

**Achado da rodada inicial:** nas duas execuções do caso 5, o executor leu só o `SKILL.md`. A tabela de decisão não
tinha linha para o pedido "aplicar SOLID" sem sintoma, então `solid.md` não foi aberto e a expectativa 1 ("SRP mede
motivo de mudança, não quantidade de métodos") ficou implícita. Correção: nova linha na tabela de decisão de
`qualidade-codigo-java` apontando para a seção "Quando NAO aplicar" de `solid.md`. No reteste, os dois executores
explicitaram o critério.

**Observações sem efeito na nota:**
- Revisão 3, execução B: classificou como "Crítico (condicional)", caindo para Importante se toda chamada filtrar
  PIX antes. Isso segue a regra do item 6.7 e indica a verificação que resolve a dúvida.
- Revisão 3, execução B: aplicou a proporcionalidade do ISP (extrair só `Parcelavel` enquanto não houver outra
  variação), coerente com a ressalva de `solid.md`.
- Qualidade 5, reteste A: aceita interface também "quando precisar de fake nos testes". Isso é mais permissivo que
  a reference, mas não leva a uma interface sem fronteira.

**Limite do ensaio:** como nas rodadas anteriores, os executores leram o catálogo-fonte desta sessão. Não houve
instalação do plugin em workspaces limpos.
