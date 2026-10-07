# Chaos em infraestrutura

Falhas de infraestrutura devem ser planejadas com o dono da plataforma, blast radius limitado, baseline, condição de abort e procedimento de reversão. Operações de rede, zona, nó, disco, certificados e instâncias são ações de plataforma; uma aplicação Java não deve assumir credenciais ou administrar a infraestrutura para testar sua própria resiliência.

## Falhas de rede

Para testar a reação do cliente Java a latência, desconexão ou interrupção, use um proxy controlado no ambiente de teste. No catálogo, [ExperimentoCoordenadorLentoExternoIT](../../../examples/java/integracao/src/test/java/br/com/srportto/exemplos/ExperimentoCoordenadorLentoExternoIT.java)
usa Toxiproxy por sua API Java, Testcontainers e Valkey. O teste mede o limite degradado, remove o toxic em `finally` e verifica recuperação.

Em infraestrutura real, ferramentas como Toxiproxy, service mesh ou recursos de cloud podem atuar no tráfego. Configure duração e alvo pela ferramenta aprovada; não copie comandos de produção para um teste unitário.

## Falhas de host e recursos

Encerramento de instância, drenagem de nó, pressão de CPU/memória, saturação de disco, DNS e expiração de certificado precisam de política específica da plataforma, orçamento de impacto e rollback. Consulte `arquiteto-cloud` para conta, rede, IAM e recuperação de cloud; consulte `engenheiro-devops` para pipeline e operação Kubernetes.

## Evidência

Meça erro, latência, saturação, backlog e tempo de recuperação antes, durante e depois da falha. Mantenha métricas agregadas sem labels de tenant, pedido, trace ou endereço. O relatório deve indicar ferramenta, alvo, parâmetros, duração, aborts, reversão e resultado observado.