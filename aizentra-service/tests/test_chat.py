import json

import httpx
import openai
import respx
from langchain_core.messages import AIMessage

from app.agent.runner import ROUND_LIMIT_ANSWER
from tests.conftest import INTERNAL_TOKEN, PROPERTY_SERVICE, tool_call

PROPERTY_URL = f"{PROPERTY_SERVICE}/api/properties/id/123"
PROPERTY_DTO = {
    "id": 123,
    "folio": "0131230000010",
    "countyName": "Miami-Dade",
    "stateCode": "FL",
    "dorDescription": "RESIDENTIAL - SINGLE FAMILY",
    "bedroomCount": 3,
    "bathroomCount": 2,
    "buildingHeatedArea": 1850,
    "lotSize": 7500,
    "yearBuilt": "1987",
    "neighborhoodDescription": None,
    "address": {"address": "123 NW 5 ST", "city": "Miami", "zip": "33128"},
    "owners": [{"name": "Jane Owner"}],
    "sales": [{"salePrice": 450000}],
}
HEADERS = {"X-Internal-Token": INTERNAL_TOKEN, "Authorization": "Bearer user-jwt"}


def ask(client, message="How many bedrooms does it have?", property_id=123, **extra):
    body = {"turnId": "t-1", "message": message, "context": {"propertyId": property_id}, **extra}
    return client.post("/v1/chat", headers=HEADERS, json=body)


def test_health_needs_no_token(make_client):
    client, _ = make_client()
    assert client.get("/health").json() == {"status": "UP"}


def test_rejects_calls_without_the_internal_token(make_client):
    client, _ = make_client()
    assert client.post("/v1/chat", json={"message": "hi"}).status_code == 401
    wrong = {"X-Internal-Token": "wrong-token-wrong-token"}
    assert client.post("/v1/chat", headers=wrong, json={"message": "hi"}).status_code == 401


@respx.mock
def test_answers_from_the_tool_and_returns_its_data_as_evidence(make_client):
    spring = respx.get(PROPERTY_URL).respond(json=PROPERTY_DTO)
    client, model = make_client(
        [tool_call("get_property_details", property_id=123), AIMessage("It has 3 bedrooms.")]
    )

    response = ask(client)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ANSWERED"
    assert body["answer"] == "It has 3 bedrooms."
    [evidence] = body["evidence"]
    assert evidence["tool"] == "get_property_details"
    assert evidence["data"]["bedroomCount"] == 3
    assert evidence["data"]["address"] == "123 NW 5 ST, Miami, 33128"
    assert "owners" not in evidence["data"] and "sales" not in evidence["data"]
    assert "neighborhoodDescription" not in evidence["data"]
    # Spring was called as the user.
    assert spring.calls.last.request.headers["Authorization"] == "Bearer user-jwt"
    # The model got the selected property, but never the user's token.
    assert "[Selected property ID: 123]" in model.received[0][-1].content
    assert all("user-jwt" not in str(message.content) for prompt in model.received for message in prompt)


@respx.mock
def test_tells_the_model_when_the_property_does_not_exist(make_client):
    respx.get(f"{PROPERTY_SERVICE}/api/properties/id/999").respond(404)
    client, model = make_client(
        [tool_call("get_property_details", property_id=999), AIMessage("I couldn't find that property.")]
    )

    body = ask(client, property_id=999).json()

    assert body["status"] == "ANSWERED"
    assert body["evidence"] == []
    tool_result = model.received[1][-1]
    assert json.loads(tool_result.content) == {"available": False, "reason": "No property with ID 999."}


@respx.mock
def test_tells_the_model_when_spring_is_down(make_client):
    respx.get(PROPERTY_URL).mock(side_effect=httpx.ConnectError("connection refused"))
    client, model = make_client(
        [
            tool_call("get_property_details", property_id=123),
            AIMessage("Property data is unavailable right now."),
        ]
    )

    body = ask(client).json()

    assert body["status"] == "ANSWERED"
    assert json.loads(model.received[1][-1].content)["reason"] == "The property service is unavailable."


@respx.mock
def test_stops_after_the_round_limit(make_client):
    respx.get(PROPERTY_URL).respond(json=PROPERTY_DTO)
    client, _ = make_client(
        [tool_call("get_property_details", property_id=123) for _ in range(3)], max_tool_rounds=2
    )

    body = ask(client).json()

    assert body["status"] == "CANNOT_ANSWER"
    assert body["answer"] == ROUND_LIMIT_ANSWER


def test_removes_thinking_from_the_answer(make_client):
    client, _ = make_client(
        [AIMessage("<think>The user is greeting me.</think>\n\nHello! Ask me about this property.")]
    )

    assert ask(client, message="hi").json()["answer"] == "Hello! Ask me about this property."


def test_sends_history_before_the_question(make_client):
    client, model = make_client([AIMessage("It was built in 1987.")])

    history = [{"role": "user", "text": "Hi"}, {"role": "assistant", "text": "Hello!"}]
    ask(client, message="When was it built?", history=history)

    roles = [type(message).__name__ for message in model.received[0]]
    assert roles == ["SystemMessage", "HumanMessage", "AIMessage", "HumanMessage"]


def test_thinking_switch_controls_the_qwen_no_think_flag(make_client):
    thinking, thinking_model = make_client([AIMessage("Hi!")])
    quick, quick_model = make_client([AIMessage("Hi!")], llm_thinking=False)

    ask(thinking, message="hi")
    ask(quick, message="hi")

    assert not thinking_model.received[0][0].content.endswith("/no_think")
    assert quick_model.received[0][0].content.endswith("/no_think")


def test_returns_503_when_the_model_server_is_down(make_client):
    def unreachable():
        raise openai.APIConnectionError(request=httpx.Request("POST", "http://llm.test/v1/chat/completions"))
        yield

    client, _ = make_client(unreachable())

    response = ask(client)

    assert response.status_code == 503
    assert response.json() == {"detail": "The language model is unavailable."}
