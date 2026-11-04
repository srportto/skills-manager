# Anti-padrões de história

Leia este arquivo quando for criticar uma história pronta ou revisar a sua própria antes de marcá-la como pronta: cada anti-padrão traz o erro típico, por que dói e a correção, com um "antes → depois" curto.

## Anti-padrões de história

### A história já é a solução

**[❌]** `Criar uma tabela de log de decisões com as colunas id, pedido_id, acao e data.`

**[🚨]** Entrega o desenho e esconde a necessidade; ninguém avalia se a informação já é recuperável.

**[✅]** `Como analista de operações, quero saber por qual caminho um pedido chegou ao estado atual, para
investigar reclamação sem depender da engenharia.`

### O critério não pode falhar

**[❌]** `Então o sistema processa corretamente e mantém a consistência dos dados.`

**[🚨]** Nenhum teste fica vermelho por causa disso; dá sensação de cobertura.

**[✅]** `Então a linha tem status ATIVO E motivo = ACEITO_POR_TODOS E exatamente um evento ATIVACAO é publicado.`

### Silêncio sobre a chamada repetida

**[❌]** `Quando o prazo expira, o pedido é rejeitado.`

**[🚨]** Quem dispara é um agendador at-least-once que não conhece o estado. E se a expiração chegar depois
da aprovação?

**[✅]**
```
Cenário: expiração chega depois da aprovação
  Dado que o pedido já está ATIVO
  Quando a expiração é processada
  Então a resposta identifica o status atual como erro de negócio
  E o pedido permanece ATIVO
  E nenhum evento é publicado
```

### "Aguenta qualquer volume"

**[❌]** `O endpoint de importação deve suportar alto volume.`

**[🚨]** Sem taxa, pico, duração e comportamento acima do limite, o dev escolhe uma fila em memória sem
limite — e o primeiro pico vira OutOfMemoryError.

**[✅]**
```
Cenário: importação acima da capacidade
  Dado que a importação processa até 200 itens/s e aceita no máximo 2.000 itens pendentes
  Quando chegam 1.000 itens/s por 10 s
  Então os itens acima de 2.000 pendentes recebem 503 com Retry-After
  E a métrica importacao_rejeitados_total registra cada rejeição
  E nenhum item aceito é perdido após reinício do serviço
```

### Contrato inventado por analogia

**[❌]** `Se não existir, 404. Se inválido, 400.`

**[🚨]** É o REST genérico, não necessariamente a convenção do projeto. Confira o perfil; divergência é
decisão explícita, não detalhe.

### Campo novo sem a lista de espelhos

**[❌]** `Adicionar canalOrigem e disponibilizá-lo para os consumidores.`

**[🚨]** "Os consumidores" esconde cópias mantidas à mão; esquecer uma não quebra a compilação — o campo
chega nulo em produção.

**[✅]** A história lista cada espelho e declara o campo nullable com default.


## Antes → depois (resumo)

Cada anti-padrão acima reescrito em uma linha. O "depois" de "Contrato inventado por analogia" depende do
perfil do projeto; os valores entre `<>` vêm dele.

| Anti-padrão | Antes | Depois |
|---|---|---|
| A história já é a solução | "Criar tabela de log de decisões com id, pedido_id, acao, data." | "Como analista de operações, quero saber por qual caminho um pedido chegou ao estado atual, para investigar reclamação sem a engenharia." |
| O critério não pode falhar | "Então o sistema mantém a consistência dos dados." | "Então a linha tem status ATIVO e exatamente um evento ATIVACAO é publicado." |
| Silêncio sobre a chamada repetida | "Quando o prazo expira, o pedido é rejeitado." | Cenário com `Dado que o pedido já está ATIVO` / `Então o pedido permanece ATIVO e nenhum evento é publicado`. |
| "Aguenta qualquer volume" | "O endpoint deve suportar alto volume." | "Com 1.000 itens/s por 10 s e fila de 2.000, o excedente recebe 503 com Retry-After e a métrica registra cada rejeição." |
| Contrato inventado por analogia | "Se não existir, 404. Se inválido, 400." | "Recurso inexistente responde `<status do perfil>`; entrada inválida responde `<status do perfil>` com o shape de erro do projeto." |
| Campo novo sem a lista de espelhos | "Adicionar canalOrigem para os consumidores." | "Adicionar canalOrigem (nullable, default `<valor>`) em: `<espelho 1>`, `<espelho 2>`, `<espelho 3>`." |
