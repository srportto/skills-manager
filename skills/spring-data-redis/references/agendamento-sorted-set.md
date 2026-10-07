# Sorted set como agenda

Leia este arquivo quando precisar agendar itens por horário de vencimento (score = epoch millis) e varrer os vencidos.

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


## Antes/depois: varredura de vencidos

```java
// ANTES: lê todos os vencidos sem limite e remove um a um; duas instâncias processam o mesmo item
Set<String> vencidos = redisTemplate.opsForZSet().rangeByScore(chaveAgenda, 0, agora);
for (String id : vencidos) {
    processar(id);
    redisTemplate.opsForZSet().remove(chaveAgenda, id);
}
```

```java
// DEPOIS (recomendado): ZREM + XADD no mesmo script Lua (veja lua-atomicidade.md); não há janela entre os dois
Long movidos = redisTemplate.execute(script, List.of(chaveAgenda, chaveStream),
    String.valueOf(agora), "100"); // lote limitado; só move quem ganhou o ZREM
```

Alternativa sem Lua: **publicar primeiro** com `XADD` idempotente e só então `ZREM`. Se o processo cair entre
os dois, o item continua na agenda e será publicado de novo; o consumidor do stream deduplica por id
(duplicata possível, perda não). Nunca faça `ZREM` antes do `XADD` em passos separados: uma queda entre eles
perde o item.

```java
for (String id : redisTemplate.opsForZSet().rangeByScore(chaveAgenda, 0, agora, 0, 100)) {
    redisTemplate.opsForStream().add(chaveStream, Map.of("id_autorizacao", id)); // consumidor idempotente por id
    redisTemplate.opsForZSet().remove(chaveAgenda, id);
}
```

O script Lua do passo atômico está em [lua-atomicidade.md](lua-atomicidade.md).
Recuperação de pendências do consumo: [RecuperacaoPendencias](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/RecuperacaoPendencias.java).
