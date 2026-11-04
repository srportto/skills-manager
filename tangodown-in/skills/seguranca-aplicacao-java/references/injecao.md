# A03 — Injection (SQL, JPQL, NoSQL, command) e validação de entrada

Leia este arquivo quando escrever query (JPQL, SQL nativo, dinâmica), executar comando externo ou definir validação de entrada (Bean Validation) em DTO de request.

**Regra de ouro:** **nunca** concatene entrada do usuário em string de query (SQL nativo, JPQL)
nem em shell — vale para ambos os estilos de query abaixo.

```java
// ERRADO - concatenacao em JPQL/SQL nativo; abre brecha para injecao
@Query("FROM Pedido WHERE id = " + id)   // NUNCA FACA ISSO
@Query(value = "SELECT * FROM pedidos WHERE id = " + id, nativeQuery = true)

// CORRETO - parametro nomeado (JPQL ou SQL nativo)
@Query("FROM Pedido WHERE id = :id")
Optional<Pedido> buscarPorId(@Param("id") Long id);

@Query(value = "SELECT * FROM pedidos WHERE id = :id", nativeQuery = true)
Optional<PedidoEntity> buscarPorIdNativo(@Param("id") Long id);
```

Para queries dinâmicas com muitos filtros opcionais, use `Criteria` API ou `QueryDSL` em vez de
montar string.

```java
// ANTES - JdbcTemplate com concatenacao (entrada ilustrativa: x' OR '1'='1)
jdbc.queryForList("SELECT * FROM usuarios WHERE email = '" + email + "'");

// DEPOIS - placeholder: o driver trata o valor sempre como dado, nunca como SQL
jdbc.queryForList("SELECT * FROM usuarios WHERE email = ?", email);

// DEPOIS (ordenacao dinamica) - placeholder NAO vale para identificador; use allowlist fechada
private static final Set<String> ORDENAVEIS = Set.of("nome", "criado_em");

String ordem = ORDENAVEIS.contains(campo) ? campo : "criado_em";
jdbc.queryForList("SELECT * FROM usuarios ORDER BY " + ordem + " LIMIT ?", limite);
```

**Command injection:** nunca passe entrada do usuário como argumento de `Runtime.exec()`/
`ProcessBuilder` sem validação rigorosa (whitelist de caracteres, allowlist de comandos).

```java
// ANTES - monta uma linha de shell com entrada do usuario
Runtime.getRuntime().exec("sh -c convert " + nomeArquivo + " saida.png");

// DEPOIS - sem shell; argumentos separados, nome validado por padrao restrito
if (!nomeArquivo.matches("[A-Za-z0-9_-]{1,64}\\.png")) {
    throw new BusinessException("Nome de arquivo invalido");
}
new ProcessBuilder("convert", nomeArquivo, "saida.png").start();
```

## Validação de entrada (Bean Validation)

Toda entrada do client passa por `@Valid` no DTO de request (ver `api-rest-design`). Nunca confie
em validação só no client — o backend sempre revalida.

```java
public record CriarUsuarioRequest(
    @NotBlank @Size(max = 200) String nome,
    @NotBlank @Email @Size(max = 254) String email,    // max 254 (RFC 5321)
    @NotBlank @Size(min = 8, max = 128) String senha, // min 8 impede senhas triviais
    @Pattern(regexp = "\\d{11}") String cpf             // formato, mas regra de negocio fica no service
) {}
```

Validação de entrada reduz a superfície de XSS e injeção, mas **não substitui** query
parametrizada nem escape na saída: valide na borda **e** parametrize/escape no destino.
