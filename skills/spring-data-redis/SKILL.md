---

name: spring-data-redis
description: "Referência para Spring Data Redis/Valkey — cache-aside, convenção de chaves, TTL, serialização Jackson 3, sorted sets (agendamento), streams com consumer groups (fila de trabalho), rate limiting. Use ao implementar cache, agendamento ou fila de trabalho com Redis/Valkey em Java/Spring Boot. Uso: sessão principal ou invocação manual via `/spring-data-redis`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.0.0"
  domain: language
  triggers: Redis, Valkey, cache, cache-aside, sorted set, stream, consumer group, rate limiting, TTL, serialização, spring-data-redis, temporizacao
  role: specialist
  scope: implementation
  output-format: code
  related-skills: arquitetura-limpa-java, persistencia-jpa, mensageria-sqs-kafka
---

# Spring Data Redis / Valkey

Referência para integração **Redis/Valkey** em Java/Spring Boot. Valkey é um fork do Redis
(API compatível), então `spring-boot-starter-data-redis` funciona para ambos. Usos cobertos: cache
protegido, quota distribuída atômica, sorted sets como agenda e streams como fila de trabalho com consumer
groups. (No monorepo de origem, um serviço de expiração de autorizações usava agenda + stream — contexto
externo, citado só como exemplo.)

**Quando NÃO usar:** para mensageria SQS/Kafka (ver `mensageria-sqs-kafka`), para cache JPA
(ver `persistencia-jpa`).

## Dependências

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

## Configuração base

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer()); // JSON, não Java serialize
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))
            .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .withCacheConfiguration("pedidos", config.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("produtos", config.entryTtl(Duration.ofHours(1)))
            .build();
    }

    // Jackson 3 (tools.jackson) — default typing OFF por padrão; habilite escopado
    // para pacotes confiáveis, senão o cache volta como LinkedHashMap e estoura ClassCastException.
    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
            .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.exemplo.")
                .allowIfSubType("java.util.")
                .build())
            .build();
    }
}
```

## Convenção de chaves

```
{app}:{dominio}:{id}              → checkout:pedido:uuid-aqui
{app}:{dominio}:lista:{filtro}    → checkout:pedido:lista:status:PENDENTE
{app}:sessao:{usuarioId}          → checkout:sessao:uuid-aqui
{app}:ratelimit:{identidade}      → checkout:ratelimit:tenant-42
```

Quota por **identidade autenticada** (cliente/tenant), não por IP: IP é compartilhado (NAT, proxies) e
`X-Forwarded-For` só é confiável quando escrito pelo proxy de borda conhecido.

## Cache declarativo (@Cacheable)

```java
@Service
@RequiredArgsConstructor
public class ProdutoService {

    @Cacheable(value = "produtos", key = "#id")
    public ProdutoResponse buscarPorId(UUID id) {
        return produtoRepository.findById(id)
            .map(ProdutoResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + id));
    }

    @CachePut(value = "produtos", key = "#result.id")
    @Transactional
    public ProdutoResponse atualizar(UUID id, AtualizarProdutoRequest request) {
        Produto produto = produtoRepository.findById(id).orElseThrow();
        produto.atualizar(request);
        return ProdutoResponse.from(produtoRepository.save(produto));
    }

    @CacheEvict(value = "produtos", key = "#id")
    @Transactional
    public void deletar(UUID id) {
        produtoRepository.deleteById(id);
    }

    @CacheEvict(value = "produtos", allEntries = true)
    public void limparCache() {}
}
```

## Cache-aside manual

```java
@Service
@RequiredArgsConstructor
public class PedidoCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JsonMapper jsonMapper; // Jackson 3 — Boot auto-configura um bean JsonMapper
    private static final Duration TTL = Duration.ofMinutes(5);

    public Optional<PedidoResponse> obter(UUID pedidoId) {
        String chave = "pedidos:pedido:" + pedidoId;
        Object cacheado = redisTemplate.opsForValue().get(chave);
        if (cacheado == null) return Optional.empty();
        return Optional.of(jsonMapper.convertValue(cacheado, PedidoResponse.class));
    }

    public void salvar(PedidoResponse pedido) {
        String chave = "pedidos:pedido:" + pedido.id();
        redisTemplate.opsForValue().set(chave, pedido, TTL);
    }

    public void invalidar(UUID pedidoId) {
        redisTemplate.delete("pedidos:pedido:" + pedidoId);
    }
}
```

## Rate limiting

**Não** faça `INCR` e depois `EXPIRE` em duas chamadas: se o processo cair (ou a conexão falhar) entre elas,
a chave fica **sem expiração** e o cliente perde a quota para sempre. Incremento e expiração precisam ser uma
operação atômica — um script Lua executa inteiro no servidor:

```java
@Component
@RequiredArgsConstructor
public class LimiteDeTaxa {

