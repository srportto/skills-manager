# Capacidade, SLIs, SLOs e orçamento de recursos

Estimativas "back-of-the-envelope" servem para **descartar desenhos inviáveis e dimensionar limites**, não para
prometer SLO. Cada número tem unidade, fonte (medido, contrato, hipótese) e escopo (por instância, por
réplica somada, por tenant).

## 1. Levantamento por operação

| Campo | Pergunta | Exemplo |
|---|---|---|
| Taxa média | Operações/dia ÷ 86.400 | 4,32 M/dia → 50/s |
| Pico | Fator e **duração** do pico (hipótese separada) | 400/s por 15 min |
| Item | Tamanho típico e **máximo** aceito | 2 KiB típico, 64 KiB máximo |
| Leitura:escrita | Proporção e consistência exigida em cada lado | 20:1 |
| Retenção/crescimento | Quanto tempo manter e quanto cresce | 5 anos; +30%/ano |
| Latência | Distribuição medida (p50/p95/p99), não só média | p50 40 ms, p99 180 ms |
| Dependências | Capacidade e limites de cada downstream | provedor: 100 req/s por conta |

## 2. Fórmulas

- **Armazenamento lógico** = itens/dia × bytes/item × dias de retenção; somar índices (comumente 30–100% do
  dado, medir), réplicas e overhead de formato.
- **Banda** = bytes/requisição × requisições/s, por direção; multiplicar por fan-out quando um evento gera N
  entregas. Considere compressão apenas se medida.
- **Memória de fila** = itens × (payload + overhead por objeto). Limite por **itens e bytes**, e por idade
  (deadline do item).
- **Lei de Little** (regime estável, mesma fronteira para as três grandezas): `L = λ × W`.
  Concorrência média = vazão × tempo médio no sistema. Ex.: 400 req/s × 0,08 s = 32 requisições ativas em
  média. Não use p99 como W para obter média; para dimensionar **folga**, avalie separadamente o percentil
  sob carga e reserve margem.
- **Não multiplique cegamente pico × p99.** Os dois raramente coincidem de forma independente; meça a
  latência sob a carga de pico ou simule.

## 3. Orçamento de conexões e concorrência

```
conexões da aplicação = réplicas máximas × pool por réplica
                      + jobs/batch + ferramentas administrativas + replicação
                      ≤ orçamento seguro do banco (não apenas max_connections)
```

Exemplo: 6 réplicas × 20 = 120; jobs 10; admin 5 → 135 ≤ 150 aceitos pelo banco. Autoscaling até 10 réplicas
levaria a 215: o máximo de réplicas ou o pool precisam de teto coerente. Espera para obter conexão também tem
limite (ex.: 200 ms) e falha de forma visível. Virtual threads não criam conexões: só tornam barato esperar.

## 4. Orçamento de tempo (deadline)

Deadline ponta a ponta definido pelo cliente/contrato; cada salto consome parte e propaga o **restante**:

| Etapa | Orçamento (ex.: deadline de 800 ms) |
|---|---|
| Gateway/LB | 20 ms |
| Admissão + validação | 10 ms |
| Aquisição de conexão | ≤ 200 ms |
| Consulta/transação | ≤ 250 ms |
| Chamada ao provedor (1ª tentativa) | ≤ 250 ms |
| Retry (somente se couber no restante) | restante − margem de resposta |

## 5. Amplificação por fan-out e retry

- Fan-out: 1 requisição que chama 5 serviços com p99 de 100 ms tem latência governada pela chamada mais lenta;
  a chance de ao menos uma cair no p99 é ≈ 1 − 0,99⁵ ≈ 4,9%.
- Retry multiplica carga: 3 camadas × 3 tentativas = até 27 chamadas por requisição lógica. Uma camada é dona
  do retry; demais propagam erro. Use orçamento agregado de retry por dependência.
- Durante incidente, retries somam-se ao pico: dimensione o downstream para **carga normal + retries
  permitidos**, ou rejeite antes.

## 6. Exercício de aceite — buffer não resolve déficit

Chegam 1.000 itens/s, processam-se 800 itens/s durante 10 s, itens de 2 KiB:

- Déficit: 200 itens/s × 10 s = **2.000 itens** acumulados.
- Payload: 2.000 × 2 KiB = 4.000 KiB ≈ **3,91 MiB** — sem contar cabeçalho de objeto, referências, índices
  e buffers de serialização (medir; pode dobrar o valor).
- Latência adicionada ao último item: 2.000 ÷ 800/s = **2,5 s** de espera na fila se a saída continuar a
  800/s; acima do deadline do item, o trabalho fica inútil.
- Buffer de **500 itens** enche em 500 ÷ 200/s = **2,5 s**; depois disso a política precisa **pausar o
  produtor, rejeitar (429/503) ou persistir** o excedente de forma durável. O buffer, sozinho, não é solução.
- Drenagem: se a chegada cai para 600/s e a saída segue 800/s, sobra 200/s → 2.000 itens drenam em **10 s**,
  supondo ausência de novas falhas e retries.
- Se o déficit for **sustentado** (chegada > capacidade indefinidamente), nenhum buffer resolve: aumentar
  capacidade, reduzir trabalho por item ou rejeitar.

## 7. SLA, SLO e SLI

- **SLI:** razão medida — numerador/denominador e fonte. Ex.: requisições `POST /pedidos` com status 2xx e
  latência < 300 ms ÷ requisições válidas (exclui 4xx de validação do cliente; **não exclui 429/503 do
  próprio serviço**, que são falhas de disponibilidade).
- **SLO:** alvo + janela. Ex.: 99,9% em 30 dias corridos.
- **SLA:** compromisso contratual com consequência; normalmente mais frouxo que o SLO interno.
- **Orçamento de erro** por requisições = (1 − alvo) × requisições elegíveis. 10 M requisições com 99,9% →
  10.000 falhas permitidas na janela.
- **Disponibilidade por tempo** (janelas "boas" ÷ total) é outra medida: 99,9% em 365 dias = **8,76 h**;
  99,99% = **52,56 min**; 99,9% em 30 dias = **43,2 min**. Não converta automaticamente uma na outra: 1 hora
  fora do ar às 3h e às 12h consome orçamentos de requisição muito diferentes.
- Disponibilidade em série multiplica: dois componentes de 99,9% em série → ~99,8%. Redundância só ajuda
  se as falhas forem independentes (mesma região, credencial ou deploy quebram juntas).
- Latência: meça p50/p95/p99 por operação, separando sucesso e rejeição. Fast failure melhora latência e piora
  disponibilidade — os dois SLIs precisam aparecer.

## 8. Artefato de saída

Tabela por operação: taxa média/pico (+duração), item, retenção, SLI/SLO, orçamento de tempo,
concorrência/conexões, fila (itens/bytes/idade), política de excesso, custo estimado e experimento que
valida a hipótese. Decisões sem números medidos ficam como hipóteses, com dono e prazo.

Detalhes de rejeição, escopo e mecanismos de proteção:
[capacidade e limites](../../resiliencia-controle-fluxo-java/references/capacidade-e-limites.md).
