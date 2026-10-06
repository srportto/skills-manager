# Cache protegido em Java

Cache melhora latência e reduz carga **enquanto funciona**. O desenho precisa dizer o que acontece quando ele
erra (miss em massa), envelhece (staleness) ou some (queda) — sem transferir toda a carga para o banco.

## Local × distribuído

| Aspecto | Local (Caffeine, em memória) | Distribuído (Redis/Valkey, Memcached) |
|---|---|---|
| Latência | Nanossegundos/microssegundos | ~1 ms de rede |
| Consistência entre réplicas | Cada réplica tem a sua cópia; invalidação precisa de broadcast | Uma cópia compartilhada |
| Memória | Multiplicada pelo número de réplicas; disputa heap com a aplicação | Cluster dedicado |
| Falha | Some com a instância (aquecimento a cada deploy) | Queda afeta todas as réplicas ao mesmo tempo |
| Uso típico | Dados pequenos e quentes, quase imutáveis (configuração, catálogo) | Sessões, dados compartilhados, contadores, quotas |

Dois níveis (L1 local com TTL curto + L2 distribuído) reduzem latência e carga no Redis; custam mais uma camada de
staleness. **Redis × Memcached:** Memcached é cache chave-valor simples, multi-thread, sem persistência nem
estruturas; Redis/Valkey oferece estruturas (hash, sorted set, stream), scripts Lua, replicação e persistência
opcional — por isso serve também para quotas e filas de trabalho.

## Padrões de leitura e escrita

| Padrão | Como funciona | Risco | Quando |
|---|---|---|---|
| Cache-aside | Aplicação lê do cache; no miss, lê a fonte e grava no cache | Stampede no miss; janela de staleness | Padrão geral |
| Read-through | O cache (biblioteca) busca na fonte no miss | Mesmos do cache-aside, escondidos na lib | Caffeine `LoadingCache`, `@Cacheable` |
| Write-through | Escrita vai ao cache e à fonte de forma síncrona | Latência de escrita; falha parcial entre os dois | Leitura logo após escrita frequente |
| Write-behind | Escrita vai ao cache e é persistida depois, em lote | **Perda** se o cache cair antes de persistir; **reordenação** de escritas; conflitos | Métricas/contadores toleráveis a perda; nunca para dado financeiro |
| Refresh-ahead | Renova antes de expirar quando a chave é acessada | Trabalho em chaves que não serão lidas | Chaves quentes com recomputação cara |

## TTL, evicção e staleness

- Toda entrada tem TTL; escolha pelo **staleness máximo aceitável** da operação ("preço pode ficar até 60 s
  desatualizado"), não por hábito.
- **Jitter no TTL** evita expiração em onda de chaves gravadas juntas:

  ```java
  // TTL base de 10 min com até 10% de jitter: chaves gravadas no mesmo segundo expiram espalhadas.
  static Duration ttlComJitter(Duration base, RandomGenerator aleatorio) {
      long jitterMs = (long) (base.toMillis() * 0.10 * aleatorio.nextDouble());
      return base.plusMillis(jitterMs);
  }
  ```

- **Evicção** quando a memória enche: LRU (menos recentemente usado), LFU (menos frequentemente usado — melhor
  para popularidade estável), FIFO/aleatório. No Redis, `maxmemory` + `maxmemory-policy` (`allkeys-lfu` para cache
  puro; `volatile-*` quando o mesmo Redis guarda dados sem TTL que não podem ser expulsos — melhor separar
  instâncias). Sem `maxmemory`, o Redis cresce até o OOM do host.
- **Negative caching:** chave inexistente consultada repetidamente (ou por ataque com ids aleatórios) bate sempre
  na fonte; guarde um marcador "ausente" com TTL curto.

## Invalidação

- Invalide/atualize **depois do commit** da transação de escrita (ex.: `@TransactionalEventListener(phase =
  AFTER_COMMIT)`). Invalidar antes do commit permite que um leitor recarregue o valor antigo e o grave de novo.
- Mesmo após o commit há corrida: leitor A lê valor antigo da fonte, escritor B commita e invalida, A grava o
  antigo no cache. Mitigações: TTL curto como teto de staleness, versão no valor (grava só se mais nova) ou
  invalidação atrasada (deletar de novo após alguns segundos).
- Em várias réplicas com cache local, invalidação exige broadcast (pub/sub) — ou aceite o TTL como limite.

## Stampede e queda do cache

- **Chave quente expirando:** uma recomputação por chave (lock por chave em processo, `@Cacheable(sync = true)`)
  e as demais esperam ou recebem o valor anterior (stale-while-revalidate).
- **Cache inteiro fora:** sem proteção, 100% das leituras viram consultas ao banco — que não foi dimensionado para
  isso. Limite a concorrência de recomputação ao **orçamento do banco** e rejeite/degrade o excedente; o tempo de
  espera pelo Redis também tem timeout curto (ex.: 50–100 ms), senão a latência do cache doente vira a latência
  da aplicação.
- **Retorno do cache:** aquecer gradualmente (taxa limitada de recomputação), não todas as chaves de uma vez.

Implementação de referência:
[CacheProtegido](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CacheProtegido.java) —
recomputação única por chave (faixas fixas de locks, sem mapa de locks ilimitado) e limite de concorrência ao
banco. Provas em
[CacheProtegidoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java):
cache indisponível mantém o limite do banco; chave quente tem uma única recomputação concorrente.

## Métricas

Taxa de acerto por cache (não global), latência do cache, erros/timeouts do cache, recomputações em andamento,
rejeições por limite do banco, memória usada e evicções. Uma queda brusca de acerto com aumento de consultas ao
banco é o sinal de stampede — alerte nele.
