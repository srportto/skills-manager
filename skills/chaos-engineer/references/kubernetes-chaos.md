# Chaos em Kubernetes

Litmus e Chaos Mesh executam experimentos no cluster por CRDs, operadores e permissões Kubernetes. `ChaosEngine`, `NetworkChaos`, `PodChaos`, `StressChaos` e recursos semelhantes são manifestos da plataforma: sua seleção de namespaces/labels, service account, duração, limpeza e blast radius devem passar pela revisão do responsável pelo cluster.

## Fronteira entre aplicação e plataforma

A trilha Java valida o comportamento da aplicação e de seus clientes. O teste [ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java)
injeta latência de rede por Toxiproxy Java em um teste externo, com Docker e cleanup explícito.

Drenar nós, alterar recursos de Kubernetes, aplicar `ChaosEngine`/CRDs, interromper uma zona ou mudar políticas de rede pertencem à trilha `cloud-architect` e à operação de `engenheiro-devops`. Não implemente essas ações como lógica da aplicação Java. Para executar um CRD, exija namespace e alvos autorizados, limite de duração, condição automática de abort e remoção garantida dos recursos temporários.

## Verificação

Observe disponibilidade, erros, latência, saturação, réplicas prontas e tempo de recuperação. Confirme que readiness, autoscaling, PDB e alertas refletem o cenário esperado; liveness não deve falhar apenas porque uma dependência externa está fora. Guarde o manifesto aplicado e o resultado junto com o relatório do game day, removendo segredos e dados de clientes.