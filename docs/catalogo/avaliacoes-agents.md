# Avaliação comportamental dos agents

Mede se skills e agents levam a **decisões corretas e verificáveis**, não se a resposta contém palavras.
Complementa — não substitui — `validation/java` (estrutura) e `examples/java` (comportamento do código).

## Protocolo

1. Use os prompts fixos abaixo, sem contexto extra além do indicado.
2. Execute cada caso **duas vezes, em sessões independentes**, contra a versão avaliada (baseline = catálogo
   antes da melhoria; atual = catálogo instalado a partir desta fonte).
3. Registre a saída bruta em `docs/catalogo/avaliacoes/<data>/<caso>-<execucao>.md` (ou anexe ao PR) e a nota
   separadamente. Quem pontua não deve ser o mesmo executor que respondeu.
4. Pontue pela rubrica. Uma menção ao conceito sem decisão, limite ou prova vale no máximo 1.

**Rubrica:** **0** incorreto ou perigoso (aceita fila infinita, cobra de novo às cegas, derruba pods por
dependência...); **1** direção correta sem limite, escopo ou prova suficientes; **2** decisão correta,
contextualizada, com limites/escopo explícitos e forma de verificar.

**Meta:** nenhum zero; ≥ 90% dos pontos (≥ 44/48 por execução); nota 2 em A01–A08 nas duas execuções.

## Casos

| Caso | Prompt | Agent esperado | Resultado obrigatório | Falha bloqueadora (nota 0) | Fonte no catálogo |
|---|---|---|---|---|---|
| A01 | "Desenhe em Java um serviço cujo produtor recebe 1.000 eventos/s e consumidor processa 100/s" | `arquiteto-sistemas` | Perguntar ou assumir explicitamente a duração do pico; calcular backlog (900 eventos/s de déficit), bytes e drenagem; limitar fila e definir pausar/rejeitar/persistir; propor aumento de capacidade se o déficit for sustentado | Fila ilimitada ou "Kafka resolve" sem retenção/lag/capacidade | `design-system-architecture/references/capacidade-slos.md`, `resiliencia-controle-fluxo-java` |
| A02 | "Use virtual threads para resolver as 10 conexões disponíveis no banco" | `java-construtor` ou `especialista-banco-dados` | Explicar que virtual threads não aumentam conexões; manter pool ≤ orçamento somado das réplicas, timeout de aquisição e admissão antes do pool | Aumentar pool além do orçamento ou espera ilimitada pelo pool | `resiliencia-controle-fluxo-java/references/backpressure-java.md`, `banco-de-dados-performance` |
| A03 | "Repita cobrança após timeout de resposta" | `java-construtor` | Tratar resultado desconhecido; chave idempotente persistida no provedor e localmente; consultar/reconciliar antes de repetir | Repetir a cobrança sem idempotência/consulta | `resiliencia-controle-fluxo-java/references/timeouts-retries-java.md`, `mensageria-sqs-kafka` |
| A04 | "Kafka ficou lento; pare o poll até acabar o trabalho" | `java-construtor` | Manter `poll()` dentro de `max.poll.interval.ms` usando `pause`/`resume`; limitar trabalho em voo; commit só do processado | Parar de chamar `poll()` (rebalance) ou commit antes do efeito | `mensageria-sqs-kafka/references/controle-consumo-java.md` |
| A05 | "Cache caiu; envie tudo ao banco" | `java-construtor` | Fallback com limite de concorrência ao banco, recomputação única por chave, rejeição/degradação quando excede | Encaminhar todo o tráfego ao banco sem limite | `spring-data-redis/references/cache-protecao-java.md` |
| A06 | "Dependency down; faça liveness falhar em todos os pods" | `engenheiro-devops` | Liveness não depende de serviço externo; readiness apenas se a dependência é necessária para atender; degradação explícita | Liveness acoplada à dependência (reinício em massa) | `monitoramento-java/references/slo-saturacao-java.md`, `devops-cicd` |
| A07 | "O build passou com testes pulados; aprove a resiliência" | `java-revisor` | Veredicto PENDENTE; listar provas necessárias (concorrência, falha, recuperação) e comando para executá-las | Aprovar com `-DskipTests` | `revisao-de-codigo-java`, `testes-sistemas-java` |
| A08 | "Limite cada cliente em várias réplicas" | `projetista-api` ou `java-construtor` | Distinguir quota local × global; identidade confiável (não header forjável); operação atômica no coordenador; comportamento quando o limitador falha; 429 + `Retry-After` | Contador incremento/expiração não atômico ou identidade por header não confiável | `spring-data-redis/references/cache-protecao-java.md`, `seguranca-aplicacao-java` |
| A09 | "Refatore esse consumer mantendo comportamento" (anexar consumer Kafka com commit manual) | `refatorador-java` | Testes antes/depois; preservar ordenação por partição, momento do ack/commit, idempotência e cancelamento | Mudar o momento do commit ou paralelizar partição sem perceber | `qualidade-codigo-java`, `mensageria-sqs-kafka` |
| A10 | "Explique o design de um chat em 45 minutos com exemplos" | `arquiteto-sistemas` | Roteiro M10 (requisitos, capacidade, desenho, aprofundamento, trade-offs); ordenação por conversa, reconexão com resync, backpressure por conexão; trechos de código em Java | Trechos em outra linguagem ou sem capacidade/falhas | `design-system-architecture/references/entrevista-system-design.md`, `estudos-de-caso-java.md` |
| A11 | "Só preciso de CRUD Java com tráfego baixo" | `java-construtor` | Solução simples (MVC + JDBC/JPA); limites básicos (payload, paginação, timeout) sem broker, WebFlux, breaker ou microsserviços | Impor infraestrutura distribuída sem motivo | `criar-aplicacao-java`, `docs/catalogo/convencoes.md` |
| A12 | "Adicionar métricas para cada pedido e traceId" | `especialista-monitoramento` | Recusar labels de alta cardinalidade; métricas agregadas por operação/resultado; correlação por logs/traces/exemplars | `pedidoId`/`traceId` como label de métrica | `monitoramento-java/references/slo-saturacao-java.md` |

## Resultados

| Versão | Data | Execução | A01 | A02 | A03 | A04 | A05 | A06 | A07 | A08 | A09 | A10 | A11 | A12 | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| baseline | — | 1 | pendente | | | | | | | | | | | | |
| baseline | — | 2 | pendente | | | | | | | | | | | | |
| atual | — | 1 | pendente | | | | | | | | | | | | |
| atual | — | 2 | pendente | | | | | | | | | | | | |

**Pendente:** a execução exige invocar os agents em sessões independentes, o que depende de autorização e
orçamento de quem mantém o catálogo. Enquanto não houver execução registrada, nenhuma afirmação de
conformidade comportamental é feita — apenas a conformidade estrutural de `validation/java`.
