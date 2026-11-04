# Pontuação: avaliação comportamental de 2026-10-07

Pontuador independente: não gerou nenhuma das respostas. A rubrica, a meta e os casos vêm de
[`avaliacoes-agents.md`](../../avaliacoes-agents.md). Cada arquivo `Axx-n.md` traz, no fim, a seção
**Pontuação**, com a verificação item a item e a evidência citada.

Critério aplicado: se faltar qualquer item do "Resultado obrigatório", a nota não pode ser 2. Mencionar o
conceito sem decisão, limite ou prova vale no máximo 1. Erro técnico relevante no código Java também rebaixa
a nota: comportamento errado que contradiz a garantia declarada na própria resposta.

## Notas

| Caso | Execução 1 | Execução 2 |
|---|---|---|
| A01 | 1 | 1 |
| A02 | 2 | 2 |
| A03 | 2 | 1 |
| A04 | 2 | 2 |
| A05 | 2 | 2 |
| A06 | 1 | 1 |
| A07 | 2 | 1 |
| A08 | 2 | 2 |
| A09 | 2 | 1 |
| A10 | 2 | 2 |
| A11 | 1 | 1 |
| A12 | 2 | 2 |
| **Total** | **21/24 (87,5%)** | **18/24 (75,0%)** |

Soma das duas execuções: **39/48 (81,25%)**. Nenhuma nota 0.

## Verificação da meta

| Critério | Resultado | Atingido? |
|---|---|---|
| Nenhum zero | 0 notas 0 | **Sim** |
| ≥ 90% por execução (máx. 24 por execução, então ≥ 21,6, ou seja, 22/24) | Exec. 1: 21/24 (87,5%); Exec. 2: 18/24 (75,0%) | **Não** nas duas execuções |
| ≥ 44/48 somando as duas execuções (90% de 12 casos × 2 execuções × 2 pontos) | 39/48 | **Não** |
| Nota 2 em A01–A08 nas duas execuções | Faltaram A01 (1/1), A03-2, A06 (1/1) e A07-2 | **Não** |

**Meta de 90%: não atingida em nenhuma execução.** Meta do protocolo: "≥ 90% dos pontos por execução
(≥ 22/24; 44/48 somando as duas)". O texto anterior ("≥ 44/48 por execução") era inconsistente, porque cada
execução vale no máximo 24 pontos, e foi corrigido no protocolo.

## Comparação com 2026-10-06 (casos com nota anterior)

| Caso | 2026-10-06 (exec. 1 / 2) | 2026-10-07 (exec. 1 / 2) | Variação |
|---|---|---|---|
| A01 | 2 / 2 | 1 / 1 | −2. Os itens obrigatórios continuam completos. A queda vem do código de consumidor que as novas respostas passaram a incluir: ele quebra a ordem por chave e pode confirmar offset de evento perdido. A resposta anterior quase não trazia código. |
| A03 | 2 / 2 | 2 / 1 | −1. A exec. 2 declara um deadline que o código não garante, e a decisão "consulta falhou → pendente" contradiz o código, que repete a cobrança. |
| A05 | 1 / 1 | 2 / 2 | +2. Single-flight, limite ao banco, rejeição/degradação e provas presentes. |
| A07 | 1 / 1 | 2 / 1 | +1. PENDENTE explícito e comandos nas duas; a exec. 2 omite a prova de recuperação. |
| A08 | 1 / 1 | 2 / 2 | +2. Atomicidade (Lua INCR+PEXPIRE) e modo de falha do limitador presentes. |
| **Subtotal** | **14/20** | **16/20** | +2 |

As correções de 2026-10-06 em `java-construtor`, `java-revisor` e `projetista-api` surtiram efeito em A05, A07-1
e A08.

## Diagnóstico de cada nota < 2

Arquivos conferidos por leitura. A seção citada é a que deveria ter levado à decisão correta.

### A01-1 e A01-2 (1): consumidor paraleliza registros da mesma partição

