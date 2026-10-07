# Integração, falhas e carga

Duas trilhas: testes locais determinísticos com dependências embutidas e integração com serviços reais efêmeros. H2 ajuda a exercitar transações, mas não comprova todos os comportamentos PostgreSQL. Broker em memória não comprova ack/offset; execute cliente real contra container.

Perfil integracao falha se Docker não estiver disponível; não usar disabledWithoutDocker para produzir verde enganoso. Declare imagens fixas, timeout de startup e limpeza automática. Injeção de falha pode usar Toxiproxy via API Java/Testcontainers ou um servidor HTTP Java que controla resposta/lentidão. Serviços AWS (SQS, S3...) rodam no Floci via `FlociContainer`.

Carga em Java deve limitar o próprio gerador: taxa oferecida, fila de agendamento, número de tarefas e tempo total. Registre carga oferecida, aceita, rejeitada, concluída, percentis, máximo ativo e recuperação. Separar fila do gerador e fila do alvo; evitar coordinated omission ao concluir capacidade com clientes de loop fechado.

Ensaio sintético determinístico prova invariantes; ensaio aberto contra HTTP real mede comportamento no ambiente. Nenhum deles certifica SLO de produção sem distribuição de carga representativa.

Perfis e comandos: [examples/java/README.md](../../../examples/java/README.md). Reporte hardware/JDK, parâmetros, limitações, falhas e testes não executados.

