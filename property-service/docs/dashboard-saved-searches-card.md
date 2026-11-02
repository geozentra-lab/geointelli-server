# User dashboard, saved-search card — `GET /api/dashboards/user/saved-searches`

Saved-search card of the normal-user dashboard (SCRUM-84 / SCRUM-96): the current user's real
saved-search count and a bounded list of their newest saved searches, each with its monitoring
state (`cadence`, `paused`). Built only from the stored definitions in `saved_searches`
(SCRUM-66 storage); **no saved search is executed**, nothing is scheduled or notified.

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
| `count` | the user's total number of saved searches, not only the ones listed |
| `limit` | the bound that was applied |
| `recent` | newest saved searches first, at most `limit` items; `[]` when the user has none |

Each item of `recent`:

| Field | Meaning |
|---|---|
| `id` | saved-search id, usable with `GET /api/saved-searches/{id}` and `/results` |
| `name` | the name given by the user |
| `cadence` | monitoring state: requested frequency, `DAILY` or `WEEKLY` |
| `paused` | monitoring state: `true` when monitoring is paused (manual replay stays available) |
| `createdAt` | when the search was saved; the list is ordered by this, newest first |
| `updatedAt` | last rename, edit, pause or resume |

The stored `filters` are deliberately **not** part of the card (a card is a summary); the full
definition is available from `GET /api/saved-searches/{id}`.

Rules:

- **ownership**: both queries are filtered by the current user's id; other users' saved searches are never read;
- **empty stays empty**: a user without saved searches gets `count 0` and `recent []`, nothing invented;
- **bounded**: `recent` never exceeds `limit`; `count` is unaffected by `limit`;
- **no execution**: the count is a row count on `saved_searches`, the searches are never replayed to build the card;
- a user seen for the first time gets their user row created during this call (same behaviour as
  every other personal endpoint); the card then shows `count 0`.

Dates are serialized the way the dashboard cards serialize `LocalDateTime`, as
`[year, month, day, hour, minute, second, nanos]` arrays (project-wide Jackson configuration; the
saved-search CRUD endpoints opt into ISO strings per field, the cards do not).

### Examples

User with three saved searches, default limit (one of them paused, weekly):

```
GET /api/dashboards/user/saved-searches
Authorization: Bearer <token of dash_user_a>

{ "scope": "CURRENT_USER", "generatedAt": [2026, 10, 29, 17, 20, 17, 57920400],
  "status": "OK", "error": null, "count": 3, "limit": 5,
  "recent": [
    { "id": 3, "name": "Zip 33183", "cadence": "DAILY", "paused": false,
      "createdAt": [2026, 10, 29, 17, 19, 32, 270271000], "updatedAt": [2026, 10, 29, 17, 19, 32, 270609000] },
    { "id": 2, "name": "Weekly paused: 3+ bedrooms", "cadence": "WEEKLY", "paused": true,
      "createdAt": [2026, 10, 29, 17, 19, 31, 308748000], "updatedAt": [2026, 10, 29, 17, 19, 31, 648948000] },
    { "id": 1, "name": "Miami homes", "cadence": "DAILY", "paused": false,
      "createdAt": [2026, 10, 29, 17, 19, 30, 427050000], "updatedAt": [2026, 10, 29, 17, 19, 30, 427050000] } ] }
```

Same user, `limit=2` (count unchanged, list trimmed):

```
GET /api/dashboards/user/saved-searches?limit=2

{ ..., "count": 3, "limit": 2, "recent": [ { "id": 3, ... }, { "id": 2, ... } ] }
```

Another user at the same moment (ownership):

```
GET /api/dashboards/user/saved-searches
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

The application maps a token to its `users` row by the Keycloak subject (`sub`) and inserts the row
when unknown. If `dash_user_a`/`dash_user_b` already exist in the target database from an earlier
run, a freshly created Keycloak user gets a new subject and the insert fails on the unique email
(`500`, or `409` on the saved-search endpoints). Either keep the subjects by importing the users
with their stored `keycloak_user_id` (`kcadm.sh create partialImport -r geozentra -f users.json`,
a JSON with `users[].id` set; `create users -s id=...` ignores the id), or use new usernames and
emails.

A token is then obtained with the password grant (the Postman collection does this in its first
requests):

```
POST http://localhost:8090/realms/geozentra/protocol/openid-connect/token
client_id=geozentra-postman&grant_type=password&username=dash_user_a&password=Test1234!
```

To give a user saved searches for the test, use the existing `POST /api/saved-searches` with that
user's token, for example `{ "name": "Miami homes", "filters": { "city": "Miami" } }`;
`DELETE /api/saved-searches/{id}` removes them afterwards. The collection stores the created ids
in `ssId1..ssId3` and deletes them in its cleanup requests.

## Implementation

`UserDashboardSavedSearchesController.savedSearches` (`limit` query parameter, no logic) →
`UserDashboardSavedSearchesService.getSavedSearchesCard` (`UserDashboardSavedSearchesServiceImpl`,
`@Transactional`) → `CurrentUserService.getCurrentUserEntity()` for the owner id, then
`SavedSearchRepository.countByUser_Id` and
`SavedSearchRepository.findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))`.

The transaction is deliberately **not** read-only: resolving the current user inserts their row on
a first login, which Postgres rejects inside a read-only transaction (same choice as
`SavedSearchServiceImpl`).

DTO: `SavedSearchesCardDTO` with nested `RecentSavedSearch`. No schema change; the two repository
methods are derived queries on the existing `saved_searches` table. The card endpoints live in one
controller class per card under `/api/dashboards/user` (favorites: SCRUM-95); the composed
`GET /api/dashboards/user` (SCRUM-102) is expected to call the same services and set
`status`/`error` on failure. Manual checks:
`docs/postman/SCRUM-96-dashboard-saved-searches.postman_collection.json`.
