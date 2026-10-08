# EXJ eval 6 — execução A

Executor independente: `eval_exj_feature_a`. Avaliador: agente principal. Nota: **2/2**.

## Saída bruta

Skill: `arquitetura-limpa-java` + agent: `java-construtor`. No Codex, aplico a skill; o agent só pode ser acionado se estiver instalado no cliente.

Para implementar a consulta de status, preciso do repositório da aplicação Spring Boot ou dos arquivos relevantes: controller de pedidos, caso de uso ou service, modelo de pedido e acesso a dados. Também preciso saber se o endpoint deve receber o ID do pedido no caminho, por exemplo `GET /pedidos/{id}/status`, ou se a aplicação já define outro padrão.

Com esse contexto, adapto o endpoint à arquitetura existente e retorno o status encontrado ou o tratamento previsto para pedido inexistente.

## Justificativa

Seleciona `arquitetura-limpa-java` para a aplicação existente e pede código e contrato ausentes, sem gerar esqueleto.
