import logging
from typing import Annotated

import openai
from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from app.agent.runner import run_turn
from app.api.schemas import ChatRequest, ChatResponse
from app.security import bearer_token, require_internal_token

log = logging.getLogger(__name__)

router = APIRouter(prefix="/v1", dependencies=[Depends(require_internal_token)])


@router.post("/chat")
async def chat(
    body: ChatRequest, request: Request, authorization: Annotated[str | None, Header()] = None
) -> ChatResponse:
    """Answers one question. Spring sends the user's token in Authorization; tools reuse it."""
    state = request.app.state
    try:
        result = await run_turn(
            state.agent,
            body,
            system_prompt=state.system_prompt,
            property_api=state.property_api,
            user_token=bearer_token(authorization),
            max_tool_rounds=state.settings.max_tool_rounds,
            model_name=state.settings.llm_model,
        )
    except openai.APIError as error:
        log.warning("turn %s: model server error: %s", body.turn_id, error)
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE, "The language model is unavailable."
        ) from error
    log.info("turn %s: %s, %d evidence item(s)", body.turn_id, result.status, len(result.evidence))
    return result
