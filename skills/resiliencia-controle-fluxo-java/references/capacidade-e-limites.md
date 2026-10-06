# Capacidade, filas e limites

Declare taxa média/pico (itens/s), duração do pico, payload máximo (bytes), tempo médio e percentis, concorrência e capacidade downstream. Em regime estável, Lei de Little: L = λ × W, usando médias e uma fronteira consistente. Não misture pico com p99 como se fossem médias.

Exercício: 1.000 itens/s chegam, 800 itens/s saem, durante 10 s: acumulam 2.000 itens. Com 2 KiB por item, são 3,91 MiB de payload, além de objetos, índices e buffers. Uma fila de 500 itens enche em 2,5 s nesse cenário. Ela precisa parar a origem, rejeitar ou persistir excedentes. Se a taxa cair para 600/s, o excedente de capacidade de 200/s drena 2.000 itens em 10 s, sem considerar novas falhas.

Dimensione itens e bytes; imponha tamanho máximo de item antes de alocar/gravar. Tenha limite de idade/deadline para não processar trabalho inútil. Buffer absorve pico finito, não resolve déficit sustentado.

## Recursos compartilhados

Conexões máximas da aplicação = réplicas máximas × pool por réplica + jobs/admin/replicação. Compare com orçamento seguro do banco, não apenas max_connections. Acrescente tempo de aquisição, consulta e transação; não retenha conexão durante backoff.

Um Semaphore por objeto não cria quota global. Compartilhe uma instância por recurso na JVM e documente a soma das instâncias. Separe reservas para checkout e relatório se suas criticidades diferirem. Admissão local protege o processo; quota por tenant pode exigir coordenação distribuída.

## Rejeição e justiça

Quota do cliente: 429 e Retry-After quando houver orientação útil. Saturação/indisponibilidade: 503 conforme contrato. Registre requisições lógicas aceitas/rejeitadas separadas de tentativas. Load shedding deve ocorrer cedo; prioridade não pode criar starvation silenciosa. Rate limiting de aplicação não protege sozinho contra DDoS de rede.

Token bucket permite burst limitado e reposição por tempo monotônico; leaky bucket regula ritmo, mas sua fila também é limitada. Teste fronteira, rollover temporal, tenant ruidoso e falha do coordenador.

## Prova

O [módulo fundamentos](../../../examples/java/fundamentos/pom.xml) mede máximo ativo e fila limitada; [carga](../../../examples/java/carga/pom.xml) registra aceitação/rejeição e recuperação. Esses ensaios são sintéticos; não extrapole throughput de uma máquina para produção.


## Exemplos executáveis

- Admissão com reserva para fluxo crítico e rejeição por deadline insuficiente:
  [AdmissaoPorPrioridade](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/AdmissaoPorPrioridade.java).
- Ritmo constante com fila de espera limitada (leaky bucket):
  [LeakyBucket](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/LeakyBucket.java).
- Taxa com burst limitado e isolamento por tenant (um balde por chave):
  [TokenBucket](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/TokenBucket.java).
