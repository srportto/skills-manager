# Modelo de relatório de revisão

Use este modelo ao entregar uma revisão de código: primeiro os achados individuais, depois o agrupamento por severidade.

**Achados individuais** (um bloco por smell) seguem o padrão abaixo — mesmo formato usado pela
skill `qualidade-codigo-java`, garantindo leitura uniforme entre autor e revisor:

````markdown
**[❌ Código Não Aderente]:**
```java
// (trecho original contendo o code smell)
```

**[🚨 Violação e Explicação]:**
- qual principio foi quebrado (Refactoring Guru, Object Calisthenics, Clean Code for AI)
- impacto tecnico/financeiro (custo de janela de contexto do LLM, manutencao, etc.)

**[✅ Exemplo de Refatoração]:**
```java
// (versao corrigida, aplicando Extract Method / Replace Magic Number / Guard Clauses / etc.)
```
````

**Agrupamento por severidade** (o relatório final entrega os achados acima dentro desta
estrutura):

````markdown
## Revisão de Código: <componente/PR>

### Crítico
- **<título curto>** (`Arquivo.java:42`) — descrição do problema e por que bloqueia o merge.

### Importante
- **<título curto>** (`Arquivo.java:17`) — descrição e sugestão objetiva de correção.

### Menor
- **<título curto>** (`Arquivo.java:5`) — descrição (estilo/nomenclatura/preferência).

### Pontos positivos
- <prática correta observada, para reforçar>

### Evidência
| Verificação | Comando | Resultado |
|---|---|---|
| Compilação | `mvn -q compile` | ok / falhou |
| Testes unitários | `mvn test` | N passaram, M falharam, K pulados |
| Integração | `mvn -Pintegracao verify` | executado / **pendente** (motivo) |
| Carga | `mvn -Pcarga verify` | executado / **pendente** / não aplicável |

### Veredicto (modo auditoria)
APROVADO | REPROVADO (há Crítico) | **PENDENTE** (falta evidência executada para um risco relevante)
````

Omita uma seção inteira se não houver achados nela (não escreva "nenhum encontrado") — exceto
"Pontos positivos", que deve sempre trazer ao menos um item quando algo no código merece ser
reforçado.

