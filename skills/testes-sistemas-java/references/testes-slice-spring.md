Leia este arquivo ao decidir o tipo de teste de uma aplicação Spring (unitário, slice web/JPA, integração, E2E) ou ao montar um teste de integração com Testcontainers. Para serviços AWS locais o catálogo usa Floci (ver `docs/catalogo/convencoes.md`, seção "AWS local").

# Testes

## Tipos de teste

| Tipo | Anotação Spring | Velocidade | Quando usar |
|---|---|---|---|
| Unitário | (nenhuma) | Muito rápido | Lógica pura, sem Spring |
| Slice — web | `@WebMvcTest` | Rápido | Controller isolado, mocka service |
| Slice — JPA | `@DataJpaTest` | Médio | Repository, com H2/Testcontainers |
| Integração | `@SpringBootTest` | Lento | Contexto completo, smoke test |
| E2E | Testcontainers + cliente HTTP | Muito lento | Validação fim a fim |

## Testcontainers (preferido para integração com infra real)

```java
@SpringBootTest
@Testcontainers
class OrderServiceIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("pedidos").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void deveProcessarPedidoQuandoDadosValidos() { /* ... */ }
}
```

> **Mock `@MockBean` foi removido no Boot 4** — use `@MockitoBean` em vez disso.
