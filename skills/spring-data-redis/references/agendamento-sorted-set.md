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
// DEPOIS: lote limitado e posse decidida por ZREM (retorno 1 = esta instância ganhou o item)
Set<String> lote = redisTemplate.opsForZSet().rangeByScore(chaveAgenda, 0, agora, 0, 100);
for (String id : lote) {
    Long removido = redisTemplate.opsForZSet().remove(chaveAgenda, id);
    if (removido != null && removido == 1) {
        publicarNoStream(id); // efeito idempotente: se cair aqui, o item reaparece via reconciliação
    }
}
```

Para fazer varredura e move em um único passo atômico, veja [lua-atomicidade.md](lua-atomicidade.md).
Recuperação de pendências do consumo: [RecuperacaoPendencias](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/RecuperacaoPendencias.java).