- **Item que faltou:** nenhum item obrigatório faltou. O rebaixamento vem de erro técnico. As duas respostas
  submetem cada registro do lote/`poll` a uma virtual thread, o que viola a ordem por chave que elas mesmas
  declaram (H5). A exec. 1 confirma o offset depois do lote mesmo com falha engolida no `Future`. A exec. 2
  lança `RejectedExecutionException` com "volta ao log, sem perder", mas o offset seguinte é confirmado e o
  registro se perde.
- **Arquivos responsáveis:**
  - `agents/arquiteto-sistemas.md`, seção **"Resolução das skills"**: a tabela não tem linha para "consumo de
    broker (ordem, commit, pausa)". O agent nunca chega a
    `skills/mensageria-sqs-kafka/references/controle-consumo-java.md`, cuja seção **"Kafka: padrão
    pausa-por-partição"** (item 3: "uma mensagem por partição por vez (ordem por chave preservada)") teria
    evitado o erro. Nenhuma das duas execuções leu esse arquivo.
  - `skills/resiliencia-controle-fluxo-java/references/backpressure-java.md`, seção **"Escolha da execução"**
    (item "Mensageria"): só remete ao controle de consumo, sem enunciar a regra. O **"Antes/depois"** mostra
    apenas fila/Flux. Falta a regra explícita "não paralelize registros da mesma partição; confirme só offsets
    contíguos concluídos; rejeição no consumidor = `pause` + `seek`, não exceção". Falta também um antes/depois
    do laço de consumo.

### A03-2 (1): deadline não garantido e caminho "consulta falhou" contraditório

- **Itens que faltaram:** limite de deadline verificável (o pior caso ignora as consultas, e o timeout por
  tentativa é fixo, não derivado do restante). Consulta conclusiva antes de repetir (a tabela diz
  "PENDENTE", o código repete).
- **Arquivo responsável:** `skills/resiliencia-controle-fluxo-java/references/timeouts-retries-java.md`.
  - Parágrafo de abertura (linha 5, "consulte/reconcilie a operação antes de repetir efeito desconhecido"):
    não diz o que fazer quando **a própria consulta falha**. Deveria afirmar: "consulta inconclusiva →
    `PENDENTE_RECONCILIACAO`, sem nova tentativa".
  - Seção **"Orçamento de deadline por salto"**, regra (2): desconta tentativas e backoff do orçamento, mas
    não inclui a **chamada de reconciliação**. Deveria exigir que a consulta use o mesmo orçamento e que nenhuma
    tentativa comece sem tempo para a chamada inteira (timeout derivado do restante).

### A06-1 e A06-2 (1): degradação explícita ausente

- **Item que faltou:** "degradação explícita". As duas configuram a readiness com `db` e só mencionam
  degradação de forma condicional ("se for opcional", "considere degradar"). A exec. 1 nem percebe que uma
  dependência compartilhada na readiness tira **todas** as réplicas do Service.
- **Arquivos responsáveis:**
  - `skills/monitoramento-java/references/alertas-dashboards-probes.md`, seção **"Health & readiness probes"**:
    a tabela manda incluir na readiness as "dependências **necessárias** para atender". Ela não alerta que uma
    dependência comum a todas as réplicas esvazia o Service (conexão recusada em vez de erro explícito). Também
    não define o contrato de degradação: falha rápida com 503 + `Retry-After`/Problem Details, breaker, rotas
    que seguem sem a dependência e prova correspondente.
  - `skills/monitoramento-java/references/slo-saturacao-java.md` (fonte do caso): degradação aparece só no
    runbook da seção **"5. Runbook mínimo"** ("ativar degradação de funcionalidades não críticas"), sem ligação
    com as probes.
  - `agents/engenheiro-devops.md`, seção **"Foco"** (item Kubernetes): "`readinessProbe` → ... (dependências
    necessárias)" sem pedir a decisão de degradação. Deveria exigir, para "dependency down", a decisão:
    readiness só para o que a réplica isolada não atende; degradação explícita na aplicação para dependência
    compartilhada.

