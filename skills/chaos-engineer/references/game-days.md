# Planejamento e execução de game days

Game day é um exercício coordenado para verificar resposta técnica e operacional a uma falha. O plano precisa caber na janela autorizada, ter participantes e critérios observáveis e permitir interromper e reverter a injeção.

## Antes do exercício

Registre:

- hipótese e resultado esperado, com SLO/SLI, baseline e duração;
- ambiente e dependências atingidas, blast radius e participantes (facilitador, operador, observador e escriba);
- comunicação, canal de incidente e cadência de atualizações;
- gatilhos de abort, pessoa autorizada a abortar e rollback já ensaiado;
- evidências a guardar: horários, métricas, logs, traces, decisão e tempo de recuperação.

Comece em ambiente não produtivo e com uma única falha. Não inicie quando o estado estável já viola os limites. Em produção, obtenha autorização explícita, reduza o blast radius e mantenha operador acompanhando a execução.

## Durante o exercício

1. Registre baseline e confirme o estado saudável.
2. Anuncie o início e injete somente a falha aprovada.
3. Observe impacto, alertas e resposta dos participantes.
4. Aborte quando um gatilho for atingido ou a falha exceder o escopo.
5. Remova o toxic/experimento e confirme recuperação, drenagem e ausência de tempestade de retries.
6. Registre linha do tempo e fatos sem atribuir culpa.

## Depois do exercício

Faça debriefing, registre hipóteses confirmadas ou refutadas e atribua ações com responsável e prazo. Agende revisão das ações; um experimento planejado ou interrompido não conta como evidência de recuperação.

O exemplo de injeção e recuperação da aplicação Java é [ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java). Use também [experiment-design](experiment-design.md) para o contrato técnico do experimento.