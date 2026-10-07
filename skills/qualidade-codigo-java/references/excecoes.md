# Tratamento de excecoes

Leia este arquivo ao escrever `try/catch`, criar excecao de dominio, relancar erro ou abrir recursos (try-with-resources). Regras de causa preservada e mensagem util.

## Exception handling

- Use **unchecked exceptions** para erros de dominio (`BusinessException` - mapeada para 422 pelo
  handler central; ver `arquitetura-limpa-java`).
- **Crie excecoes especificas do dominio** (`MarketNotFoundException`) em vez de `RuntimeException`
  generica.
- **Evite** `catch (Exception ex)` amplo, a menos que seja para relancar/logar centralmente.
- **Sempre preserve a causa** (`throw new ApplicationException(msg, e)`) - perder a stack trace
  original torna investigacao quase impossivel.
- **Recursos** - sempre try-with-resources; `close()` manual nao executa se o codigo anterior lancar.
- **Mensagens de erro claras** - a mensagem deve dizer o que deu errado + identificador da
  operacao. Mensagens vagas forcam o agente a gastar turnos extras para descobrir a causa.

**[Codigo Nao Aderente]:**
```java
// perde a causa
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}
```

**[Violacao e Explicacao]:** perde a `Throwable cause` (stack trace original) e produz
mensagem generica sem contexto da operacao; investigacao quase impossivel depois.

**[Exemplo de Refatoracao]:**
```java
// especifica, com causa preservada
try {
    integracaoClient.enviar(pedido);
} catch (IOException e) {
    throw new ApplicationException("Falha ao enviar pedido " + pedido.id() + " para integracao", e);
}
```

