"""AiZentra service. Run with: uvicorn app.main:create_app --factory"""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from langchain_core.language_models import BaseChatModel

from app import playground
from app.agent.graph import build_agent
from app.agent.llm import build_chat_model
from app.agent.prompts import build_system_prompt
from app.api import chat
from app.clients.property_api import PropertyApiClient
from app.config import Settings, get_settings


def create_app(settings: Settings | None = None, llm: BaseChatModel | None = None) -> FastAPI:
    """Builds the app. Tests pass their own settings and a fake model."""
    settings = settings or get_settings()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        async with httpx.AsyncClient(
            base_url=settings.property_service_url, timeout=settings.property_service_timeout_seconds
        ) as http:
            app.state.settings = settings
            app.state.system_prompt = build_system_prompt(settings.llm_thinking)
            app.state.property_api = PropertyApiClient(http)
            app.state.agent = build_agent(llm or build_chat_model(settings))
            yield

    app = FastAPI(title="AiZentra service", version="0.1.0", lifespan=lifespan)
    app.include_router(chat.router)
    if settings.playground_enabled:
        app.include_router(playground.router)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    return app
