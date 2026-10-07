# A02 — Cryptographic Failures (senhas, dados em repouso)

Leia este arquivo quando for armazenar senha, escolher algoritmo de hash, proteger dado sensível em repouso (CPF, cartão) ou decidir onde guardar segredos.

**Regra de ouro:** hash de senha com **bcrypt** (cost ≥ 10) ou **argon2id** — nunca MD5/SHA-1/
SHA-256 unsalted, nunca reversível ("encrypt" simétrico de senha).

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

private static final int BCRYPT_COST = 12;   // >= 10, balanceia seguranca e performance

public String hashPassword(String plaintext) {
    return new BCryptPasswordEncoder(BCRYPT_COST).encode(plaintext);
}

public boolean verifyPassword(String plaintext, String hash) {
    return new BCryptPasswordEncoder(BCRYPT_COST).matches(plaintext, hash);
}
```

## Preferir `PasswordEncoder` como bean (delegating, com migração de algoritmo)

Em vez de instanciar o encoder em cada método, exponha um único bean. O
`DelegatingPasswordEncoder` grava o prefixo do algoritmo (`{bcrypt}`, `{argon2@SpringSecurity_v5_8}`),
o que permite trocar o algoritmo no futuro sem invalidar hashes antigos.

```java
// ANTES - SHA-256 sem salt: rapido demais e identico para senhas iguais
String hash = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8)));

// DEPOIS - bean unico, algoritmo adaptativo com salt embutido
@Configuration
class SenhaConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        // delegating: bcrypt por padrao, reconhece hashes antigos pelo prefixo
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // alternativa explicita (requer BouncyCastle no classpath):
        // return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}

// no login: re-hash transparente se o encoder considera o hash armazenado desatualizado
if (encoder.matches(senhaInformada, usuario.hash()) && encoder.upgradeEncoding(usuario.hash())) {
    usuario.atualizarHash(encoder.encode(senhaInformada));
}
```

## Dados em repouso

**Dados em repouso:** campos sensíveis (CPF, número de cartão) em coluna criptografada (ex.: JPA
`@Convert` com `AttributeConverter` via AES-GCM) ou vault/tokenização (nunca armazene PAN de
cartão sem tokenizar via gateway de pagamento).

Use IV/nonce **aleatório e único por registro** (AES-GCM com nonce repetido anula a segurança) e
guarde o IV junto do texto cifrado; a chave vem de KMS/vault, nunca do código.

## Segredos — onde guardar

**Nunca em código-fonte, `application.yml` versionado, ou logs.** Dev: `application-local.yml`
(no `.gitignore`) ou env vars. CI: GitHub Actions / Azure DevOps Secrets. Produção: Azure Key
Vault, AWS Secrets Manager, HashiCorp Vault, ou env var injetada pelo orquestrador (Kubernetes
`Secret` montado como env).

```java
// ERRADO - secret em codigo
private static final String JWT_SECRET = "minha-chave-secreta-123";

// CORRETO - secret de variavel de ambiente
@Value("${jwt.secret}")
private String jwtSecret;
```

```yaml
# application.yaml — referencia a variavel de ambiente, sem valor padrao
jwt:
  secret: ${JWT_SECRET}   # a subida falha se ausente: melhor que subir com segredo fraco
```
