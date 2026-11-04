# EXJ eval 5 — execução B

Executor independente: `eval_exj_release_5b`. Avaliador: agente principal. Nota: **2/2**.

## Saída bruta

`$exj` não é o roteador adequado para Python. A skill disponível mais pertinente é `python-pro`; para este exemplo, segue uma aplicação FastAPI mínima em `main.py`:

```python
from fastapi import FastAPI

app = FastAPI(title="Minha API")


@app.get("/")
async def raiz() -> dict[str, str]:
    """Retorna uma mensagem de boas-vindas."""
    return {"mensagem": "Olá, mundo!"}


@app.get("/health")
async def health() -> dict[str, str]:
    """Informa que a aplicação está funcionando."""
    return {"status": "ok"}
```

Instale e execute:

```bash
python -m pip install fastapi uvicorn
uvicorn main:app --reload
```

A documentação interativa fica em `http://127.0.0.1:8000/docs`.

## Justificativa

Declara que `$exj` não atende Python e oferece `python-pro` como skill pertinente, sem selecionar recursos Java.
