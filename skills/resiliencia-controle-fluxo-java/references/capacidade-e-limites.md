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

## Rate limiting distribuído

Use uma identidade obtida da autenticação (por exemplo, `tenant` validado + nome da operação). Não derive a
quota de um IP sem autenticação: endereços podem ser compartilhados por NAT, mudam em redes móveis e podem ser
forjados quando um proxy não confiável preenche o cabeçalho. Normalize a identidade no servidor e mantenha o
conjunto de chaves e seu TTL limitados.

Para uma janela fixa no Redis/Valkey, execute `INCR` e `PEXPIRE` em um script Lua atômico; uma falha entre dois
comandos deixaria contador sem TTL. O script também deve corrigir chave existente com `PTTL < 0`. Uma janela
fixa pode admitir rajada na fronteira entre janelas, então escolha algoritmo e janela conforme o contrato da
quota.

Separe os orçamentos local e global. A quota global do coordenador limita o agregado normal entre réplicas; o
balde local protege cada processo quando o coordenador falha. O limite degradado agregado pode chegar a
`réplicas máximas × burst local`, portanto dimensione o burst local com o pior número de réplicas e deixe
explícito que a decisão local não garante atomicidade global.

Na falha do coordenador, não libere toda a capacidade (fail-open) nem bloqueie todo o serviço (fail-closed):
use limite local conservador, com capacidade e taxa limitadas. Exponha falhas como `app.ratelimit.falhas_coordenador`
sem tags de tenant, chave ou IP. O exemplo [LimiteDistribuido](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/LimiteDistribuido.java)
expõe o contador local em `falhasCoordenador()`; sua integração com Micrometer deve manter cardinalidade fixa.
Confira concorrência, expiração e recuperação em
[LimiteDistribuidoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/LimiteDistribuidoExternoIT.java).

## Prova

O [módulo fundamentos](../../../examples/java/fundamentos/pom.xml) mede máximo ativo e fila limitada; [carga](../../../examples/java/carga/pom.xml) registra aceitação/rejeição e recuperação. Esses ensaios são sintéticos; não extrapole throughput de uma máquina para produção.


## Exemplos executáveis

- Admissão com reserva para fluxo crítico e rejeição por deadline insuficiente:
  [AdmissaoPorPrioridade](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/AdmissaoPorPrioridade.java).
- Ritmo constante com fila de espera limitada (leaky bucket):
  [LeakyBucket](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/LeakyBucket.java).
- Taxa com burst limitado e isolamento por tenant (um balde por chave):
  [TokenBucket](../../../examples/java/fundamentos/src/main/java/br/com/srportto/exemplos/TokenBucket.java).
