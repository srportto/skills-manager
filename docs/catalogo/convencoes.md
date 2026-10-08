# Convenções do catálogo

## Fonte e instalação

Neste repositório, `skills/<nome>/SKILL.md` e `agents/<nome>.md` são as fontes. Em uma instalação, o executor pode copiá-las para `.claude/skills`, `.codex/skills` ou outra raiz. Resolva referências usando a raiz em que a skill foi encontrada, nunca um caminho absoluto de uma máquina. Links relativos entre skills continuam relativos ao catálogo.

[README.md](../../README.md) é o índice canônico. O frontmatter identifica cada item; a lista `skills` de um agent deve referenciar identificadores existentes. `metadata.related-skills` pode usar lista YAML ou texto separado por vírgulas. Templates, URLs e exemplos de caminhos não são dependências locais reais.

## Contrato de skill

Seções esperadas (nomes podem variar; o conteúdo, não): **quando usar / quando não usar**, **entradas**,
**decisão**, **passo a passo**, **saída**, **critérios de validação**, **limites** e **referências sob
demanda**, fechando com **quem aplica o quê**. A forma em disco (`references/`, `assets/`, `evals/`) está em [Anatomia de skill](#anatomia-de-skill).

Declare quando usar e quando não usar; leia somente as referências pertinentes. Receba escopo, requisitos conhecidos e evidências. Entregue decisão, artefatos, verificação e limitações. Defina quem aplica o conteúdo. Não copie regras completas de outras skills: a fonte de resiliência é `resiliencia-controle-fluxo-java`; a de evidências de teste é `testes-sistemas-java`.

Pergunte apenas pelo que muda a decisão e ainda não foi informado. Use defaults declarados quando adequados; explicite hipóteses revisáveis. Referências a aplicações de outros monorepos são contexto histórico, não arquivos obrigatórios deste catálogo.

## Anatomia de skill

Complementa o contrato acima (conteúdo) com a forma em disco:

```text
skills/<nome>/
  SKILL.md          # obrigatório: frontmatter name + description
  references/       # opcional: lido sob demanda, um nível (sem subpastas)
  assets/           # opcional: arquivos usados na saída (templates, YAML, SQL)
  evals/evals.json  # casos de avaliação da skill
  scripts/          # só para operação determinística repetitiva
```

- `SKILL.md` tem **no máximo 500 linhas**; o que passa disso vai para `references/`.
- `references/` tem **um nível**; todo arquivo é **linkado a partir do `SKILL.md`** numa tabela
  "arquivo | quando ler" (o "quando ler" diz em que situação abrir, não resume o arquivo).
- Reference com **mais de 300 linhas** abre com `## Sumário`.
- Conteúdo movido para `references/` é movido, não reescrito: nenhuma regra some no fatiamento.
- `assets/` guarda só o que vira saída (modelo copiado, YAML, SQL); explicação fica em `references/`.
- `evals/evals.json` acompanha a skill; mudou a skill, atualize os casos e rode `/exj:av`.
- Ferramentas auxiliares (`Catalogo.AUXILIARES`) só recebem ajustes estruturais mínimos (sumário, links).

## Comandos

Os comandos de `commands/exj/` oferecem os fluxos do EXJ; os de `commands/opsx/` oferecem o fluxo OpenSpec.
Todos são **finos**: acionam o agent (ou a skill) certo,
repassam `$ARGUMENTS` e **não repetem** passos, regras ou checklists, que ficam na skill. Um comando que
cresce além de poucas linhas indica conteúdo que deveria estar numa skill. Todo agent ou skill citado deve
existir; os testes de `validation/java` verificam os tokens dos comandos.

## Contrato de agent

Seções esperadas: **escopo**, **skills relevantes** (carregadas conforme o assunto, não todas),
**entradas**, **entregas**, **evidências**, **fronteiras** e **encaminhamentos**. Um default declarado
(ex.: variante padrão) é usado sem perguntar quando o pedido não o contradiz; o agent só pergunta o que
altera a decisão e ainda não foi informado.

O agent recebe tarefa, fronteira, requisitos e evidências. Retorna decisões justificadas, arquivos afetados, validações executadas com resultado e pendências. Não mistura construção, revisão e operação sem autorização. Modelos, ferramentas, memória e permissões do frontmatter são metadados do executor de origem; quem instala deve mapear capacidades suportadas, sem presumir equivalência de modelos.

Compilação não comprova comportamento. Relate separadamente testes unitários, integração, carga e verificações não executadas. `-DskipTests` nunca é evidência suficiente para aprovar resiliência ou regra de negócio.

## Java e formatos

Exemplos novos/revisados da trilha de engenharia usam Java 25, sem preview, com comentários em português. Spring Boot 4 é usado quando o exemplo requer aplicação. Exemplos executáveis vivem em `examples/java`; a skill aponta para a fonte testada. YAML/XML/HCL/SQL/PromQL/Mermaid são formatos de configuração, consulta e documentação, não alternativas de implementação da aplicação.

Graphify, OpenSpec e `python-pro` são ferramentas auxiliares preservadas, fora da trilha Java. Não apagar nem reescrever seus scripts por causa da preferência de linguagem da aplicação. Não introduzir Python no fluxo do `java-construtor`.

## Fluxo OpenSpec

Os comandos `/opsx:*` só delegam às skills `openspec-*` (fonte única do fluxo). Essas skills, geradas pelo OpenSpec
1.4.1, recebem ganchos curtos que carregam [openspec-catalogo-java](../../skills/openspec-catalogo-java/SKILL.md),
onde fica o mapa fase → skills → agent → prova. No projeto alvo, use `openspec init --tools none` e o
`openspec/config.yaml` modelo da skill: as `rules` chegam pelo CLI e sobrevivem a uma regeneração das skills.
Ao atualizar o OpenSpec, reaplique os ganchos; `OpenSpecIntegracaoTest` falha se algum sumir.

## AWS local

Serviços AWS em desenvolvimento e testes usam o **Floci** (`floci/floci`, porta 4566, credenciais `test`/`test`):
`docker run` ou compose localmente e `FlociContainer` (`io.floci:testcontainers-floci`) nos testes de integração.
Versões fixadas em [compatibilidade](compatibilidade.md).

## Decisão de proteção

Registre operação/dependência, taxa média/pico, tamanho de item, concorrência, fila por itens/bytes, espera/deadline, erro elegível, política de retry, efeito idempotente, destino de rejeição, escopo local/global e recuperação. Cada número deve ter unidade e justificativa. Documente métrica, teste e limite de validade da solução.

Use políticas proporcionais ao problema. CRUD de baixo tráfego não exige broker, WebFlux ou circuit breaker sem dependência que justifique. Heurísticas de estilo não são bugs automáticos. Segurança exige risco concreto; cenários de produção seguem a autorização do usuário e o blast radius acordado.

## Manutenção

Ao mudar uma API de exemplo, compile e teste os consumidores. Ao mudar uma skill, avalie roteamento e decisões com casos realistas. Ao mudar versão de JDK/framework, consulte documentação da versão e execute os módulos afetados. Preserve autoria e ferramentas importadas. O inventário é gerado a partir dos arquivos, não de contagens fixas.
