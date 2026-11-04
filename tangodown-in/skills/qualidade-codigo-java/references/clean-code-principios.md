# Clean code - principios (DRY, KISS, YAGNI)

Leia este arquivo ao escrever ou limpar codigo Java e suspeitar de duplicacao de regra, abstracao especulativa (interface com implementacao unica) ou excesso de engenharia. Traz o formato antes/depois de cada principio.

## Clean code - principios com exemplo

> **Coesao com `revisao-de-codigo-java`:** esta skill e o "lado ativo" (o **como** aplicar cada
> refactoring). A `revisao-de-codigo-java` e o "lado passivo" (o **o que** revisar com checklist
> e severidades). Mesmo formato de exemplo (Codigo Nao Aderente / Violacao e Explicacao /
> Exemplo de Refatoracao), mesmas terminologias (`Magic Number`, `Primitive Obsession`, `Guard
> Clause`, `Tell Don't Ask`).

> **Principio-mestre (Clean Code for AI):** alem de bom para humanos, todo codigo deste
> catalogo deve estar **otimizado para a janela de contexto do LLM** - nomes grepaveis,
> metodos curtos, arquivos pequenos, tipos explicitos e comentarios "por que". Cada secao
> abaixo reforca esse objetivo.

### DRY - Don't Repeat Yourself

**[Codigo Nao Aderente]:**
```java
// logica de validacao duplicada em dois metodos
public void criarUsuario(UsuarioRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Email invalido");
    }
}

public void atualizarUsuario(UsuarioRequest req) {
    if (req.getEmail() == null || !req.getEmail().contains("@")) {
        throw new ValidationException("Email invalido");
    }
}
```

**[Violacao e Explicacao]:** mesma validacao em 2 lugares - a 3a ocorrencia (em
`importarEmLote`, por exemplo) confirma o padrao. Manter a duplicacao significa N lugares para
corrigir quando a regra mudar.

**[Exemplo de Refatoracao]:**
```java
// fonte unica: metodo privado resolve sem criar interface/factory para o futuro
public class UsuarioService {
    public void criarUsuario(UsuarioRequest req)  { validarEmail(req.getEmail()); /* ... */ }
    public void atualizarUsuario(UsuarioRequest req) { validarEmail(req.getEmail()); /* ... */ }

    private void validarEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Email invalido");
        }
    }
}
```

> **DRY com bom senso:** regra das 3 ocorrencias - na 1a e 2a, duplicar pode ser mais barato que a
> abstracao errada; extraia na 3a. Nao crie `EmailValidator` com interface e implementacao unica "para
> o futuro" - abstracao especulativa e over-engineering (ver `padroes-de-projeto-java`, secao "Quando
> NAO aplicar pattern").

### KISS - Keep It Simple / YAGNI - You Aren't Gonna Need It

**[Codigo Nao Aderente]:**
```java
// sobre-engenharia para 1 implementacao, sem segunda variacao a vista
public interface UserFactory {
    User createUser();
}
public class ConcreteUserFactory implements UserFactory {
    public User createUser() { return new User(); }
}
```

**[Violacao e Explicacao]:** interface + implementacao unica **"para o futuro"** e a abstracao
especulativa classica (YAGNI). O custo (mais arquivos para ler, mais para o agente raciocinar)
nao traz beneficio enquanto houver 1 variante.

**[Exemplo de Refatoracao]:**
```java
// chamada direta; implemente a abstracao quando a segunda variacao aparecer de fato
public User createUser() { return new User(); }
```

