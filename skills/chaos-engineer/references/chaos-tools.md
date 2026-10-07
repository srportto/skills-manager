# Ferramentas de chaos engineering

Esta referência compara categorias e fronteiras operacionais. Cada ferramenta externa conserva sua configuração e seus comandos próprios; a aplicação Java e seus testes de falha ficam no catálogo Java.

## Ferramentas e responsabilidades

| Ferramenta | Uso conceitual | Responsável operacional |
|---|---|---|
| Chaos Monkey | Encerrar instâncias dentro de uma política de blast radius | Plataforma de deploy/cloud |
| Gremlin | Injetar falhas de CPU, rede e processos por uma plataforma gerenciada | Plataforma de chaos |
| Litmus | Executar experimentos Kubernetes por CRDs e operadores | Kubernetes/cloud |
| Chaos Mesh | Injetar falhas de rede, processo, I/O e recursos no Kubernetes | Kubernetes/cloud |
| Toxiproxy | Inserir latência, desconexão e outros toxics entre serviços | Teste de integração da aplicação |
| Pumba | Controlar falhas de containers Docker | Plataforma de containers |
| AWS Fault Injection Service | Executar experimentos controlados em recursos AWS | Cloud architect |

Políticas de seleção, identidade, agenda, permissões e rollback pertencem à ferramenta e ao ambiente. Restringir alvos, janela, duração e blast radius; definir abort automático e limpeza mesmo quando a execução falhar. Não tratar uma configuração de ferramenta como prova de que a aplicação recuperou.

## Exemplo Java no catálogo

O teste [ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java)
usa Testcontainers e Toxiproxy Java para injetar latência no Valkey, medir o limite local degradado, remover a falha e verificar recuperação. O teste roda no perfil `integracao` e requer Docker.

Para desenhar hipótese, baseline, condição de abort e recuperação, consulte [experiment-design](experiment-design.md). Para instalar ou operar Litmus, Chaos Mesh, FIS ou outra ferramenta no cluster/conta, encaminhe a topologia a `cloud-architect` ou `engenheiro-devops`.