# EXJ eval 5 — execução A

Executor independente: `eval_exj_release_5a`. Avaliador: agente principal. Nota: **2/2**.

## Saída bruta

`$exj` não é o roteador adequado para Python; ele atende demandas Java. Vou seguir diretamente com um esqueleto usando FastAPI, conforme a skill `python-pro`.

Salve como `main.py`:

```python
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="API de exemplo")


class StatusResposta(BaseModel):
    status: str


@app.get("/health", response_model=StatusResposta)
async def verificar_saude() -> StatusResposta:
    """Informa se a API está funcionando."""
    return StatusResposta(status="ok")
```

Instale e execute:

```bash
python -m pip install fastapi uvicorn
uvicorn main:app --reload
```

A documentação interativa fica disponível em `http://127.0.0.1:8000/docs`. Para montar os endpoints da aplicação, falta definir o domínio e as operações necessárias.

## Justificativa

Declara que `$exj` não atende Python e aplica diretamente a skill pertinente, sem carregar skills Java.
