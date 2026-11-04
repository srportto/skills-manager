# EXJ: acesso curto às capacidades Java

## Contexto

O identificador `catalogo-java` aparece no uso e na instalação do plugin. Os comandos próprios também estão distribuídos entre os namespaces `/catalogo`, `/java` e `/arq`. Isso torna o ponto de entrada menos direto para quem quer pedir uma tarefa de engenharia Java.

## Objetivo

Oferecer um ponto de entrada curto e consistente para as skills, agents e fluxos Java do catálogo, usando **EXJ** como marca e `exj` como identificador técnico em comandos e manifestos.

## Decisões

- A marca de exibição será **EXJ — Especialista em tudo Java**.
- O slug técnico do plugin e do namespace de comandos será `exj`, em minúsculas.
- Uma nova skill de entrada `$exj` identificará a intenção do pedido e encaminhará para as skills e agents Java já existentes. Ela não deve carregar instruções especializadas em massa nem substituir os escopos e limites desses recursos.
- Os comandos específicos do catálogo serão agrupados em `commands/exj/` e terão nomes curtos:
  - `/exj:app` — criar aplicação;
  - `/exj:feat` — conduzir feature;
  - `/exj:ref` — refatorar;
  - `/exj:rev` — revisar;
  - `/exj:adr` — registrar ADR;
  - `/exj:av` — avaliar agents;
  - `/exj:val` — validar o catálogo.
- Os comandos genéricos `/opsx:*` manterão seus nomes e namespaces.
- Os IDs das skills especializadas e dos agents não serão renomeados. Permanecem explícitos para seleção direta e compatibilidade com referências internas.
- O nome técnico do plugin será atualizado para `exj` nos manifestos do Codex e Claude Code, no registro de plugin do marketplace Claude e nos exemplos de configuração do README. O marketplace Claude passará a ser `srportto-exj`.
- O repositório continuará se chamando `skills-manager`; caminhos e pastas internas das skills e agents não serão renomeados.
- A documentação explicará que usuários com o plugin antigo precisam atualizar sua referência ao identificador `exj`.

## Fluxo

O usuário pode pedir uma tarefa diretamente à skill `$exj`, que seleciona o recurso especializado adequado, ou escolher uma skill ou agent pelo nome atual. No Claude Code, os comandos de fluxo do catálogo passam a usar `/exj:*`; comandos OpenSpec seguem em `/opsx:*`. A identidade de instalação do plugin usa `exj` nos dois clientes.

## Critérios de aceitação

1. Os manifestos Codex e Claude Code e o marketplace declaram `exj` de forma consistente.
2. A skill `$exj` existe, descreve o escopo Java do catálogo e encaminha para os recursos existentes sem duplicar seus procedimentos.
3. Os sete comandos `/exj:*` existem e continuam delegando aos mesmos procedimentos e agents que os comandos atuais.
4. As referências de uso e instalação no README usam `exj` e os novos nomes de comando.
5. Testes de validação cobrem a consistência dos manifestos, o inventário dos comandos e os nomes citados na documentação.
6. Nenhuma skill especializada, agent ou comando `/opsx:*` muda de identificador.

## Verificação

A validação automatizada do catálogo deve confirmar os manifestos, referências, anatomia, comandos e tabelas de agents. A revisão manual deve confirmar que a skill `$exj` funciona como roteador de intenção e que as descrições curtas permitem escolher o comando correto.

## Fora de escopo

- Renomear skills ou agents especializados.
- Mudar conteúdo técnico, políticas ou fronteiras dos especialistas.
- Renomear o repositório GitHub ou diretórios de instalação do usuário.
- Alterar os nomes dos comandos OpenSpec.