    // PTTL < 0 também corrige chaves antigas que ficaram sem expiração.
    private static final RedisScript<Long> INCREMENTAR_COM_JANELA = RedisScript.of("""
            local atual = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return atual
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** identidade = cliente/tenant autenticado, nunca header livre enviado pelo cliente. */
    public boolean permitido(String identidade, int maxRequisicoes, Duration janela) {
        Long atual = redisTemplate.execute(INCREMENTAR_COM_JANELA,
                List.of("ratelimit:" + identidade), String.valueOf(janela.toMillis()));
        return atual != null && atual <= maxRequisicoes;
    }
}
```

Decida o que fazer quando o Redis está indisponível: **fail-open** libera tudo (o downstream perde a proteção),
**fail-closed** derruba o serviço junto com o limitador. O meio-termo é um limite local conservador por
instância. Exemplo executável com fallback local limitado e testes contra Valkey real (concorrência entre
réplicas e chave órfã sem TTL):
[LimiteDistribuido](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/LimiteDistribuido.java).

Janela fixa permite até 2× o limite na virada da janela; se isso importa, use janela deslizante (sorted set ou
contadores ponderados) ou token bucket em Lua. Quota de aplicação não substitui proteção de borda (WAF/CDN)
contra DDoS.

## Sorted set como agenda (ex.: agendador de expirações)

```java
// ZADD — agenda com score = timestamp de vencimento (epoch millis)
redisTemplate.opsForZSet().add(chaveAgenda, autorizacaoId.toString(), vencimento.toEpochMilli());

// ZRANGEBYSCORE — varredura dos vencidos até agora
Set<String> vencidos = redisTemplate.opsForZSet()
    .rangeByScore(chaveAgenda, 0, Instant.now().toEpochMilli(), 0, limite);

// ZREM — remove após processar
redisTemplate.opsForZSet().remove(chaveAgenda, autorizacaoId.toString());

// ZCARD — tamanho da agenda (útil para health check)
Long tamanho = redisTemplate.opsForZSet().zCard(chaveAgenda);
```

## Stream com consumer group (ex.: agendador de expirações)

```java
// XADD — adiciona mensagem ao stream
redisTemplate.opsForStream().add(chaveStream, Map.of("id_autorizacao", autorizacaoId.toString()));

// XGROUP CREATE — cria consumer group (idempotente)
redisTemplate.opsForStream().createGroup(chaveStream, ReadOffset.from("0"), grupoConsumidor);

// XREADGROUP — lê mensagens pendentes para este consumidor
List<MapRecord<String, Object, Object>> mensagens = redisTemplate.opsForStream().read(
    Consumer.from(grupoConsumidor, nomeConsumidor),
    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
    StreamOffset.create(chaveStream, ReadOffset.lastConsumed())
);

// XACK — confirma DEPOIS do efeito (idempotente); sem XACK a entrada fica pendente
redisTemplate.opsForStream().acknowledge(chaveStream, grupoConsumidor, recordId);

// XPENDING — só INSPECIONA pendências (monitoramento); não recupera nada
PendingMessagesSummary resumo = redisTemplate.opsForStream().pending(chaveStream, grupoConsumidor);
```

Três operações diferentes, frequentemente confundidas:

| Comando | O que faz | Quando |
|---|---|---|
| `XPENDING` | Lista/conta entradas entregues e não confirmadas | Métrica e alerta (pendências e ociosidade) |
| `XAUTOCLAIM` (Redis/Valkey ≥ 6.2) | **Transfere a posse** de pendências ociosas há mais de N ms para outro consumidor e as devolve | Recuperar trabalho de consumidor morto/travado |
| `XACK` | Remove a entrada da lista de pendências | Depois do efeito durável |

`XAUTOCLAIM` não tem atalho dedicado em todas as versões do `StreamOperations`; use o cliente nativo (Lettuce/
Jedis) ou `execute`. Escolha a ociosidade mínima **maior** que o tempo máximo de processamento — senão você
rouba trabalho de um consumidor vivo e processa em dobro. Entradas reivindicadas muitas vezes (contador de
entregas do `XPENDING`) vão para uma quarentena em vez de ciclar para sempre. Defina retenção do stream
(`XADD ... MAXLEN ~ N` ou `XTRIM MINID`) para ele não crescer sem limite.

Exemplo executável (consumidor morre sem `XACK`; outro reivindica após a ociosidade mínima, processa uma vez e
confirma; nada é roubado antes do prazo):
[RecuperacaoPendencias](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/RecuperacaoPendencias.java)
com [RecuperacaoPendenciasExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasExternoIT.java).

## Lua script para atomicidade (varredura + move)

```lua
-- varredura.lua: lê vencidos do sorted set e move para o stream atomicamente
local vencidos = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))
for _, id in ipairs(vencidos) do
    local removido = redis.call('ZREM', KEYS[1], id)
    if removido == 1 then
        redis.call('XADD', KEYS[2], '*', 'id_autorizacao', id)
    end
end
return #vencidos
```

```java
// Uso no Java
DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
Long movidos = redisTemplate.execute(script, List.of(chaveAgenda, chaveStream),
    String.valueOf(Instant.now().toEpochMilli()), String.valueOf(limite));
```

## application.yml

```yaml
spring:
  data:
    redis:
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      password: ${VALKEY_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
  cache:
    type: redis
```

## Cache stampede

Quando uma chave quente expira, toda requisição concorrente erra o cache ao mesmo tempo e todas
batem no banco para recalcular o mesmo valor ("thundering herd"). Para cargas caras e de alto
tráfego, deixe um único chamador computar enquanto os outros esperam:

```java
@Cacheable(value = "produtos", key = "#id", sync = true)
public ProdutoResponse buscarPorId(UUID id) { ... }
```

`sync = true` serializa a recomputação por chave **dentro de uma única instância**. Com N réplicas, até N
recomputações simultâneas ainda chegam ao banco — normalmente aceitável. Para limitar entre réplicas, um lock
curto (`SET chave valor NX PX ttl`, liberado com script que confere o valor) reduz a duplicação, mas **não** é
exclusão mútua garantida (expira durante pausas de GC/rede): use-o como otimização, nunca para proteger efeito
de negócio. Combine com TTL com jitter e, sobretudo, **limite de concorrência ao banco** para o caso em que o
cache inteiro some. Detalhes, incluindo queda do cache e invalidação após commit:
[cache protegido](references/cache-protecao-java.md).

## Armadilhas

- Agent usa serialização Java para valores — sempre use JSON (`GenericJacksonJsonRedisSerializer`).
- Agent cacheia entidades com campos JPA lazy — cacheie DTOs/responses, não entidades.
- Agent não configura TTL — memória não é infinita; sempre defina expiração.
- Agent esquece `@EnableCaching` — `@Cacheable` silenciosamente não faz nada sem ele.
- Agent cacheia valores `null` sem critério — por padrão use `.disableCachingNullValues()`; quando consultas a
  chaves inexistentes forem um vetor de carga (ex.: ids aleatórios), use **negative caching** explícito com TTL
  curto (marcador "ausente"), não `null` indistinto.
- Agent trata o cache como fonte de verdade — cache é cópia; decisão de negócio crítica (saldo, estoque,
  pagamento) consulta a fonte.
- Agent deixa a queda do cache mandar todo o tráfego ao banco — limite a concorrência de recomputação
  ([CacheProtegido](../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CacheProtegido.java)).
- Agent não protege chave quente — use `@Cacheable(sync = true)` para evitar stampede na expiração.
- Agent usa o mesmo TTL para tudo — adicione jitter para não expirar em onda sincronizada.
- Agent usa `GenericJackson2JsonRedisSerializer` — API Jackson 2 deprecada; use
  `GenericJacksonJsonRedisSerializer` (Jackson 3, `tools.jackson`).
- Agent espera default typing out of the box — o serializer Jackson 3 vem com ele OFF; chame
  `.enableDefaultTyping(validator)` ou `@Cacheable` retorna `LinkedHashMap` e lança
  `ClassCastException`.
- Agent registra `JavaTimeModule` no mapper — Jackson 3 lida com `java.time` nativamente.
- Agent declara bean `ObjectMapper` genérico para customizar JSON — declare `JsonMapper` ou
  `JsonMapperBuilderCustomizer`.
- Migrando do Boot 3: chaves do Spring Session mudaram de `spring.session.redis.*` para
  `spring.session.data.redis.*`.
- Valkey vs Redis: a API é compatível, mas **não** use comandos Redis específicos de módulos
  (RediSearch, RedisJSON) sem confirmar suporte no Valkey. O básico (String, Hash, List, Set,
  Sorted Set, Stream) funciona igual.
