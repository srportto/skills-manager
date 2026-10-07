# Rate limiting com Redis/Valkey

Leia este arquivo quando for implementar quota atômica por identidade com Redis/Valkey (janela fixa em Lua). A política (local × global, o que fazer se o limitador falhar) pertence a `resiliencia-controle-fluxo-java`.

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
[LimiteDistribuido](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/LimiteDistribuido.java).

Janela fixa permite até 2× o limite na virada da janela; se isso importa, use janela deslizante (sorted set ou
contadores ponderados) ou token bucket em Lua. Quota de aplicação não substitui proteção de borda (WAF/CDN)
contra DDoS.


## Antes/depois: INCR + EXPIRE

```java
// ANTES: duas chamadas; se o processo cair entre elas a chave fica sem TTL e o cliente é bloqueado para sempre
Long atual = redisTemplate.opsForValue().increment("ratelimit:" + tenant);
if (atual != null && atual == 1) {
    redisTemplate.expire("ratelimit:" + tenant, Duration.ofMinutes(1)); // pode nunca executar
}
return atual != null && atual <= 100;
```

```java
// DEPOIS: INCR + PEXPIRE no mesmo script Lua (LimiteDeTaxa acima) e identidade vinda da autenticação
boolean ok = limite.permitido(tenantAutenticado, 100, Duration.ofMinutes(1));
if (!ok) throw new LimiteExcedidoException(Duration.ofSeconds(30)); // vira 429 + Retry-After
```

Política (cota local × global, fail-open × fail-closed, `réplicas × burst local`) fica em
[capacidade e limites](../../resiliencia-controle-fluxo-java/references/capacidade-e-limites.md) — aqui só a
implementação Redis. Provas: [LimiteDistribuidoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoTest.java)
(fallback local) e [LimiteDistribuidoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java)
(Valkey real, concorrência e chave órfã).