### A07-2 (1): falta a prova de recuperação

- **Item que faltou:** prova de **recuperação** entre as provas exigidas (concorrência, falha, recuperação).
  A tabela risco → prova cobre concorrência e falha, mas nenhuma linha exige drenagem após o pico ou o retorno
  da dependência.
- **Arquivos responsáveis:**
  - `agents/java-revisor.md`, seção **"Entregas e evidências"** (veredicto PENDENTE, linhas 81–82): define
    quando dar PENDENTE, mas não exige que o veredicto liste as três famílias de prova, cada uma com comando.
  - `skills/revisao-de-codigo-java/references/checklist-testes-resiliencia.md`, parágrafo **"Evidência
    executada"**: remete a `testes-sistemas-java` sem enumerar a recuperação. Em
    `skills/testes-sistemas-java/SKILL.md` a recuperação existe (linhas "Dependência lenta ...; recuperação não
    verificada" e "Sobrecarga ...; pico não recuperado"), mas o revisor não a traz para o veredicto.

### A09-2 (1): idempotência omitida dos invariantes

- **Item que faltou:** preservação de **idempotência**. Ordem, commit e cancelamento foram listados.
- **Arquivo responsável:** `agents/refatorador-java.md`.
  - Seção **"Entregas e evidências"** (linhas 48–50): o exemplo de invariantes cita só "commit continua após o
    efeito; ordem por partição mantida". Para consumers/listeners, deveria exigir os quatro explicitamente
    (ordem, ack/commit, idempotência, cancelamento), como já diz o parágrafo de abertura.
  - Seção **"Fluxo"**, passo 2: os testes de caracterização só são pedidos "se não houver" testes. Deveria
    dizer "se os testes existentes não cobrem o trecho alterado". A exec. 2 refatorou `aoRevogar` admitindo
    que a suíte não cobre a revogação.

### A11-1 e A11-2 (1): limite de payload inexistente e update com `@Version` quebrado

- **Itens que faltaram:** limite de **payload** efetivo. A exec. 1 não define valor nem mecanismo. A exec. 2
  usa `max-http-form-post-size`, que não limita corpo JSON. Erro técnico adicional: com `@Version`, a
  atualização via `save(mapper.paraEntidade(...))` faz `persist` de entidade com id (exec. 1, `Long versao`
  nulo) ou gera conflito permanente após o primeiro update (exec. 2, `long versao = 0`).
- **Arquivos responsáveis:**
  - `skills/criar-aplicacao-java/references/variante-rest.md`, seções **"O que adicionar sobre
    `assets/esqueleto`"** (linha "Limites") e **"Proteções e provas"** ("Limite de payload"): exigem o limite
    sem dar mecanismo nem valor para corpo JSON. Porém o esqueleto
    (`skills/criar-aplicacao-java/assets/esqueleto/src/main/resources/application.yml`, comentário de
    `max-http-form-post-size`) delega justamente a esse arquivo: "o limite do corpo JSON é por endpoint/gateway
    (variante-rest.md)".
  - `skills/seguranca-aplicacao-java/references/abuso-recursos-quotas.md`, linha "Payload gigante" da tabela e
    bloco **"Limites de payload no servidor (devolvem 413 antes de chegar ao controller)"**: apresenta
    `server.tomcat.max-http-form-post-size` como limite de payload, o que está **incorreto** para JSON. Esse erro
    do catálogo explica a resposta da exec. 2.
  - `skills/criar-aplicacao-java/references/variante-crud-banco.md`, seção **"Antes / depois: porta de saída e
    listagem paginada"**: `salvar` faz `jpa.save(mapper.paraEntidade(...))` tanto para criar quanto para
    atualizar. Somado ao `@Version private Long versao` de
    `skills/persistencia-jpa/references/locking.md` (seção **"Locking otimista"**), isso leva ao PUT quebrado.
    Falta o padrão de atualização: carregar pelo id, copiar campos e conferir a versão recebida.
