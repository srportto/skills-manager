# Critérios de aceite observáveis

Leia este arquivo quando for escrever ou criticar critérios de aceite (Etapa 4): o que torna um `Então` observável, a cobertura mínima de cenários, um exemplo completo Dado/Quando/Então com limites e reescritas de "ruim para bom".

## Etapa 4 — Critérios de aceite observáveis

Um critério serve quando nomeia um **efeito observável numa borda**: status HTTP e shape do corpo, linha
persistida (coluna e valor), mensagem publicada (destino e atributo), chave em cache, métrica, entrada de log.

| Vago (não serve) | Observável (serve) |
|---|---|
| "o pedido é cancelado" | "a linha persistida tem `status` = `CANCELADO` **e** `motivo` = `<valor>`" |
| "retorna erro" | "responde `<status do projeto>` com o shape de erro do projeto **e** nada é persistido" |
| "o evento é publicado" | "**exatamente um** evento `<tipo>` é publicado em `<destino>`" |
| "a consulta é rápida" | "p99 < `<N>` ms com `<volume>` carregado" — sem `<N>` conhecido, Questão em Aberto **Bloqueia** |
| "aguenta o pico" | "com `<taxa>` req/s por `<duração>`, requisições acima de `<limite>` recebem 503 em < 50 ms, nenhuma fila passa de `<itens>` e o serviço volta ao p99 nominal em `<tempo>` após o pico" |
| "resiliente ao provedor" | "com provedor respondendo em > `<deadline>`, no máximo `<N>` chamadas simultâneas ficam abertas e o pedido fica `PENDENTE` para reconciliação" |

**Cobertura mínima de cenários:** caminho feliz; regra violada (o erro **e** o que não aconteceu); **chamada
repetida**; **concorrência**; recurso ausente/estado inválido; e, quando o eixo 7 se aplica, **sobrecarga**,
**dependência indisponível** e **recuperação**.

O item mais esquecido é uma cláusula: **o efeito que não deve acontecer** — "e nenhum evento é publicado",
"e exatamente uma cobrança no total", "e a fila não passa de N itens".


## Exemplo completo: Dado/Quando/Então com limites

Fluxo de criação de pedido no checkout (contrato real em
[`CheckoutApplication`](../../../examples/java/integracao/src/main/java/br/com/srportto/exemplos/CheckoutApplication.java);
os números abaixo são **ilustrativos** — em história real, vêm do PO/SRE ou viram Questão em Aberto **Bloqueia**).

```gherkin
Cenário 1: pedido novo aceito
  Dado que o tenant "loja-42" não usou a chave "k-001"
  Quando POST /pedidos com Idempotency-Key "k-001" e centavos = 15000
  Então responde 201 com corpo {"id": "<uuid>"}
  E a tabela pedidos tem exatamente uma linha com centavos = 15000
  E a tabela outbox tem exatamente um evento com publicado = false

Cenário 2: chamada repetida (mesma chave, mesmo payload)
  Dado que "k-001" já criou o pedido <uuid>
  Quando POST /pedidos repete "k-001" com centavos = 15000
  Então responde 201 com o mesmo id <uuid>
  E a tabela pedidos continua com exatamente uma linha
  E nenhum evento novo entra na outbox

Cenário 3: mesma chave, payload diferente
  Dado que "k-001" já foi usada com centavos = 15000
  Quando POST /pedidos com "k-001" e centavos = 99900
  Então responde 409 (application/problem+json)
  E nada é persistido

Cenário 4: sobrecarga
  Dado que a instância admite no máximo 16 pedidos simultâneos (checkout.capacidade)
  Quando 200 requisições/s chegam por 30 s
  Então as que excedem a capacidade recebem 503 com Retry-After: 1 em < 50 ms
  E a taxa de rejeição aparece na métrica de proteção de "criar-pedido"
  E nenhum pedido aceito é perdido

Cenário 5: pré-condição ausente
  Quando POST /pedidos sem o cabeçalho Idempotency-Key
  Então responde 400 com o nome do cabeçalho ausente
  E nada é persistido
```

Note como cada `Então` aponta uma borda (status, linha, evento, métrica) e como o `E` carrega o efeito que
**não** deve acontecer ("nenhum evento novo", "nada é persistido").

## Reescrita: ruim → bom

**Ruim**

```gherkin
Dado que o usuário está logado
Quando ele cria um pedido
Então o sistema trata corretamente a repetição e continua rápido
```

Problemas: ator vago (quem repete é um cliente HTTP, não "o usuário"); "trata corretamente" e "rápido" não
falham em nenhum teste; não diz o que não acontece.

**Bom**

```gherkin
Dado que o pedido da chave "k-001" já foi criado
Quando o cliente repete POST /pedidos com a mesma chave e o mesmo payload após timeout
Então a resposta é 201 com o id original
E a tabela pedidos tem exatamente uma linha para (tenant, chave)
E a latência p99 da rota fica < <N> ms com <volume> carregado  (sem <N> conhecido: Questão em Aberto, Bloqueia)
```
