# README e distribuição do catálogo — Especificação de desenho

## Objetivo

Transformar a raiz do repositório na porta de entrada do catálogo Java 25 / Spring Boot 4. O guia deve permitir que uma pessoa descubra, instale e use as skills, os agents e os comandos tanto neste repositório quanto em outro projeto, no escopo de usuário ou de projeto.

## Público e resultado esperado

O público é formado por pessoas que usam Codex ou Claude Code e querem aplicar o catálogo em projetos Java. Ao abrir o repositório, elas devem conseguir identificar qual recurso atende a tarefa, escolher a forma de instalação adequada e acionar um fluxo completo sem ler a estrutura interna primeiro.

O resultado esperado é um `README.md` na raiz que explica:

- propósito, cobertura e limites do catálogo;
- uso a partir de um clone do repositório;
- instalação no escopo de usuário e no escopo de projeto;
- como o Codex descobre e usa skills;
- como os agents e comandos participam dos fluxos suportados;
- exemplos de solicitações para tarefas recorrentes;
- manutenção e comandos de validação.

## Estado atual e decisões

O catálogo tem 31 skills, 11 agents e 12 comandos. Seu índice principal está em `skills/README.md`, portanto a raiz não orienta uma pessoa que acabou de clonar o repositório. O repositório contém o manifesto legado de plugin em `.claude-plugin/`, mas não o manifesto de compatibilidade do Codex em `.codex-plugin/`.

O novo `README.md` será o índice canônico. O conteúdo funcional de `skills/README.md` será migrado, seus links internos serão ajustados e o arquivo será removido. A validação Java passará a verificar o novo índice. Isso remove uma duplicação de porta de entrada e deixa o catálogo acessível no local esperado.

O conteúdo de `AGENTS.md` e `CLAUDE.md` continuará específico ao executor, pois os arquivos são carregados automaticamente por ferramentas diferentes. A redução de duplicação entre eles ficará fora desta mudança: uma referência indireta poderia impedir o carregamento de regras essenciais pelo executor.

Os relatórios históricos de avaliação e planos existentes serão preservados. Eles registram evidências de qualidade e decisões anteriores. A consolidação ou o arquivamento desses relatórios é uma mudança de política de retenção e requer decisão específica posterior.

## Distribuição e instalação

O README descreverá quatro caminhos, deixando explícito o efeito de cada um:

| Cenário | Forma | Resultado |
|---|---|---|
| Avaliar ou contribuir | Clonar o repositório | Consulta direta às fontes, exemplos e validações; apropriado para manutenção do catálogo. |
| Usar em um único projeto | Clone dentro do projeto ou referência local de marketplace | O projeto passa a disponibilizar o catálogo a quem trabalha nele. |
| Usar em todos os projetos locais | Clone em diretório estável e marketplace pessoal | O catálogo fica disponível para a conta do desenvolvedor. |
| Distribuir para a equipe | Marketplace Git ou plugin publicado | Instalação versionada e descoberta centralizada. |

O guia diferenciará o comportamento por plataforma:

- **Codex:** skills são entregues pelo plugin; o manifesto `.codex-plugin/plugin.json` declarará `./skills/`. A documentação mostrará marketplace pessoal e de projeto, habilitação em `.codex/config.toml` e o uso explícito de `$nome-da-skill` quando necessário.
- **Claude Code:** o manifesto e o marketplace existentes permanecem compatíveis; a documentação mostrará o uso de `skills/`, `agents/` e `commands/` instalados pelo plugin.
- **Clone sem instalação:** a pessoa pode ler e copiar as skills necessárias para o projeto alvo. O README explicará que esse modo não oferece atualização automática e que referências relativas e assets devem acompanhar cada skill copiada.

Os arquivos em `agents/` e `commands/` serão apresentados como definições de fluxo do catálogo. O README não prometerá que todo cliente Codex os carrega automaticamente: para distribuição nativa em Codex, o comportamento reutilizável deve ser convertido em skills. Essa limitação será descrita com uma alternativa prática: usar as skills correspondentes e fornecer ao Codex o papel e o fluxo indicados pelo agent ou comando.

## Estrutura do README

O arquivo seguirá esta ordem:

1. título, objetivo e público;
2. início rápido por clone;
3. instalação no Codex e no Claude Code, separada por escopo;
4. como escolher entre skill, agent e comando;
5. mapa de capacidades e inventário completo;
6. fluxos recomendados e exemplos de prompts;
7. estrutura do repositório;
8. manutenção, avaliações e validações;
9. compatibilidade e limites de portabilidade.

O mapa de capacidades permanecerá em tabelas por tarefa para evitar que o usuário tenha de percorrer as 31 skills em ordem alfabética. Os links apontarão para a fonte de cada skill, agent, comando, exemplo e referência normativa.

## Alterações de arquivos

| Arquivo | Alteração | Responsabilidade |
|---|---|---|
| `README.md` | criar | Índice e guia de instalação e uso. |
| `skills/README.md` | remover | Índice substituído pela raiz. |
| `.codex-plugin/plugin.json` | criar | Manifesto de compatibilidade do plugin Codex, declarando as skills. |
| `AGENTS.md` | alterar | Atualizar o link para o índice raiz. |
| `CLAUDE.md` | alterar | Atualizar o link para o índice raiz. |
| `commands/catalogo/validar.md` | alterar | Referenciar a seção de validação do README raiz. |
| `docs/catalogo/convencoes.md` | alterar | Declarar o README raiz como índice canônico. |
| `validation/java/src/test/java/br/com/srportto/catalogo/ReferenciasCatalogoTest.java` | alterar | Validar a cobertura do README raiz. |
| `validation/java/src/test/java/br/com/srportto/catalogo/PluginManifestoTest.java` | alterar | Validar o manifesto de compatibilidade do Codex. |

## Critérios de aceitação

- A raiz contém `README.md` em português do Brasil e sem links locais quebrados.
- O README mostra a instalação por clone, por marketplace pessoal e por marketplace de projeto.
- O README identifica corretamente o que é portátil entre Codex e Claude Code e o que depende de cada cliente.
- Todas as skills e agents do catálogo são citados pelo índice canônico.
- `skills/README.md` não permanece como segundo índice.
- Os testes de validação verificam o novo índice e os dois manifestos.
- `mvn -f validation/java/pom.xml verify` passa antes do commit.

## Fora de escopo

- Converter agents e comandos em skills nativas do Codex.
- Alterar o conteúdo técnico das skills, agents, comandos ou exemplos.
- Remover, compactar ou mover relatórios de avaliação históricos.
- Publicar o plugin em marketplace público ou de workspace.

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Instrução de instalação desatualizada | Basear o texto na documentação oficial consultada e citar a configuração por escopo. |
| Redução de arquivos apagar evidência | Limitar esta mudança à remoção do índice redundante. |
| Promessa de compatibilidade que o executor não oferece | Separar explicitamente a experiência Codex da experiência Claude Code. |
| Link quebrado após a migração | Atualizar referências e executar a suíte de validação. |
