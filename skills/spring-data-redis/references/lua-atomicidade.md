# Lua para atomicidade

Leia este arquivo quando duas ou mais operações Redis precisarem ser atômicas (varredura + move, incremento + expiração).

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


## Antes/depois: ZRANGEBYSCORE + ZREM + XADD

```java
// ANTES: três idas ao servidor; entre o ZRANGE e o ZREM outra instância lê os mesmos itens
Set<String> vencidos = redisTemplate.opsForZSet().rangeByScore(chaveAgenda, 0, agora, 0, limite);
for (String id : vencidos) {
    redisTemplate.opsForZSet().remove(chaveAgenda, id);
    redisTemplate.opsForStream().add(chaveStream, Map.of("id_autorizacao", id)); // pode duplicar ou perder
}
```

```java
// DEPOIS: um único EVAL; o servidor executa o script inteiro sem intercalar outros comandos
Long movidos = redisTemplate.execute(script, List.of(chaveAgenda, chaveStream),
    String.valueOf(agora), String.valueOf(limite));
```

Scripts Lua bloqueiam o servidor enquanto rodam: mantenha-os curtos, com lote limitado. O mesmo princípio vale
para quota (`INCR` + `PEXPIRE`): [rate-limiting.md](rate-limiting.md). Prova com Valkey real:
[LimiteDistribuidoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java).
