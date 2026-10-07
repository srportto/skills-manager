---
name: testes-sistemas-java
description: "Definir e executar evidências de comportamento em sistemas Java: concorrência, idempotência, contratos, arquitetura, falhas e carga. Use quando implementar ou revisar esses comportamentos; não exigir testes que apenas repitam texto ou estrutura de documentação."
metadata:
  version: "1.0.0"
  related-skills: [arquitetura-limpa-java, resiliencia-controle-fluxo-java, revisao-de-codigo-java, mensageria-sqs-kafka]
---

# Testes de sistemas Java

Receba comportamento esperado, risco, fronteira e ambiente. Antes de escrever teste, diga qual defeito observável ele detecta. Use Java 25/JUnit Jupiter; preserve a biblioteca de asserções do projeto quando suficiente.

**Quando não usar:** refatoração puramente editorial não exige teste espelhando texto; benchmark não substitui requisito; auditoria de segurança dedicada pertence a seguranca-aplicacao-java.

## Fluxo

1. Separe unidade, contrato/arquitetura, integração e carga. Declare dependências externas.
2. Escreva uma prova que falha pelo comportamento ausente/incorreto, implemente e repita a suíte afetada.
3. Teste feliz, borda e falha conforme risco; relógio/aleatoriedade injetáveis, latches/barreiras para concorrência. Evite sleep como sincronização.
4. Execute comando e leia relatório. Não chamar testes pulados de aprovação.
5. Relate cenário, parâmetros, resultado, limites e pendências.

## Referências

- [Concorrência e resiliência](references/concorrencia-resiliencia.md): demanda, limites, cancelamento, retry e idempotência.
- [Contratos e arquitetura](references/contratos-arquitetura.md): compatibilidade, fronteiras e validação do catálogo.
- [Integração e carga](references/integracao-carga.md): containers, falhas, carga e recuperação.
- [Testes de slice Spring](references/testes-slice-spring.md): ler ao escolher o tipo de teste Spring (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`) ou montar integração com Testcontainers; AWS local usa Floci.

## Saída e responsáveis

Entregue arquivos, comandos reproduzíveis, contagens de sucesso/falha/skip e ambiente. Construtor escreve provas; revisor verifica relevância e resultados; chaos mede falhas controladas; DevOps aplica gates. Compare resultado real com o requisito, não apenas cobertura de linhas.

