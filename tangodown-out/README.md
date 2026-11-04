# tangodown — pacote para remontar o catálogo `skills-manager`

Este diretório contém o repositório `srportto/skills-manager` (catálogo EXJ de skills, agents e comandos
Java 25 / Spring Boot 4) desmontado em arquivos `.txt` e agrupado em zips de até 3,9 MB.

- Arquivos: **450** em **1** zip(s) (`tangodown-parte-001.zip` … `tangodown-parte-001.zip`)
- Commit de origem: `c7f890da869a53af2e8ef1caff6a54a59f5fb930`
- Gerado em: 2026-11-04T01:03:25+00:00
- Fora do pacote: `.claude`, `.codex-plugin`, `.docs`, `.github`, `.superpowers`, `.vscode`, `.worktrees`, `.git` e tudo que o `.gitignore` ignora (ex.: `target/`, `*.class`)

## Instruções para o Claude (remontagem)

Quando receber estes arquivos e um pedido para recriar o diretório de skills, siga exatamente:

1. Garanta que **todos** os zips `tangodown-parte-NNN.zip` estão na mesma pasta, junto com
   `checksums.sha256`. A quantidade esperada é 1. Não renomeie os zips.
2. Se `remontar.py` e `manifesto.json` não estiverem soltos na pasta, extraia-os da parte 001
   (eles vão sempre dentro dela):
   ```bash
   unzip -o tangodown-parte-001.zip README.md manifesto.json remontar.py
   ```
3. Rode a remontagem (Python 3.8+, só biblioteca padrão; no macOS use `python3`):
   ```bash
   python3 remontar.py --origem . --destino ../skills-manager
   ```
   O script confere o SHA-256 dos zips, junta os pedaços, decodifica os arquivos que estão em base64,
   confere o SHA-256 de cada arquivo e o total. A saída esperada termina com
   `ok: 450 arquivos remontados`. Se aparecer `ERRO:`, pare e informe o usuário. Não tente
   "consertar" arquivos.
4. Para versionar o resultado (opcional), dentro do destino:
   ```bash
   git init && git add -A && git commit -m "chore: remonta catálogo a partir do tangodown"
   ```
   Ou, para seguir o remoto original, clone `https://github.com/srportto/skills-manager.git` e compare.
5. Valide o catálogo remontado (requer JDK 25 + Maven): `mvn -f validation/java/pom.xml verify`.
   **Resultado esperado: 45 testes, 2 falhas.** As duas falhas vêm das exclusões feitas de propósito, e não de
   corrupção do pacote:
   - `PluginManifestoTest.manifestoCodexDeclaraAsSkillsDoCatalogo` → falta `.codex-plugin/plugin.json`;
   - `ReferenciasCatalogoTest.linksLocaisDevemResolverNaFonteDoCatalogo` → `skills/devops-cicd/references/pipeline-ci.md`
     aponta para `.github/workflows/validar-catalogo.yml`.

   Qualquer outra falha indica problema real. Para zerar as duas, copie `.codex-plugin/` e `.github/` do remoto original.
   O plugin do Claude Code (`.claude-plugin/`) está incluído e não depende delas.

## Formato do pacote

| Item | Conteúdo |
|---|---|
| `tangodown-parte-NNN.zip` | Entradas `txt/<caminho-original>.txt`. A parte 001 também traz `README.md`, `manifesto.json` e `remontar.py` na raiz. |
| `manifesto.json` | Para cada arquivo: `caminho`, `codificacao` (`texto` ou `base64`), `executavel`, `tamanho`, `sha256` e `pedacos` (cada um com o `.txt` e o zip onde está). |
| `checksums.sha256` | SHA-256 de cada zip, no formato de `shasum -a 256 -c checksums.sha256`. |
| `remontar.py` | Script de remontagem descrito acima. |
| `txt/` | Os `.txt` soltos de onde os zips foram gerados (não são necessários para remontar). |

Regras de conversão:

- Arquivo de texto UTF-8 → `txt/<caminho>.txt`, com bytes idênticos ao original (fim de linha LF, como no git).
- Arquivo binário → `txt/<caminho>.b64.txt`, em base64.
- Arquivo maior que 3 MB após a conversão → fatiado em `….txt.parte001`, `….txt.parte002`, … (concatenar em ordem).

## Remontagem manual (sem Python)

Use isto só se não houver Python. No macOS:

```bash
mkdir -p ../extraido && for z in tangodown-parte-*.zip; do unzip -oq "$z" 'txt/*' -d ../extraido; done
cd ../extraido/txt
# texto: remover o sufixo .txt
find . -type f -name '*.txt' ! -name '*.b64.txt' | while read -r f; do mkdir -p "../../skills-manager/$(dirname "$f")"; cp "$f" "../../skills-manager/${f%.txt}"; done
# binário: decodificar base64 e remover o sufixo .b64.txt
find . -type f -name '*.b64.txt' | while read -r f; do mkdir -p "../../skills-manager/$(dirname "$f")"; base64 -D -i "$f" -o "../../skills-manager/${f%.b64.txt}"; done
```

Pedaços `.parteNNN`, se existirem, devem ser concatenados em ordem (`cat x.txt.parte* > x.txt`) antes
do passo acima. Depois confira os hashes contra o `manifesto.json` (`shasum -a 256 <arquivo>`).
