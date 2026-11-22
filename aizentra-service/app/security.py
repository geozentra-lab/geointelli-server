"""Only Spring may call this service: every request must carry the shared internal token."""

import secrets
from typing import Annotated

from fastapi import Header, HTTPException, Request, status


async def require_internal_token(
    request: Request, x_internal_token: Annotated[str | None, Header()] = None
) -> None:
    expected = request.app.state.settings.internal_token.get_secret_value()
    if x_internal_token is None or not secrets.compare_digest(x_internal_token.encode(), expected.encode()):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing or invalid internal token.")


def bearer_token(authorization: str | None) -> str | None:
    """The user's Keycloak token from `Authorization: Bearer ...`, passed on to Spring unchanged."""
    if authorization and authorization.lower().startswith("bearer "):
        return authorization[len("bearer ") :].strip() or None
    return None
