"""The only module that knows which model server is used. Swap models here."""

from langchain_core.language_models import BaseChatModel
from langchain_openai import ChatOpenAI

from app.config import Settings


def build_chat_model(settings: Settings) -> BaseChatModel:
    # No temperature override: Qwen's docs advise against greedy decoding (temperature 0), and the
    # model server applies the model's recommended sampling settings.
    return ChatOpenAI(
        model=settings.llm_model,
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        timeout=settings.llm_timeout_seconds,
        max_retries=1,
    )
