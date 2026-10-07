# Eixos de interrogatório

Leia este arquivo quando for executar a Etapa 3 do refinamento: ele traz os oito eixos de risco (contrato, estado, idempotência, dado, evento, observabilidade, carga/limites/recuperação e verificabilidade) com as perguntas que não podem ficar sem resposta.

## Etapa 3 — Eixos de interrogatório

Oito eixos. Não escreva uma seção por eixo — **não deixe a pergunta sem resposta**. O que sobrar vira
Questão em Aberto classificada.

### 1. Contrato de entrada e status HTTP

Use a convenção **do projeto** (perfil), não o padrão REST genérico. Se o projeto não tem convenção
declarada, use `api-rest-design` como default explícito e registre a decisão. Pontos que sempre importam:
qual status para entrada inválida, recurso inexistente, conflito de concorrência, quota do cliente (429) e
indisponibilidade/saturação do serviço (503); faixa declarada para campo numérico; tamanho máximo de
payload e de página.

### 2. Máquina de estados

Toda história que muda status responde: **de qual estado, para qual estado, com qual motivo**. Aresta
nova no grafo é mudança de alto impacto (**Bloqueia** até decisão explícita). **Alcançabilidade no grafo não é
idempotência**: pergunte qual é a **checagem explícita de origem** da transição, não só se a aresta existe.

### 3. Idempotência e concorrência

| Pergunta | Por que importa |
|---|---|
| Quem chama, e o chamador repete? | Filas e brokers entregam at-least-once; agendadores e clientes HTTP repetem após timeout |
| A segunda chamada devolve o quê? | O chamador automatizado precisa distinguir "já resolvida" (não repetir) de falha (repetir) |
| Mesma chave com payload diferente? | Deve ser conflito, não sobrescrita silenciosa |
| Dois chamadores simultâneos? | Lock otimista/restrição única e o status resultante |
| Quantos efeitos colaterais no total? | O critério diz **exatamente um** evento/cobrança, não "um evento é publicado" |

### 4. Persistência, particionamento e migration

Onde o campo precisa ser refletido (e o que **não** quebra a compilação se for esquecido), migration com
expand/contract, índice em tabela grande/particionada com procedimento próprio, volume esperado para
consultas novas (sem ele, critério de performance é chute).

### 5. Eventos e espelhos

Se o projeto espelha schemas manualmente, **a lista de espelhos entra na própria história**. Campo novo em
evento é pergunta de compatibilidade: nullable com default, ou o consumidor antigo quebra.

### 6. Observabilidade e dado sensível

Correlação (`traceId`) em fluxo assíncrono, rastreabilidade de quem decidiu e por qual canal, erro sem
detalhes internos, dado pessoal/financeiro fora de log. O critério diz o que **não** aparece no log.

### 7. Carga, limites e recuperação

Para fluxo com volume, dependência remota ou processamento assíncrono:

| Pergunta | Critério observável esperado |
|---|---|
| Taxa média, pico e **duração** do pico? Tamanho máximo do item? | Números com unidade ou Questão em Aberto (nunca número inventado) |
| O que acontece acima da capacidade? | Rejeição (429 quota / 503 saturação, com `Retry-After` quando útil), pausa do produtor ou persistência durável — e a métrica que mostra isso |
| Dependência lenta ou fora? | Deadline, sem retenção ilimitada de recursos; degradação semanticamente válida (nunca "aprovado" inventado) |
| Duplicidade após timeout ou reentrega? | Efeito único; resultado anterior devolvido |
| Dados podem estar desatualizados? | Staleness máximo aceito e onde ele é visível |
| Como volta ao normal? | Drenagem/replay em taxa limitada, sem tempestade de retries |

Em fluxo crítico, "comportamento sob sobrecarga indefinido" é **Bloqueia**. Em CRUD de baixo tráfego, basta
limite de payload/paginação e timeout — não exija infraestrutura distribuída sem motivo. Mecanismos e
números de referência: `resiliencia-controle-fluxo-java` e `design-system-architecture` (capacidade e SLOs).

### 8. Verificabilidade

Para cada `Então`: **existe um teste que fica vermelho se isso não acontecer?** Se não, o critério é vago ou
falta uma tarefa (ex.: teste de concorrência, de carga ou de falha). Indique o tipo de prova esperado
(unitário, integração com serviço real, carga) conforme `testes-sistemas-java`.

