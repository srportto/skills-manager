# Parâmetros da aplicação e container web

Leia este arquivo quando precisar definir **nome, variante, pasta destino, porta, profile, carga esperada** ou trocar o container web (Tomcat/Jetty) antes de gerar a aplicação.

## Parâmetros: pergunte só o que falta

Use o que o pedido já informou. **Nome da aplicação** e **variante** não têm default seguro: se faltarem,
pergunte. Os demais têm default declarado — aplique-o sem perguntar e liste na entrega os valores assumidos,
para o usuário poder corrigir.

| Parâmetro | Uso | Default (se não informado) |
|-----------|-----|---------|
| **Nome da aplicação** | deriva `artifactId`, pacote `br.com.srportto.<nome>`, classe `<Nome>Application`, `spring.application.name` | — (perguntar) |
| **Variante** | base pura ou uma das 6 variantes — ver tabela abaixo | — (perguntar; "só um CRUD" = `rest-crud-banco`) |
| **Nome da pasta destino** | diretório onde o projeto será gerado | `<nome>-service` |
| **Porta** | `server.port` | `8080` |
| **Profile default** | `spring.profiles.default` | `local` |
| **Container web** | Tomcat (default) ou Jetty — ver abaixo | Tomcat |
| **Carga esperada** | taxa/pico, dependências — decide limites e proteções | baixa; proteções mínimas de borda |

> Derive os identificadores do "nome da aplicação": pacote = `br.com.srportto.<nome>` (minúsculo),
> classe principal = `<Nome>Application` (PascalCase).

### Container web

`spring-boot-starter-webmvc` traz **Tomcat** por padrão.

| Container | Quando preferir |
|-----------|------------------|
| **Tomcat** (default) | Sem alteração; máxima compatibilidade, maior base de troubleshooting. |
| **Jetty** | Cloud-native/containers, alta concorrência, muitos WebSockets/streaming. |

> ⚠️ **Undertow NÃO existe no Spring Boot 4.x** (o BOM só gerencia Tomcat e Jetty para web MVC, mais
> reactor-netty para reativo). `spring-boot-starter-undertow` falha com "version is missing".

Para Jetty, exclua o Tomcat do starter web e adicione o starter Jetty:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```
> Após trocar o container, valide no log de startup a linha do servidor ativo (`Jetty started on port
> <porta>` em vez de `Tomcat started on port <porta>`).

