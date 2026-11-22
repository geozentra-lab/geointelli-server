"""Temporary browser page for trying the agent: GET /playground. On only when PLAYGROUND_ENABLED=true.

To remove it: delete this folder, the include in app/main.py and the playground_enabled setting.
"""

from pathlib import Path

from fastapi import APIRouter
from fastapi.responses import HTMLResponse

_PAGE = (Path(__file__).parent / "index.html").read_text(encoding="utf-8")

router = APIRouter(include_in_schema=False)


@router.get("/playground")
async def playground() -> HTMLResponse:
    return HTMLResponse(_PAGE)
