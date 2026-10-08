# EXJ eval 6 — execução B

Executor independente: `eval_exj_feature_b`. Avaliador: agente principal. Nota: **2/2**.

## Saída bruta

Skill: `arquitetura-limpa-java` + agent: `java-construtor`. No Codex, aplico a skill; o agent só pode ser acionado se estiver instalado no cliente.

Para a consulta, proponho `GET /pedidos/{pedidoId}/status`. Em uma estrutura hexagonal, o controller em `infrastructure/web` chamaria uma porta de entrada em `domain/port/in`; o caso de uso consultaria a porta de saída de pedidos, e o adaptador de persistência faria a leitura.

Preciso do repositório ou dos arquivos relevantes da aplicação Spring Boot para implementar o endpoint seguindo a estrutura existente. O workspace disponível contém o catálogo de skills, não o código dessa aplicação.

## Justificativa

Seleciona o par correto, apresenta um desenho compatível com uma feature local à aplicação existente e pede o código para implementar sem presumir acesso ao projeto.
