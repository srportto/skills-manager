# Consistência e dados distribuídos

Comece pela operação: o usuário precisa ler sua escrita, evitar cobrança duplicada, tolerar inventário stale ou manter ordem por agregado? A resposta vale mais que escolher uma tecnologia por categoria.

CAP descreve, durante partição de rede, tensão entre consistência linearizável e disponibilidade de respostas em todos os nós não falhos. Não é um menu irrestrito de dois atributos. PACELC acrescenta a troca entre latência e consistência quando não há partição. ACID descreve propriedades transacionais; o C de ACID não é sinônimo do C de CAP. BASE é uma abordagem ampla, não um nível de isolamento.

## Replicação e particionamento

Leader/follower facilita ordem de escrita, mas réplica atrasada pode violar read-your-writes. Multi-leader exige política de conflito e impacto de relógio/ordem. Quorum depende de interseção, protocolo, falhas e leitura/escrita; fórmula isolada não comprova linearizabilidade.

Exemplo de quorum (sistemas estilo Dynamo/Cassandra): N=3 réplicas, escrita confirmada com W=2 e leitura com
R=2 → R + W > N garante que leitura e escrita se cruzam em pelo menos uma réplica. Isso **não** basta, sozinho,
para linearizabilidade (relógios, read repair, hinted handoff e falhas parciais importam); com W=1 e R=1 a
latência cai e a leitura pode voltar dado antigo.

**Por operação, não por banco:** saldo e cobrança exigem consistência forte e idempotência; catálogo de
produtos tolera minutos de atraso; contador de curtidas tolera aproximação. Escreva a tolerância (ex.: "leitura
de catálogo pode estar até 60 s desatualizada") e como ela é observada.

Sharding reduz conjunto por nó, mas adiciona roteamento, transações cruzadas e rebalance. Escolha chave com distribuição e localidade; detecte hot keys/tenants. Plano de resharding inclui convivência de rotas, cópia, validação, corte e rollback.

## Efeitos distribuídos

Banco + broker não viram transação única por publicar depois do commit. Outbox grava efeito e intenção juntos, relay publica ao menos uma vez, consumidor deduplica com inbox/efeito transacional. Saga compensa ações; compensação pode falhar e não é rollback ACID global. Documente estados pendentes, reconciliação e operação humana.

Exemplo executável Java: [ProcessadorIdempotente](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/ProcessadorIdempotente.java). O exemplo usa mesmo banco para idempotência/efeito/outbox; pagamento remoto exige chave no provedor e reconciliação adicional.

Teste perda de resposta após commit, duplicata concorrente, conflito de payload, leitura em réplica atrasada, falha do relay e replay. Migração usa expand/contract: adicionar campo compatível, migrar dados, conviver leitores, mudar produtor e remover contrato antigo só após janela acordada.

