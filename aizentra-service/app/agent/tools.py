"""Tools the model may call. Each reads Spring data as the current user.

Per-request values (the API client, the user's token) arrive through the run config. They are not
tool arguments, so the model never sees or fills them.
"""

import json
from typing import Any

from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool

from app.clients.property_api import PropertyApiClient, PropertyServiceError


@tool(response_format="content_and_artifact")
async def get_property_details(property_id: int, config: RunnableConfig) -> tuple[str, dict[str, Any] | None]:
    """Get the basic facts of one property: address, county, property type, bedrooms, bathrooms,
    building area, lot size, year built, floors, units, municipality, neighborhood and zoning.

    property_id is GeoZentra's internal property ID, such as the selected property's ID.
    """
    run = config["configurable"]
    client: PropertyApiClient = run["property_api"]
    try:
        summary = await client.get_property_summary(property_id, run.get("user_token"))
    except PropertyServiceError as error:
        return json.dumps({"available": False, "reason": error.reason}), None
    # The model reads the JSON text; the dict travels as the artifact and becomes the answer's evidence.
    return json.dumps(summary), summary


TOOLS = [get_property_details]
