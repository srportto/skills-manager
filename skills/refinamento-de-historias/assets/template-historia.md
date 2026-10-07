<!-- Template de saída da skill refinamento-de-historias. Omita seções sem conteúdo real, exceto Prontidão e Questões em Aberto, que sempre aparecem. -->
## 🎯 História de Usuário (INVEST)

**Como** [ator real — nomeie o sistema, se for sistema],
**Eu quero** [ação/funcionalidade],
**Para que** [benefício verificável].

## 🗺️ Escopo e serviços impactados

| Serviço | O que muda | Espelho a replicar |
|---|---|---|

## 📏 Limites e não funcionais (quando aplicável)

| Fluxo | Carga (média/pico/duração) | Limite e escopo | Acima do limite | SLO/latência | Prova |
|---|---|---|---|---|---|

## ✅ Critérios de Aceite

### Cenário 1: [Caminho feliz]
- **Dado que** [pré-condição verificável]
- **Quando** [ação]
- **Então** [efeito observável numa borda]
- **E** [efeito colateral esperado]

### Cenário 2: [Regra de negócio violada]
### Cenário 3: [Chamada repetida]
### Cenário 4: [Concorrência]
### Cenário 5: [Sobrecarga / dependência indisponível / recuperação] (quando aplicável)

## 🛠️ Detalhamento Técnico

- **Contrato**: rotas, headers, status por caso, tópicos/filas
- **Estado**: transição de → para, motivo, checagem explícita de origem
- **Persistência**: entidades, colunas, migration, particionamento
- **Eventos**: tipo, payload, compatibilidade de schema
- **Resiliência**: idempotência, concorrência, deadline, retry (dono e limite), DLQ, rejeição
- **Observabilidade**: correlação, métricas, o que nunca logar
- **Provas**: testes unitários, integração, carga — e o que fica pendente

## ⚠️ Bordas e Riscos
- [ ] [risco concreto e sua consequência]

## 🚦 Prontidão

| Nível | Lacuna | Ação |
|---|---|---|

## ❓ Questões em Aberto
- **[Bloqueia]** [pergunta objetiva, endereçada a quem pode respondê-la]
