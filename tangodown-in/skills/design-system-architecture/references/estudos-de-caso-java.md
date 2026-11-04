# Estudos de caso de system design (Java)

Quatro estudos no mesmo formato: requisitos → estimativas → componentes → alternativa simples → trade-offs →
falhas e proteções → validação. Números são **hipóteses de exercício**; troque pelos do seu sistema. Trechos de
código são Java 25. O checkout é o caso executável (fim do documento).

---

## 1. Encurtador de URLs

**Requisitos.** Criar link curto para URL longa; redirecionar; expiração opcional; métricas de cliques
(aproximadas). Leitura domina: redirect p99 < 50 ms; disponibilidade de redirect 99,95%.

**Estimativas.** 100 M links novos/mês ≈ 40/s (pico 400/s); leitura 100:1 ≈ 4.000/s (pico 40.000/s). Registro
≈ 500 bytes → 50 GB/mês; 5 anos ≈ 3 TB (+ índices). Código de 7 caracteres base62 = 62⁷ ≈ 3,5 × 10¹² combinações.

**Componentes.** API Java (criação e redirect) → cache (Redis) → banco chave-valor/relacional particionado por
código. Cliques vão para fila/log e são agregados à parte (não no caminho do redirect).

**Geração de identificadores.** Evite colisão sem consulta prévia: contador distribuído em blocos (cada instância
reserva um intervalo) codificado em base62, ou id aleatório com restrição única e nova tentativa limitada.

```java
// Codifica um id numérico (de um bloco reservado por instância) em base62.
static String base62(long valor) {
    final String alfabeto = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    var codigo = new StringBuilder();
    do {
        codigo.append(alfabeto.charAt((int) (valor % 62)));
        valor /= 62;
    } while (valor > 0);
    return codigo.reverse().toString();
}
```

**Alternativa simples.** Um PostgreSQL com índice no código + cache local Caffeine atende dezenas de milhares de
redirects/s para catálogos que cabem em memória; só particione quando o volume exigir.

**Trade-offs.** Ids sequenciais são enumeráveis (privacidade); aleatórios exigem tratamento de colisão. 301 é
cacheado pelo navegador (menos carga, perde métricas); 302 mantém métricas.

**Falhas e proteções.** Cache fora → recomputação limitada ao orçamento do banco (`CacheProtegido`); hot link
viral → cache local L1 + TTL com jitter; abuso de criação → quota por identidade (429); links maliciosos →
validação/lista de bloqueio. Cliques: fila limitada com descarte **amostrado** aceitável (é telemetria, não
negócio).

**Validação.** Teste de colisão sob concorrência (restrição única), carga de leitura com cache frio e quente,
queda do cache com banco limitado.

---

## 2. Chat (mensageria em tempo real)

**Requisitos.** Mensagens 1:1 e em grupo, ordem por conversa, entrega a quem está online, histórico, indicador de
presença. Entrega p99 < 500 ms para online; nenhuma mensagem perdida após confirmação ao remetente.

**Estimativas.** 10 M usuários ativos/dia, 50 mensagens/usuário/dia ≈ 5.800 msg/s (pico 30.000/s); 1 M conexões
WebSocket simultâneas → ~20 k conexões por instância ⇒ ~50 instâncias de gateway (com folga).

**Componentes.** Gateway WebSocket (Java, virtual threads) mantém conexões; serviço de mensagens persiste e
publica no Kafka particionado por `conversaId` (ordem por conversa); fan-out para gateways onde os destinatários
estão conectados (registro de presença em Redis com TTL).

**Ordem e reconexão.** Cada mensagem recebe sequência por conversa ao ser persistida. O cliente envia a última
sequência vista ao reconectar e o servidor reenvia o intervalo faltante (resync), em vez de confiar na ordem de
chegada pela rede.

```java
// Buffer de saída por conexão LIMITADO: cliente lento é desconectado (e fará resync), não derruba o gateway.
final class ConexaoCliente {
    private final FilaLimitada<MensagemChat> saida = new FilaLimitada<>(256);

    boolean entregar(MensagemChat mensagem) {
        if (saida.oferecer(mensagem) == FilaLimitada.Admissao.ACEITO) return true;
        fechar("cliente lento: buffer de saída cheio"); // o cliente reconecta e pede o que faltou
        return false;
    }

    void fechar(String motivo) { /* fecha o WebSocket com código de política e registra métrica */ }
}
```

**Alternativa simples.** Long polling + banco relacional com sequência por conversa atende milhares de usuários
sem gateway dedicado nem broker.

**Trade-offs.** Ordem global é cara e desnecessária: ordem **por conversa** basta. Grupos enormes geram fan-out
caro: limite tamanho de grupo ou trate como canal (pull).

