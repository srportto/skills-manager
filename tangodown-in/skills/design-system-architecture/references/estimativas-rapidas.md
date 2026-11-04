# Estimativas rápidas (back-of-the-envelope)

Leia este arquivo quando precisar de ordens de grandeza para descartar um desenho inviável: latências típicas, conversões de volume (req/dia para req/s, pico, bytes/dia) e a Lei de Little aplicada a pools Java. Para orçamento completo de conexões, deadline, fan-out e SLO, use [capacidade e SLOs](capacidade-slos.md) — este arquivo não repete aquelas fórmulas, só dá a régua de bolso.

## Latências: ordens de grandeza

Valores **aproximados** e dependentes de hardware, nuvem e carga; servem para comparar camadas entre si (ex.: "disco é ~1000x mais lento que memória"), nunca como SLO. Meça no seu ambiente antes de decidir.

| Operação | Ordem de grandeza | Leitura prática |
|---|---|---|
| Acesso a cache L1/L2 da CPU | 1–10 ns | Irrelevante para desenho de sistema |
| Acesso à memória principal (heap) | ~100 ns | Estruturas em memória são "grátis" frente à rede |
| Lock não disputado / chamada de método virtual | 10–100 ns | Contenção real custa muito mais: meça |
| Leitura aleatória em SSD NVMe | 20–100 µs | Banco com working set em disco já é ~1000x a memória |
| Round trip na mesma zona de disponibilidade | 0,2–1 ms | Cada salto de rede interna soma ~0,5 ms |
| Consulta indexada simples no banco (sem rede) | 0,1–2 ms | Com rede e pool, conte 1–5 ms |
| Round trip entre zonas da mesma região | 1–2 ms | Replicação síncrona entre zonas paga isso em toda escrita |
| Leitura aleatória em HDD (seek) | 5–10 ms | Raro em nuvem; ainda aparece em storage frio |
| Round trip entre regiões no mesmo continente | 30–80 ms | Multi-região síncrono inviabiliza p99 baixo |
| Round trip intercontinental | 100–250 ms | CDN/edge e réplica de leitura regional existem por isso |
| Handshake TLS 1.3 (conexão nova) | 1–2 round trips | Reutilize conexões (keep-alive, HTTP/2, pool) |

Regras de bolso derivadas:

- Cada dependência síncrona adiciona pelo menos um round trip; 5 chamadas sequenciais na mesma zona já custam ~2,5 ms só de rede.
- Operação em memória é ordens de grandeza mais barata que operação em rede; cache só compensa se o hit ratio for alto e a invalidação for tolerável.
- Percentil alto de uma cadeia é pior que o de cada elo: ver fan-out em [capacidade e SLOs](capacidade-slos.md#5-amplificação-por-fan-out-e-retry).

## Conversões de volume

| Conversão | Fórmula | Atalho |
|---|---|---|
| Requisições/dia para req/s médio | `req/dia ÷ 86.400` | 1 M/dia ≈ 12 req/s |
| Pico a partir da média | `média × fator de pico` | Fator 2–10 conforme o negócio; declare **duração** do pico |
| Bytes/dia | `req/dia × bytes por item` | 1 M/dia × 2 KiB ≈ 2 GiB/dia |
| Armazenamento em N anos | `bytes/dia × 365 × N × (1 + overhead de índice e réplicas)` | Índices 30–100% do dado, réplicas multiplicam |
| Banda | `req/s × bytes por resposta × 8` bits/s | 400 req/s × 10 KiB ≈ 32 Mbit/s por direção |
| Concorrência (Lei de Little) | `L = λ × W` | λ em req/s, W em segundos |

Potências úteis: 2^10 ≈ 10^3 (KiB), 2^20 ≈ 10^6 (MiB), 2^30 ≈ 10^9 (GiB); 1 dia ≈ 10^5 s (86.400); 1 mês ≈ 2,6 × 10^6 s.

## Exemplo resolvido

Pergunta: "Quantos pedidos por segundo, que volume de dados e quantas requisições simultâneas um checkout de 4,32 milhões de pedidos/dia exige?" (mesmos números do caso de checkout em [estudos de caso](estudos-de-caso-java.md)).

1. Média: 4.320.000 ÷ 86.400 = **50 pedidos/s**.
2. Pico: o caso assume 400/s por 15 min (fator 8). Total no pico: 400 × 900 s = 360.000 pedidos, ou ~8% do dia concentrado em 1% do tempo — hipótese a validar com dados de lançamentos anteriores.
3. Dados: 4.320.000 × 2 KiB ≈ **8,2 GiB/dia** de pedidos; em 5 anos, 8,2 GiB × 365 × 5 ≈ 15 TiB antes de índices (+30% a +100%) e réplicas. Conclusão: cabe em um PostgreSQL bem dimensionado com particionamento por data; não exige sharding de partida.
4. Banda de entrada no pico: 400 × 2 KiB × 8 ≈ 6,4 Mbit/s — irrelevante; o gargalo é conexão de banco e provedor de pagamento, não rede.
5. Concorrência (Little): se o tempo médio por pedido é 80 ms, L = 400 × 0,08 = **32 pedidos em voo** no pico. Se o provedor de pagamento responde em 250 ms (p50), L sobe a 400 × 0,25 = 100 — e é aí que deadline e bulkhead protegem o pool.

## Notas de capacidade para Java

A Lei de Little dimensiona pools e limites a partir da vazão e do tempo de serviço:

```java
// Antes: pool "no chute" — 200 conexões por réplica porque "parece seguro".
// Com 6 réplicas, 1.200 conexões disputam um banco que aceita 150.
//   spring.datasource.hikari.maximum-pool-size=200

// Depois: dimensiona pela Lei de Little com folga e soma entre réplicas.
// λ por réplica = 400 req/s ÷ 6 ≈ 67 req/s; W de banco = 20 ms
// L = 67 × 0,02 ≈ 1,3 conexão em uso média; com folga de 5x para rajadas => pool ~ 8–20.
//   spring.datasource.hikari.maximum-pool-size=20   (6 réplicas × 20 = 120 ≤ orçamento do banco)
//   spring.datasource.hikari.connection-timeout=200 (falha visível, não espera infinita)
```

- Pool maior que o necessário não acelera: aumenta contenção no banco. O limite real é o orçamento **somado** das réplicas (ver [capacidade e SLOs](capacidade-slos.md#3-orçamento-de-conexões-e-concorrência)).
- Virtual threads tornam barato esperar uma conexão, mas não criam conexões: o pool continua sendo o teto de concorrência para o banco.
- Threads de plataforma: concorrência máxima = tamanho do pool de threads do servidor (`server.tomcat.threads.max`); se λ × W exceder isso, a fila cresce na porta — limite e rejeite com 503 em vez de aceitar tudo.
- O estudo de caso mostra o orçamento aplicado e provado sob carga: [CheckoutApplication](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java) (admissão limitada) e [CheckoutSobCargaSimulationCargaIT](../../../examples/java/carga/src/test/java/br/com/srportto/exemplos/CheckoutSobCargaSimulationCargaIT.java) (pico acima da capacidade). Números de laboratório não são SLO.
