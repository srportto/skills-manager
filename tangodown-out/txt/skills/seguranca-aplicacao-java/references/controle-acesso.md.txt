# A01 — Broken Access Control

Leia este arquivo quando for implementar ou revisar autorização: ownership de recurso, IDOR, escalada de privilégio horizontal/vertical, `@PreAuthorize` e mass assignment em DTOs.

**Sintomas:** endpoint que confia no client para passar o `userId` na URL, ou que não verifica
ownership do recurso.

```java
// ERRADO - o proprio client informa o userId; o backend confia
@DeleteMapping("/users/{userId}/orders/{orderId}")
public void deletar(@PathVariable Long userId, @PathVariable Long orderId) {
    orderRepository.deleteById(orderId);
}

// CORRETO - o userId vem do token (autenticado), nao da URL; ownership e verificado
@DeleteMapping("/orders/{orderId}")
public void deletar(@PathVariable Long orderId, @AuthenticationPrincipal User user) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Pedido nao encontrado"));
    if (!order.belongsTo(user.id())) {   // vertical E horizontal
        throw new BusinessException("Pedido nao pertence ao usuario");
    }
    orderRepository.delete(order);
}
```

## Mass assignment

**Mass assignment:** DTOs de entrada não devem expor campos sensíveis (`role`, `id`, status
administrativo, `createdAt`) que o cliente não pode setar — use DTOs específicos por operação, não
a entidade JPA direto no `@RequestBody`. Exemplo: `AdminAtualizarUsuarioRequest` com campo `role`,
`CriarUsuarioRequest` sem.

```java
// ANTES - a entidade JPA vai direto no body: o cliente envia {"role":"ADMIN"} e se promove
@PutMapping("/usuarios/{id}")
public Usuario atualizar(@PathVariable Long id, @RequestBody Usuario usuario) {
    return usuarioRepository.save(usuario);
}

// DEPOIS - DTO sem campos privilegiados; role so muda no endpoint administrativo
public record AtualizarPerfilRequest(@NotBlank @Size(max = 200) String nome) {}

@PutMapping("/usuarios/me")
public UsuarioResponse atualizar(@Valid @RequestBody AtualizarPerfilRequest req,
                                 @AuthenticationPrincipal UsuarioAutenticado logado) {
    return usuarioService.atualizarNome(logado.id(), req.nome());   // id vem do token
}
```

## Autorização declarativa no método (defense in depth)

Além da checagem de ownership no domínio, proteja o método com regra declarativa — a rota na
`SecurityFilterChain` pode mudar, a anotação acompanha o código. Requer `@EnableMethodSecurity`.

```java
// ANTES - so a autenticacao protege; qualquer usuario logado le qualquer pedido
@GetMapping("/orders/{orderId}")
public OrderResponse buscar(@PathVariable Long orderId) {
    return orderService.buscar(orderId);
}

// DEPOIS - o bean de autorizacao confere o dono; suporte tem acesso por role
@PreAuthorize("hasRole('SUPORTE') or @pedidoAutorizacao.ehDono(#orderId, authentication)")
@GetMapping("/orders/{orderId}")
public OrderResponse buscar(@PathVariable Long orderId) {
    return orderService.buscar(orderId);
}
```

Responda **404** (e não 403) quando a existência do recurso de outro usuário não puder ser revelada.