**Falhas e proteções.** Gateway reinicia → milhares reconectam juntos: reconexão com backoff + jitter e admissão
de handshake; consumidor lento no Kafka → pausa por partição (lag no broker, memória estável); idempotência pela
chave da mensagem do cliente (reenvio após timeout não duplica).

**Validação.** Teste de reconexão com resync; cliente lento não afeta os demais; tempestade de reconexão limitada;
duplicata de envio não duplica mensagem.

---

## 3. Feed de rede social

**Requisitos.** Timeline com posts de quem o usuário segue, ordem aproximada por tempo/relevância, leitura p99 <
200 ms; atraso de alguns segundos aceitável.

**Estimativas.** 200 M usuários, 500 M leituras de feed/dia (~5.800/s, pico 50.000/s), 50 M posts/dia (~600/s).
Seguidores: mediana 200, celebridades com 50 M.

**Componentes.** Fan-out na escrita (push) para usuários comuns: ao publicar, o id do post é inserido nas
timelines materializadas (Redis sorted set limitado aos N mais recentes). Fan-out na leitura (pull) para contas
com muitos seguidores: o feed mescla a timeline materializada com posts recentes das celebridades seguidas.

```java
// Timeline materializada limitada: mantém só os 800 mais recentes por usuário (memória previsível).
void inserirNaTimeline(UnifiedJedis redis, String usuarioId, String postId, long epochMillis) {
    String chave = "feed:timeline:" + usuarioId;
    redis.zadd(chave, epochMillis, postId);
    redis.zremrangeByRank(chave, 0, -801);
}
```

**Alternativa simples.** Pull puro com consulta indexada (posts dos seguidos, ordenados por data, com cache curto)
atende bases pequenas sem pipeline de fan-out.

**Trade-offs.** Push: leitura barata, escrita amplificada (1 post × seguidores). Pull: escrita barata, leitura cara.
Híbrido por número de seguidores equilibra os dois. Consistência eventual aceita e declarada.

**Falhas e proteções.** Hot key (celebridade) → pull + cache; fan-out atrasado → lag observado e workers
escaláveis com teto do downstream; replay de fan-out idempotente (inserir o mesmo post duas vezes é inócuo no
sorted set).

**Validação.** Carga com distribuição de seguidores realista (cauda longa), lag de fan-out medido, hot key sem
saturar uma partição.

---

## 4. E-commerce em alta escala (checkout) — caso executável

**Requisitos.** Criar pedido sem cobrança duplicada; consulta de catálogo pode ficar alguns segundos
desatualizada; checkout 99,9% bem-sucedido em 30 dias; p99 < 300 ms em carga nominal. Em pico (lançamento), é
preferível rejeitar rápido parte dos pedidos a degradar todos.

**Estimativas.** Média 50 pedidos/s, pico 400/s por 15 min; item 2 KiB; 6 réplicas × pool de 20 = 120 conexões
≤ orçamento do banco.

**Componentes.** Checkout Java/Spring Boot com admissão limitada (503 + `Retry-After`), deadline por requisição,
idempotência transacional (chave + tenant + payload) com outbox na mesma transação, relay em taxa limitada para o
Kafka; catálogo com cache protegido; pagamento atrás de deadline e bulkhead.

**Alternativa simples.** Monólito modular com PostgreSQL e outbox — é o que o exemplo implementa. Microsserviços
só quando times/escala exigirem.

**Trade-offs.** Rejeitar no pico (perda de vendas controlada e medida) × aceitar tudo (latência explode e todos
falham). Outbox dá at-least-once: consumidores deduplicam.

**Falhas e proteções.** Provedor lento → deadline, sem retenção de conexão; banco saturado → pool limitado +
rejeição; broker fora → eventos ficam na outbox; repetição do cliente → mesma resposta; pico → admissão.

**Validação executada.** Aplicação:
[CheckoutApplication](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java)
com [CheckoutApplicationTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CheckoutApplicationTest.java)
(idempotência, conflito 409, 400/422, 503 rápido com `Retry-After` e métrica de rejeição, probes). Ensaio de carga
em Java: [CheckoutSobCargaSimulation](../../../examples/java/carga/src/main/java/br/com/srportto/exemplos/CheckoutSobCargaSimulation.java)
com [CheckoutSobCargaSimulationCargaIT](../../../examples/java/carga/src/test/java/br/com/srportto/exemplos/CheckoutSobCargaSimulationCargaIT.java)
(baseline, rampa, pico acima da capacidade, retorno). Resultado registrado em `docs/catalogo/compatibilidade.md`
— números de laboratório, não SLO.
