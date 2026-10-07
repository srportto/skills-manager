# Configuração e serialização (Redis/Valkey)

Leia este arquivo quando for adicionar dependências, configurar `RedisTemplate`/`RedisCacheManager`, escolher serializer Jackson 3, definir convenção de chaves ou ajustar o `application.yml`.

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

## Antes/depois: serialização de valores

Antes: o `RedisTemplate` padrão usa serialização Java (JDK) — o valor fica binário, ilegível no `redis-cli`,
acoplado ao `serialVersionUID` da classe e é vetor de desserialização insegura.

```java
// ANTES: sem serializers explícitos -> chave e valor em bytes do JDK
RedisTemplate<String, Object> template = new RedisTemplate<>();
template.setConnectionFactory(factory);
template.opsForValue().set("pedidos:pedido:42", pedido); // exige Serializable; "\xac\xed\x00\x05..."
```

```java
// DEPOIS: chave em String e valor em JSON (Jackson 3), com default typing restrito a pacotes confiáveis
template.setKeySerializer(new StringRedisSerializer());
template.setValueSerializer(GenericJacksonJsonRedisSerializer.builder()
    .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.exemplo.").build())
    .build());
template.opsForValue().set("pedidos:pedido:42", PedidoResponse.de(pedido), Duration.ofMinutes(5));
```

Cacheie DTO imutável (`record`), nunca entidade JPA. Prova executável do uso de cache com Valkey real:
[CacheProtegidoTest](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/CacheProtegidoTest.java).
