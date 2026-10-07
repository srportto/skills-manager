# Logs: o que logar em cada camada e erros comuns

Leia este arquivo ao decidir o que registrar em `domain`, `application` e `infrastructure` de uma app hexagonal, e ao revisar logs com defeitos típicos (log duplicado, concatenação ansiosa, payload com PII). O formato e os níveis estão em `logs-estruturados.md`.

## 5. O que logar em cada camada hexagonal

| Camada | O que logar | O que NÃO logar aqui |
|---|---|---|
| `infrastructure` — driving adapters (`web/`, `messaging/`) | Chegada e saída da requisição/mensagem; `traceId` gerado ou recebido; status HTTP retornado ou confirmação de consumo da mensagem | Corpo completo de payload sensível; stack trace de exceção (isso é do handler central) |
| `application/usecase` | Decisões de negócio relevantes e seus ids (`"pedido duplicado ignorado id={}"`, `"pedido processado id={} valor={}"` — como em `ProcessarPedidoService`) | Log em todo método só por rotina; dado pessoal/sensível (regra de ouro, seção 1) |
| `domain` | **Nada.** Domínio é puro — não importa SLF4J, não conhece logging, é testável sem subir nenhum contexto de log | Qualquer log — se uma regra de domínio "precisa" logar, o log pertence a quem chama, no use case |
| `infrastructure` — driven adapters (`persistence/`, `external/`) | Falha de integração com o recurso externo e o identificador da chamada, antes de traduzir para exceção de domínio | Repetir o log que o use case já emitiu para a mesma decisão |
| Handler central (`ApiExceptionHandler`, em `infrastructure/web/`) | A exceção completa, com stack trace, **uma única vez**, no ponto central de tratamento, antes de montar a resposta de erro | Logar a mesma exceção de novo em outro lugar do fluxo — ver "log duplicado" na seção 6 |

## 6. Erros comuns

### Log duplicado (log e relança — "log-and-rethrow")

```java
// ERRADO - loga no service E de novo no handler central: o mesmo erro aparece duas vezes no log
try {
    pedidoRepository.save(pedido);
} catch (Exception e) {
    log.error("Erro ao salvar pedido {}", pedido.id(), e);
    throw new ApplicationException("Falha ao salvar pedido", e);
}

// CORRETO - so relanca com contexto; quem loga (uma vez, com stack trace) e o handler central (shared)
try {
    pedidoRepository.save(pedido);
} catch (Exception e) {
    throw new ApplicationException("Falha ao salvar pedido " + pedido.id(), e);
}
```

### Outros erros comuns

| Anti-padrão | Por que é errado | Correção |
|---|---|---|
| Log dentro de loop quente (`log.info` por item, 10 mil itens = 10 mil linhas) | Explode volume de log para a mesma operação, sem valor extra | Um `log.info` antes/depois do loop com o total; detalhe por item só em `debug`, sob `if (log.isDebugEnabled())` |
| `e.printStackTrace()` | Vai para stdout sem nível, sem timestamp, fora do JSON — invisível para qualquer ferramenta | `log.error("mensagem", e)` — vira evento ERROR estruturado, stack trace no campo de exceção do JSON |

### Concatenação ansiosa (eager)

Placeholder evita a concatenação de string, mas **não** evita a avaliação do argumento em si — o Java
avalia os argumentos antes de chamar `log.debug(...)`, então uma chamada cara (serialização, outro
método pesado) roda de qualquer forma, mesmo com `DEBUG` desligado, se estiver como argumento direto:

```java
// ERRADO - a concatenacao E a serializacao rodam sempre, mesmo com DEBUG desligado
log.debug("Payload recebido: " + objectMapper.writeValueAsString(pedido));

// CORRETO - argumento simples (toString barato): placeholder sozinho ja resolve
log.debug("Processando pedido {} valor {}", pedido.id(), pedido.valor());

// CORRETO - argumento caro (serializacao): guarda com isDebugEnabled() alem do placeholder
if (log.isDebugEnabled()) {
    log.debug("Payload recebido: {}", objectMapper.writeValueAsString(pedido));
}
```

