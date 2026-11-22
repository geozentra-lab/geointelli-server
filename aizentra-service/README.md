# aizentra-service

The AI service behind AiZentra, GeoZentra's property assistant. It answers a question about a property
with a Qwen model and tools that read data from `property-service` **as the current user**.

Only Spring calls this service. Angular never calls it directly.

```
Spring ── POST /v1/chat ─────────────▶ aizentra-service
          X-Internal-Token: <shared secret>       │
          Authorization: Bearer <user's JWT>      ▼
                                       LangGraph agent:  agent ⇄ tools
                                                          │
          property-service ◀── GET /api/properties/id/{id}, same user JWT
```

- The model never sees the user's token. Tools get it from the LangGraph run config, not as an argument.
- The service keeps no state. Spring sends the recent history with every question.
- Figures in the UI should come from `evidence`, which is the tool data exactly as Spring returned it.

## Run locally

Needs Python 3.12 and a recent [Ollama](https://ollama.com).

```shell
ollama pull qwen3:8b

cd aizentra-service
py -3.12 -m venv .venv
.venv\Scripts\activate          # Git Bash: source .venv/Scripts/activate
pip install -e ".[dev]"
copy .env.example .env          # Git Bash: cp .env.example .env

uvicorn app.main:create_app --factory --port 8000
```

Start `property-service` too, at `PROPERTY_SERVICE_URL`. Then ask a question in the Swagger UI at
http://localhost:8000/docs, or:

```shell
curl -X POST http://localhost:8000/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: local-dev-internal-token-change-me" \
  -d '{"message": "How many bedrooms does this property have?", "context": {"propertyId": 123}}'
```

Use the ID of a property in your database. Spring will send `Authorization: Bearer <user JWT>` as well.

### Playground (temporary)

Set `PLAYGROUND_ENABLED=true` in `.env`, restart, and open http://localhost:8000/playground. Enter a
property ID and the `INTERNAL_TOKEN` from `.env`, then chat. The page sends the conversation history
the way Spring will, and shows the tool data (evidence) under each answer. It is off by default; to
remove it, delete `app/playground/` and its two lines in `app/main.py`.

## Contract

Request:

```json
{
  "conversationId": "c-1",
  "turnId": "t-7",
  "message": "When was it built?",
  "context": { "propertyId": 123 },
  "history": [{ "role": "user", "text": "Hi" }, { "role": "assistant", "text": "Hello!" }]
}
```

Response (`status` is `ANSWERED` or `CANNOT_ANSWER`; `503` means the model server is down):

```json
{
  "status": "ANSWERED",
  "answer": "It was built in 1987.",
  "evidence": [{ "tool": "get_property_details", "data": { "id": 123, "yearBuilt": "1987" } }],
  "usage": { "model": "qwen3:8b", "inputTokens": 812, "outputTokens": 41 }
}
```

## Tests

```shell
pytest
ruff check . && ruff format --check .
```

Tests use a fake model and a mocked `property-service`, so they need neither Ollama nor Spring.

## Layout

```
app/
  main.py               app factory, /health
  config.py             settings from the environment / .env
  security.py           X-Internal-Token check, user token extraction
  api/chat.py           POST /v1/chat
  api/schemas.py        the Spring <-> Python contract (camelCase JSON)
  agent/llm.py          the model client: the only file that knows Ollama/vLLM
  agent/prompts.py      system prompt
  agent/tools.py        tools the model may call
  agent/graph.py        the LangGraph loop
  agent/runner.py       one chat turn: prompt in, answer + evidence out
  clients/property_api.py   HTTP client for property-service
tests/
```

## Adding a tool

1. Add a method to a client in `app/clients/`. Return only the fields the model needs, and turn
   Spring errors into a `PropertyServiceError` with a short, user-safe reason.
2. Add a `@tool(response_format="content_and_artifact")` function in `app/agent/tools.py` that
   returns `(json_text, data)`, and add it to `TOOLS`.
3. Add a test that scripts the model's tool call with `tool_call(...)` in `tests/conftest.py`.
