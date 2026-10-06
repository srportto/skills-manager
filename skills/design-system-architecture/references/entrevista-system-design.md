# Entrevista de system design em 45 minutos

Roteiro para conduzir (ou responder) uma entrevista de system design. É material de estudo/avaliação — **não**
bloqueia nem substitui o fluxo de produção da skill (requisitos medidos, ADRs, provas).

## Roteiro

| Tempo | Etapa | O que produzir |
|---|---|---|
| 0–5 min | **Requisitos** | 3–5 RF essenciais; RNF com números (latência, disponibilidade, consistência); o que está fora do escopo |
| 5–10 min | **Capacidade** | Taxa média/pico (com duração), leitura:escrita, armazenamento, banda, memória; conta de padeiro em voz alta |
| 10–20 min | **Desenho de alto nível** | Componentes e fluxo principal; começar simples (monólito modular + banco) e justificar cada adição |
| 20–35 min | **Aprofundamento e falhas** | 1–2 componentes críticos em detalhe: dados e consistência, ordenação, idempotência, hot keys; o que acontece quando cada dependência falha ou o pico excede a capacidade |
| 35–40 min | **Trade-offs** | O que foi escolhido, o que se perdeu, quando a decisão mudaria (gatilho mensurável) |
| 40–45 min | **Revisão** | Recapitular requisitos × desenho; riscos em aberto; o que mediria primeiro em produção |

## Perguntas que mostram maturidade

- "Qual a duração do pico?" — define se buffer basta ou se é preciso rejeitar/escalar.
- "Essa operação pode repetir? Qual o efeito de duas execuções?" — idempotência.
- "Que consistência essa leitura precisa?" — por operação, não por banco.
- "Quem reduz a produção quando o consumidor não acompanha?" — backpressure, não fila infinita.
- "Como sei que está funcionando?" — SLI, saturação, alerta.

## Rubrica (0–2 por critério)

| Critério | 0 | 1 | 2 |
|---|---|---|---|
| Requisitos | Começa desenhando | Lista sem números | RF/RNF com números e escopo |
| Capacidade | Ausente | Contas soltas | Contas que mudam o desenho (ex.: partições, cache, rejeição) |
| Desenho | Componentes sem justificativa | Desenho plausível | Simples primeiro, cada componente ligado a um requisito |
| Dados e consistência | Ignora | Escolhe banco por categoria | Decide por operação, cita replicação/particionamento e custos |
| Falhas e proteções | "Usa Kafka/retry" genérico | Cita mecanismos | Limites com números, idempotência, degradação e recuperação |
| Trade-offs | Só benefícios | Cita custos | Perda aceita explícita e gatilho de revisão |
| Comunicação | Desorganizada | Segue roteiro com lacunas | Estruturada, confirma premissas, administra o tempo |

## Armadilhas comuns

- Microsserviços, Kafka e Kubernetes antes de um único número de carga.
- "Fila resolve" para déficit sustentado (buffer só adia o colapso).
- CAP como "escolha dois de três"; "NoSQL não tem transação".
- Retry sem limite/jitter e sem idempotência ("tenta de novo até dar certo").
- Ignorar hot keys/celebridades e a cauda da distribuição.
- Média no lugar de percentil; p99 como se fosse média.
- Esquecer observabilidade e recuperação (como volta ao normal sem nova tempestade).
- Gastar 20 minutos no desenho e não chegar às falhas.

## Exemplo: chat em 45 minutos

Aplicação do roteiro ao estudo "Chat" de [estudos de caso](estudos-de-caso-java.md): requisitos (ordem por
conversa, sem perda após confirmação), capacidade (1 M conexões → ~50 gateways), desenho (gateway WebSocket Java,
serviço de mensagens, Kafka por `conversaId`, presença em Redis), aprofundamento (sequência por conversa,
resync na reconexão, buffer de saída limitado por conexão, backoff com jitter na reconexão em massa), trade-offs
(ordem por conversa × global; push × pull em grupos grandes), revisão (riscos: tempestade de reconexão, grupos
gigantes). Trechos de implementação em Java estão no estudo.
