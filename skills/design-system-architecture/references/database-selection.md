# Seleção de persistência por operação

| Modelo | Uso comum | Pergunta decisiva |
|---|---|---|
| Relacional | Transações, restrições e joins | Quais invariantes devem ser atômicas? |
| Chave-valor | Acesso por chave, sessão/cache | Tamanho, TTL, hot keys e durabilidade? |
| Documento | Agregados com forma variável | Fronteiras do documento e transações entre documentos? |
| Colunar/wide-column | Consultas e armazenamento por padrão de acesso | Partição, cardinalidade e distribuição? |
| Grafo | Travessias de relações | Profundidade, fan-out e atualização? |
| Séries temporais | Tempo/retenção/agregação | Volume, cardinalidade e downsampling? |
| Busca | Texto e ranking | Consistência da indexação e fonte de verdade? |

Não iguale NoSQL a ausência de transações: MongoDB suporta transações multi-documento, com custos e restrições a avaliar. Relacional também exige decisões de isolamento, replicação e particionamento. Fonte: [transações MongoDB](https://www.mongodb.com/docs/manual/core/transactions/).

Por operação registre: chave/consulta, frequência, tamanho, crescimento, índice, consistência, retenção, atraso de réplica tolerado e estratégia de migração. Índice acelera leitura e custa escrita/espaço. Sharding não corrige query sem índice nem resolve hot key por si só.

Replicação para leitura exige política de read-your-writes e failover. SQL tuning pertence a banco-de-dados-performance; JPA/lock/transação em Java a persistencia-jpa. [Consistência distribuída](consistencia-distribuida.md) define as fronteiras de garantia.
