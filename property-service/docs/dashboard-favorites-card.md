# User dashboard, favorites card — `GET /api/dashboards/user/favorites`

Favorites card of the normal-user dashboard (SCRUM-84 / SCRUM-95): the current user's real
favorite count and a bounded list of their newest favorites. Built only from the existing
`property_favorites` records; no valuation, no notification, nothing computed.

Authentication: `Authorization: Bearer <token>` is **required**. The user is taken from the token;
there is no user parameter, so a caller can only ever see their own card.

## Request

| Part | Name | Type | Required | Meaning |
|---|---|---|---|---|
| query | `limit` | integer 1–20, default `5` | no | maximum number of items in `recent` |

## Response

`200 OK` with:

| Field | Meaning |
|---|---|
| `scope` | whose data the card shows; always `CURRENT_USER` |
| `generatedAt` | freshness: server time at which the card was built |
| `status` | `OK` when the data loaded; `ERROR` is reserved for the composed dashboard (SCRUM-102) when it cannot load this card |
| `error` | reason when `status` is `ERROR`; `null` otherwise |
| `count` | the user's total number of favorites, not only the ones listed |
| `limit` | the bound that was applied |
| `recent` | newest favorites first, at most `limit` items; `[]` when the user has none |

Each item of `recent`: `propertyId`, `folio`, `address`, `countyName`, `stateCode`, `favoritedAt`
(when the user favorited the property). These are display fields read from the property the
favorite points to; a property without an address or county yields `null` for that field.

Rules:

- **ownership**: both queries are filtered by the current user's id; other users' favorites are never read;
- **empty stays empty**: a user without favorites gets `count 0` and `recent []`, nothing invented;
- **bounded**: `recent` never exceeds `limit`; `count` is unaffected by `limit`;
- a user seen for the first time gets their user row created during this call (same behaviour as
  every other personal endpoint); the card then shows `count 0`.

Dates are serialized the way every endpoint of this service serializes `LocalDateTime`, as
`[year, month, day, hour, minute, second, nanos]` arrays (project-wide Jackson configuration).

### Examples

User with three favorites, default limit:

```
GET /api/dashboards/user/favorites
Authorization: Bearer <token of dash_user_a>

{ "scope": "CURRENT_USER", "generatedAt": [2026, 10, 29, 10, 41, 42, 681507700],
  "status": "OK", "error": null, "count": 3, "limit": 5,
  "recent": [
    { "propertyId": 4, "folio": "3049340031160",
      "address": "7600 SW 136 CT, Unincorporated County, FL 33183-0000",
      "countyName": "Miami-Dade", "stateCode": "FL", "favoritedAt": [2026, 10, 29, 10, 39, 56, 984731000] },
    { "propertyId": 3, "folio": "3049340031200",
      "address": "13660 SW 79 ST, Unincorporated County, FL 33183-0000",
      "countyName": "Miami-Dade", "stateCode": "FL", "favoritedAt": [2026, 10, 29, 10, 39, 55, 969193000] },
    { "propertyId": 2, "folio": "3049340031190",
      "address": "13670 SW 79 ST, Unincorporated County, FL 33183-0000",
      "countyName": "Miami-Dade", "stateCode": "FL", "favoritedAt": [2026, 10, 29, 10, 39, 52, 409477000] } ] }
```

Same user, `limit=2` (count unchanged, list trimmed):

```
GET /api/dashboards/user/favorites?limit=2

{ ..., "count": 3, "limit": 2, "recent": [ { "propertyId": 4, ... }, { "propertyId": 3, ... } ] }
```

Another user at the same moment (ownership):

```
GET /api/dashboards/user/favorites
Authorization: Bearer <token of dash_user_b>

{ "scope": "CURRENT_USER", "generatedAt": [...], "status": "OK", "error": null,
  "count": 0, "limit": 5, "recent": [] }
```

## Errors

Every error uses the service's standard body `{ "status", "error", "message", "timestamp" }`.

| Case | Status | `message` |
|---|---|---|
| no `Authorization` header | `401` | `A bearer token is required to identify the current user` |
| invalid or expired token | `401` | rejected by Spring Security before the controller (empty body) |
| `limit` < 1 or > 20 | `400` | `limit must be between 1 and 20, got 0` |
| `limit` not a number | `400` | `limit: invalid value` |

`limit` is validated before the user is resolved, so a bad `limit` returns 400 even without a token.

## Verifying locally

Personal endpoints need a token from Keycloak, which the `local` profile expects at
`http://localhost:8090/realms/geozentra`. Without a shared dev Keycloak, run a throwaway one:

```
docker run -d --name kc-local -p 8090:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:24.0 start-dev
docker exec kc-local bash -c '
K=/opt/keycloak/bin/kcadm.sh
$K config credentials --server http://localhost:8080 --realm master --user admin --password admin
$K create realms -s realm=geozentra -s enabled=true -s accessTokenLifespan=3600
$K create clients -r geozentra -s clientId=geozentra-postman -s publicClient=true -s directAccessGrantsEnabled=true -s standardFlowEnabled=false
for u in a b; do
  $K create users -r geozentra -s username=dash_user_$u -s enabled=true -s email=dash_user_$u@example.test -s firstName=Dash -s lastName=User$u
  $K set-password -r geozentra --username dash_user_$u --new-password Test1234!
done'
```

A token is then obtained with the password grant (the Postman collection does this in its first
requests):

```
POST http://localhost:8090/realms/geozentra/protocol/openid-connect/token
client_id=geozentra-postman&grant_type=password&username=dash_user_a&password=Test1234!
```

To give a user favorites for the test, use the existing `POST /api/properties/{id}/favorite`
with that user's token; `DELETE` on the same path removes them afterwards.

## Implementation

`UserDashboardController.favorites` (`limit` query parameter, no logic) →
`UserDashboardFavoritesService.getFavoritesCard` (`UserDashboardFavoritesServiceImpl`,
`@Transactional`) → `CurrentUserService.getCurrentUser()` for the owner id, then
`FavoriteRepository.countByUserId` and `FavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))`.

The transaction is deliberately **not** read-only: resolving the current user inserts their row on
a first login, which Postgres rejects inside a read-only transaction (same choice as
`SavedSearchServiceImpl`).

DTO: `FavoritesCardDTO` with nested `RecentFavorite`. No schema change; the two repository methods
are derived queries on the existing `property_favorites` table. The composed
`GET /api/dashboards/user` (SCRUM-102) is expected to call the same service and set
`status`/`error` on failure. Manual checks: `docs/postman/SCRUM-95-dashboard-favorites.postman_collection.json`.
