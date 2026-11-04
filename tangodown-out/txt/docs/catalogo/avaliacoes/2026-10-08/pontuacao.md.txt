# Avaliação comportamental da skill EXJ — 2026-10-08

Foram avaliados os seis casos definidos em `skills/exj/evals/evals.json`, com duas execuções independentes por caso. Os executores foram subagentes e a pontuação foi feita pelo agente principal, que não produziu as respostas. Cada saída bruta está registrada nos arquivos desta pasta.

| Caso | Decisão verificada | A | B |
|---|---|---:|---:|
| 1 — criar API Spring | `criar-aplicacao-java` + `java-construtor`; pede entradas necessárias antes de implementar | 2 | 2 |
| 2 — revisar diff Java | `revisao-de-codigo-java` + `java-revisor`; não altera arquivos e pede o diff ausente | 2 | 2 |
| 3 — arquitetura e ADR | `design-system-architecture` + `arquiteto-sistemas`; separa arquitetura distribuída de interna e registra trade-offs | 2 | 2 |
| 4 — latência, saturação, métricas, traces e alertas | `monitoramento-java` + `especialista-monitoramento`; solicita evidências e evita conclusões sem dados | 2 | 2 |
| 5 — aplicação Python | declara EXJ fora do escopo e usa `python-pro`, sem skills Java | 2 | 2 |
| 6 — feature em app existente | `arquitetura-limpa-java` + `java-construtor`; não gera esqueleto de app nova | 2 | 2 |
| **Total** |  | **12/12** | **12/12** |

**Resultado:** 24/24 pontos (100%), nenhum zero. As correções feitas na skill foram reavaliadas: o encaminhamento exige o par skill + agent, diferencia app nova de feature em app existente e executa a tarefa ou solicita entrada concreta indispensável.
