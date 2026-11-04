# Migração por versão

Leia este arquivo quando o código vem do Java 8/11/17/21 e é preciso saber o que já pode ser modernizado, o que falta para chegar ao 25 e qual armadilha existe no entrypoint do Spring Boot.

## 8. Guia de migração por versão

| Vindo do Java | Já pode usar | Ainda falta para chegar no 25 |
|---|---|---|
| **8** | lambdas, streams, `Optional` (já eram do próprio 8) | records, sealed classes, pattern matching, switch expressions, text blocks, `var`, virtual threads |
| **11** | tudo do 8 + `var` (inferência de tipo local, finalizada no LTS 11) | records, sealed classes, pattern matching, switch expressions, text blocks, virtual threads |
| **17** | tudo do 11 + records, sealed classes, switch expressions, text blocks, pattern matching para `instanceof` (todos finalizados até o LTS 17) | pattern matching para `switch`, record patterns, virtual threads (finalizados no LTS 21) |
| **21** | tudo do 17 + pattern matching completo para `switch`, record patterns, virtual threads (LTS 21 finaliza o Project Loom) | `_` para variáveis e padrões sem nome (JEP 456, final no 22); o resto desta skill o 21 já cobre |

**Nota JDK 25 + Spring Boot:** nenhuma feature desta lista é obrigatória ao migrar do 21 para o 25 —
o ponto de atenção é o entrypoint da aplicação. O JDK 25 introduz instance main methods (classe sem
nome, `void main()` sem `args`), mas o **plugin do Spring Boot ainda não suporta `void main()` do
JDK 25** — o `spring-boot-maven-plugin` (versão 4.0.7, fixa neste catálogo) exige o entrypoint
clássico para gerar o jar executável: com `void main()` o goal `repackage` falha com "Unable to find main class"
(verificado em 2026-10-06). Por isso a classe principal mantém `public static void main(String[] args)` dentro de
`@SpringBootApplication` — não troque por `void main()` em aplicações Spring Boot deste catálogo.


## Antes/depois: entrypoint Spring Boot no Java 25

```java
// ERRADO neste catálogo: instance main do JDK 25; o repackage do spring-boot-maven-plugin falha
// com "Unable to find main class"
@SpringBootApplication
class Aplicacao {
    void main() { SpringApplication.run(Aplicacao.class); }
}

// CORRETO: entrypoint clássico
@SpringBootApplication
public class Aplicacao {
    public static void main(String[] args) { SpringApplication.run(Aplicacao.class, args); }
}
```

## Roteiro de migração

1. Compile e rode os testes na versão atual; sem teste verde não há como provar que a modernização preservou comportamento.
2. Troque classes de dados por [records](records.md) e `instanceof` + cast por [pattern matching](pattern-matching.md).
3. Feche hierarquias de domínio com [sealed e switch exaustivo](sealed-e-switch.md).
4. Avalie [virtual threads](virtual-threads.md) só para carga I/O-bound.
5. Repita os testes e peça revisão ao `java-revisor`.
