import itertools
from collections.abc import Iterable

import pytest
from fastapi.testclient import TestClient
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage, BaseMessage
from pydantic import Field

from app.config import Settings
from app.main import create_app

INTERNAL_TOKEN = "test-internal-token-123"
PROPERTY_SERVICE = "http://property-service.test"

_call_ids = itertools.count(1)


class FakeChatModel(GenericFakeChatModel):
    """Replays scripted replies and records the messages it was sent."""

    received: list[list[BaseMessage]] = Field(default_factory=list)

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        self.received.append(list(messages))
        return super()._generate(messages, stop=stop, run_manager=run_manager, **kwargs)


def tool_call(name: str, **args) -> AIMessage:
    """A model reply that asks for one tool call."""
    return AIMessage(
        content="",
        tool_calls=[{"name": name, "args": args, "id": f"call-{next(_call_ids)}", "type": "tool_call"}],
    )


@pytest.fixture
def make_client():
    """Starts the app with a fake model that replays `replies`; returns the client and the model."""
    started: list[TestClient] = []

    def _make(replies: Iterable[AIMessage] = (), **overrides) -> tuple[TestClient, FakeChatModel]:
        """`overrides` replaces any Settings field, e.g. max_tool_rounds=2."""
        model = FakeChatModel(messages=iter(replies))
        values = {
            "internal_token": INTERNAL_TOKEN,
            "property_service_url": PROPERTY_SERVICE,
            "llm_model": "fake-model",
        }
        settings = Settings(_env_file=None, **(values | overrides))
        client = TestClient(create_app(settings, model))
        client.__enter__()
        started.append(client)
        return client, model

    yield _make
    for client in started:
        client.__exit__(None, None, None)
