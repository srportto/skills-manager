# Catálogo de skills, agents e comandos

Catálogo Java 25 / Spring Boot 4 empacotado como plugin do Codex. Índice em `skills/README.md`;
regras de anatomia em [docs/catalogo/convencoes.md](docs/catalogo/convencoes.md).

- Idioma: português do Brasil em textos, respostas e comentários de código; termos técnicos consagrados em inglês.
- Antes de commitar, rode `mvn -f validation/java/pom.xml verify` (links, inventário, anatomia, comandos e tabelas dos agents).
- Mudou uma skill: atualize o `evals/evals.json` dela e rode `/catalogo:avaliar` nos casos afetados.
- Nunca reescreva as ferramentas auxiliares (`graphify`, `openspec-*`, `python-pro`, `remover-imports-nao-usados`, `terraform-engineer`; lista em `Catalogo.AUXILIARES`): só ajustes estruturais mínimos.
- `SKILL.md` com no máximo 500 linhas; detalhe vai para `references/` (um nível, linkado com "quando ler").
- Comandos são finos: delegam ao agent/skill e não repetem regras.
- Commits no padrão `tipo(escopo): resumo`, em português.
