"""HTTP client for Spring's property-service. Every call carries the user's own token."""

from typing import Any

import httpx

# Facts the assistant needs. Owners (personal data), sales, taxes and parcel geometry stay out.
_PROPERTY_FIELDS = (
    "id",
    "folio",
    "countyName",
    "stateCode",
    "dorDescription",
    "bedroomCount",
    "bathroomCount",
    "halfBathroomCount",
    "buildingHeatedArea",
    "buildingGrossArea",
    "lotSize",
    "yearBuilt",
    "floorCount",
    "unitCount",
    "municipality",
    "neighborhoodDescription",
    "subdivision",
    "primaryZoneDescription",
)


class PropertyServiceError(Exception):
    """Property data could not be read. `reason` is safe to show to the model and the user."""

    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


class PropertyApiClient:
    def __init__(self, http: httpx.AsyncClient):
        self._http = http

    async def get_property_summary(self, property_id: int, user_token: str | None) -> dict[str, Any]:
        headers = {"Authorization": f"Bearer {user_token}"} if user_token else {}
        try:
            response = await self._http.get(f"/api/properties/id/{property_id}", headers=headers)
        except httpx.HTTPError as error:
            raise PropertyServiceError("The property service is unavailable.") from error
        if response.status_code == 404:
            raise PropertyServiceError(f"No property with ID {property_id}.")
        if response.status_code in (401, 403):
            raise PropertyServiceError("The user is not allowed to view this property.")
        if response.is_error:
            raise PropertyServiceError("The property service returned an error.")
        return summarize_property(response.json())


def summarize_property(data: dict[str, Any]) -> dict[str, Any]:
    """Reduces Spring's PropertyDTO to the fields above, without empty values."""
    summary = {key: data[key] for key in _PROPERTY_FIELDS if data.get(key) is not None}
    address = data.get("address") or {}
    parts = [address.get(key) for key in ("address", "city", "zip") if address.get(key)]
    if parts:
        summary["address"] = ", ".join(parts)
    return summary
