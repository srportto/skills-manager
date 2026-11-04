#!/usr/bin/env python3
"""Remonta o repositório a partir dos zips tangodown-parte-NNN.zip.

Uso: python3 remontar.py --destino <dir> [--origem <dir-dos-zips>] [--sobrescrever]

Só usa a biblioteca padrão (Python 3.8+). Confere o SHA-256 de cada zip (se
houver checksums.sha256 ao lado), de cada arquivo remontado e o total de arquivos.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


def falhar(msg):
    sys.exit(f"ERRO: {msg}")


def caminho_seguro(destino, relativo):
    # Impede path traversal: o caminho precisa ser relativo e ficar dentro do destino
    p = PurePosixPath(relativo)
    if p.is_absolute() or ".." in p.parts or not p.parts:
        falhar(f"caminho inválido no manifesto: {relativo}")
    return destino.joinpath(*p.parts)


def main():
    ap = argparse.ArgumentParser(description="Remonta o repositório a partir dos zips tangodown.")
    ap.add_argument("--origem", default=str(Path(__file__).resolve().parent),
                    help="diretório com os tangodown-parte-NNN.zip (padrão: pasta deste script)")
    ap.add_argument("--destino", required=True, help="diretório onde o repositório será recriado")
    ap.add_argument("--sobrescrever", action="store_true", help="permite sobrescrever arquivos existentes")
    args = ap.parse_args()

    origem, destino = Path(args.origem).resolve(), Path(args.destino).resolve()
    zips = sorted(p for p in origem.iterdir() if re.fullmatch(r"tangodown-parte-\d{3}\.zip", p.name))
    if not zips:
        falhar(f"nenhum tangodown-parte-NNN.zip em {origem}")

    # 1) Confere integridade dos zips, quando o arquivo de checksums estiver presente
    checksums = origem / "checksums.sha256"
    if checksums.exists():
        for linha in checksums.read_text(encoding="utf-8").splitlines():
            if not linha.strip():
                continue
            esperado, nome = linha.split(maxsplit=1)
            alvo = origem / nome.strip().lstrip("*")
            if not alvo.exists():
                falhar(f"zip ausente: {alvo.name}")
            if hashlib.sha256(alvo.read_bytes()).hexdigest() != esperado:
                falhar(f"SHA-256 divergente em {alvo.name} (download corrompido?)")
        print(f"checksums dos {len(zips)} zip(s): ok")
    else:
        print("aviso: checksums.sha256 não encontrado; integridade validada só por arquivo")

    # 2) Lê o manifesto (fica na parte 001) e indexa as entradas de todos os zips
    abertos = {z.name: zipfile.ZipFile(z) for z in zips}
    primeiro = abertos.get("tangodown-parte-001.zip")
    if primeiro is None or "manifesto.json" not in primeiro.namelist():
        falhar("manifesto.json não encontrado em tangodown-parte-001.zip")
    manifesto = json.loads(primeiro.read("manifesto.json").decode("utf-8"))
    if manifesto.get("formato") != "tangodown/1":
        falhar(f"formato de manifesto desconhecido: {manifesto.get('formato')}")
    faltando = [f"tangodown-parte-{i:03d}.zip" for i in range(1, manifesto["total_partes"] + 1)
                if f"tangodown-parte-{i:03d}.zip" not in abertos]
    if faltando:
        falhar("zips ausentes: " + ", ".join(faltando))

    # 3) Recria cada arquivo juntando seus pedaços e decodificando quando for base64
    destino.mkdir(parents=True, exist_ok=True)
    escritos = 0
    for arq in manifesto["arquivos"]:
        alvo = caminho_seguro(destino, arq["caminho"])
        if alvo.exists() and not args.sobrescrever:
            falhar(f"{alvo} já existe (use --sobrescrever)")
        bruto = b"".join(abertos[p["zip"]].read(p["txt"]) for p in arq["pedacos"])
        dados = base64.decodebytes(bruto) if arq["codificacao"] == "base64" else bruto
        if len(dados) != arq["tamanho"] or hashlib.sha256(dados).hexdigest() != arq["sha256"]:
            falhar(f"conteúdo divergente após remontar: {arq['caminho']}")
        alvo.parent.mkdir(parents=True, exist_ok=True)
        alvo.write_bytes(dados)
        if arq.get("executavel") and os.name != "nt":
            alvo.chmod(0o755)
        escritos += 1

    if escritos != manifesto["total_arquivos"]:
        falhar(f"remontados {escritos} de {manifesto['total_arquivos']} arquivos")
    print(f"ok: {escritos} arquivos remontados em {destino} (commit de origem {manifesto['commit_origem'][:7]})")


if __name__ == "__main__":
    main()
