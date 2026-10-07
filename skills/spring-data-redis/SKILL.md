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
  related-skills: arquitetura-limpa-java, persistencia-jpa, mensageria-sqs-kafka, resiliencia-controle-fluxo-java
---

# Spring Data Redis / Valkey

Referência para integração **Redis/Valkey** em Java/Spring Boot. Valkey é um fork do Redis
(API compatível), então `spring-boot-starter-data-redis` funciona para ambos. Usos cobertos: cache
protegido, quota distribuída atômica, sorted sets como agenda e streams como fila de trabalho com consumer
groups. (No monorepo de origem, um serviço de expiração de autorizações usava agenda + stream — contexto
externo, citado só como exemplo.)

## Quando usar / Quando NÃO usar

- **Usar:** cache-aside/`@Cacheable`, quota por identidade, agenda por horário, fila de trabalho com ack e
  recuperação, atomicidade entre comandos Redis.
- **NÃO usar:** mensageria SQS/Kafka (ver `mensageria-sqs-kafka`); cache de segundo nível JPA (ver
  `persistencia-jpa`); política de resiliência (deadline, retry, local × global) → `resiliencia-controle-fluxo-java`.

## Entradas

Dado a guardar (formato, tamanho, tolerância a staleness); TTL; identidade da quota; volume e duração do pico;
o que acontece se o Redis cair (fail-open, fail-closed ou limite local); versão do Redis/Valkey (≥ 6.2 para
`XAUTOCLAIM`).

## Decisão

| Necessidade | Estrutura | Reference |
|---|---|---|
| Evitar reconsulta a fonte lenta | String + TTL (cache-aside / `@Cacheable`) | `references/cache.md` |
| Chave quente / queda do cache | `sync = true`, jitter, limite de concorrência | `references/cache.md`, `references/cache-protecao-java.md` |
| Quota por tenant/cliente | `INCR` + `PEXPIRE` atômico (Lua) | `references/rate-limiting.md` |
| Executar item no horário | Sorted set (score = epoch millis) | `references/agendamento-sorted-set.md` |
| Fila de trabalho com ack e recuperação | Stream + consumer group | `references/streams-consumer-group.md` |
| Duas operações que não podem intercalar | Script Lua | `references/lua-atomicidade.md` |

## Passo a passo

- [ ] Declare dependências, serializers (JSON, nunca JDK) e `application.yml` — `references/configuracao-serializacao.md`.
- [ ] Defina a convenção de chaves `{app}:{dominio}:{id}` e **TTL em toda chave** (com jitter).
- [ ] Cacheie DTOs, não entidades; decida negative caching — `references/cache.md`.
- [ ] Quota: identidade autenticada, script atômico, política de falha do Redis decidida em `resiliencia-controle-fluxo-java`.
- [ ] Stream: `XACK` só depois do efeito; recuperação com `XAUTOCLAIM`; retenção (`MAXLEN`/`MINID`).
- [ ] Prove com Valkey real (`examples/java/integracao`, perfil `integracao`).

## Saída

Configuração Redis (beans + YAML), código de cache/quota/agenda/stream com TTL e ack definidos, e a prova
executada (teste ou IT contra Valkey), com a decisão sobre indisponibilidade do Redis registrada.

## Validação

- Teste de integração com Valkey real: [CacheProtegidoTest](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java),
  [LimiteDistribuidoExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java),
  [RecuperacaoPendenciasExternoIT](../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/RecuperacaoPendenciasExternoIT.java).
- Nenhuma chave sem TTL (exceto agenda/stream com retenção definida); valor legível no `redis-cli`.

## Gotchas (armadilhas)

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

## Guia de references

| Arquivo | Quando ler |
|---|---|
| [configuracao-serializacao.md](references/configuracao-serializacao.md) | Dependências, `RedisTemplate`, `RedisCacheManager`, Jackson 3, chaves e `application.yml` |
| [cache.md](references/cache.md) | `@Cacheable`/`@CachePut`/`@CacheEvict`, cache-aside manual, stampede |
| [cache-protecao-java.md](references/cache-protecao-java.md) | Cache local × distribuído, queda do cache, invalidação após commit |
| [rate-limiting.md](references/rate-limiting.md) | Quota atômica por identidade com Lua |
| [agendamento-sorted-set.md](references/agendamento-sorted-set.md) | Agenda por vencimento com sorted set |
| [streams-consumer-group.md](references/streams-consumer-group.md) | Fila de trabalho: grupo, `XACK`, `XAUTOCLAIM`, retenção |
| [lua-atomicidade.md](references/lua-atomicidade.md) | Varredura + move atômicos em script Lua |

## Quem aplica o quê

| Papel | Uso desta skill |
|---|---|
| Sessão principal / `java-construtor` | Implementa cache, quota, agenda ou stream conforme a decisão |
| `java-revisor` | Confere TTL, serializer, ack após efeito e política de falha do Redis |
| `resiliencia-controle-fluxo-java` | Dona da política de resiliência (local × global, falha do limitador) |
