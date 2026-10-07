# A04 — Insecure Design

Leia este arquivo quando desenhar fluxos de recuperação de conta, login, endpoints administrativos ou qualquer fluxo em que a resposta possa vazar informação (enumeração de usuários, IDOR).

Padrões inseguros por design (ex.: fluxo de "esqueci senha" que revela se email existe; endpoint
de admin sem rate limit; IDOR — Insecure Direct Object Reference).

```java
// ERRADO - "esqueci senha" revela se o email existe (informacao vaza)
if (userRepository.existsByEmail(email)) {
    sendResetLink(email);
    return "Link enviado se o email existir";  // diferente se nao existe = info leak
} else {
    return "Link enviado se o email existir";
}

// CORRETO - resposta identica independente da existencia
sendResetLinkIfExists(email);   // sempre, sem condicionar a resposta
return "Se o email estiver cadastrado, um link sera enviado";
```

## Login sem enumeração de usuário

A mensagem **e o tempo de resposta** não podem distinguir "usuário inexistente" de "senha errada".

```java
// ANTES - mensagens e tempos diferentes denunciam quais e-mails existem
Usuario u = repo.findByEmail(email)
        .orElseThrow(() -> new CredenciaisException("Usuario nao encontrado"));
if (!encoder.matches(senha, u.hash())) throw new CredenciaisException("Senha incorreta");

// DEPOIS - mesma mensagem; um hash "isca" mantem o custo de CPU parecido quando o usuario nao existe
// (calculado uma vez na subida com o mesmo encoder: private final String hashIsca = encoder.encode("isca");)
Optional<Usuario> usuario = repo.findByEmail(email);
String hashComparado = usuario.map(Usuario::hash).orElse(hashIsca);
boolean senhaOk = encoder.matches(senha, hashComparado);
if (usuario.isEmpty() || !senhaOk) {
    throw new CredenciaisException("Credenciais invalidas");   // generica
}
```

## Token de reset de senha

Token aleatório criptograficamente forte (`SecureRandom`, ≥ 128 bits), de **uso único**, com
expiração curta (ex.: 15–30 min), armazenado como **hash** no banco — nunca em claro.
