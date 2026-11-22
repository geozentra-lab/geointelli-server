"""Runs one chat turn through the agent and shapes the result for Spring."""

import re

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.errors import GraphRecursionError
from langgraph.graph.state import CompiledStateGraph

from app.agent.graph import recursion_limit
from app.api.schemas import ChatRequest, ChatResponse, Evidence, Usage
from app.clients.property_api import PropertyApiClient

# Qwen3 on older Ollama versions returns its reasoning inline; it never belongs in the answer.
_THINKING = re.compile(r"<think>.*?</think>", re.DOTALL)

ROUND_LIMIT_ANSWER = "I couldn't finish looking this up. Please ask a narrower question."
EMPTY_ANSWER = "I couldn't produce an answer. Please rephrase your question."


async def run_turn(
    agent: CompiledStateGraph,
    request: ChatRequest,
    *,
    system_prompt: str,
    property_api: PropertyApiClient,
    user_token: str | None,
    max_tool_rounds: int,
    model_name: str,
) -> ChatResponse:
    prompt = build_messages(request, system_prompt)
    config = {
        "recursion_limit": recursion_limit(max_tool_rounds),
        # Read by tools only; none of it is sent to the model.
        "configurable": {"property_api": property_api, "user_token": user_token},
    }
    try:
        state = await agent.ainvoke({"messages": prompt}, config=config)
    except GraphRecursionError:
        return ChatResponse(status="CANNOT_ANSWER", answer=ROUND_LIMIT_ANSWER)

    produced = state["messages"][len(prompt) :]
    answer = _THINKING.sub("", str(produced[-1].text)).strip()
    evidence = [
        Evidence(tool=message.name, data=message.artifact)
        for message in produced
        if isinstance(message, ToolMessage) and message.artifact is not None
    ]
    usage = _usage(produced, model_name)
    if not answer:
        return ChatResponse(status="CANNOT_ANSWER", answer=EMPTY_ANSWER, evidence=evidence, usage=usage)
    return ChatResponse(status="ANSWERED", answer=answer, evidence=evidence, usage=usage)


def build_messages(request: ChatRequest, system_prompt: str) -> list[BaseMessage]:
    messages: list[BaseMessage] = [SystemMessage(system_prompt)]
    for turn in request.history:
        messages.append(HumanMessage(turn.text) if turn.role == "user" else AIMessage(turn.text))
    # The selected property travels with the question so the system prompt stays identical on every request.
    question = request.message
    if request.context.property_id is not None:
        question = f"[Selected property ID: {request.context.property_id}]\n\n{question}"
    messages.append(HumanMessage(question))
    return messages


def _usage(messages: list[BaseMessage], model_name: str) -> Usage:
    usage = Usage(model=model_name)
    for message in messages:
        if isinstance(message, AIMessage) and message.usage_metadata:
            usage.input_tokens += message.usage_metadata["input_tokens"]
            usage.output_tokens += message.usage_metadata["output_tokens"]
    return usage
