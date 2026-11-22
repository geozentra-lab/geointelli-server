"""The contract between Spring and this service. JSON uses camelCase to match the Java side."""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class _Camel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel, validate_by_name=True, validate_by_alias=True, serialize_by_alias=True
    )


class HistoryMessage(_Camel):
    role: Literal["user", "assistant"]
    text: str


class ChatContext(_Camel):
    property_id: int | None = None


class ChatRequest(_Camel):
    conversation_id: str | None = None
    turn_id: str | None = None
    message: str = Field(min_length=1, max_length=4000)
    context: ChatContext = Field(default_factory=ChatContext)
    # Spring sends the recent turns; this service keeps no conversation state.
    history: list[HistoryMessage] = Field(default_factory=list, max_length=20)


class Evidence(_Camel):
    """Data a tool returned, exactly as returned. The UI shows figures from here, not from the answer text."""

    tool: str
    data: dict[str, Any]


class Usage(_Camel):
    model: str
    input_tokens: int = 0
    output_tokens: int = 0


class ChatResponse(_Camel):
    status: Literal["ANSWERED", "CANNOT_ANSWER"]
    answer: str
    evidence: list[Evidence] = Field(default_factory=list)
    usage: Usage | None = None
