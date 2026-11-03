# User dashboard, alerts card — `GET /api/dashboards/user/alerts`

Alerts card of the normal-user dashboard (SCRUM-84 / SCRUM-97): the current user's configured alert
rules counted by effective active/paused state, plus a bounded list of their newest rules. Built
only from the `alert_rules` table (SCRUM-67 storage).

**Rules, not notifications.** An alert rule is what the user asked to watch (a property or a saved
search, for one event type). A notification is a change that was later detected and queued for
delivery (`alert_outbox`, SCRUM-68). This card counts rules only; it never reads the outbox, never
evaluates a rule and never sends anything.

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
| `count` | the user's total number of alert rules; always `activeCount + pausedCount` |
| `activeCount` | rules that will be evaluated: not paused themselves and not linked to a paused saved search |
| `pausedCount` | rules that are effectively paused: paused themselves **or** linked to a paused saved search |
| `limit` | the bound that was applied |
| `recent` | newest rules first, at most `limit` items; `[]` when the user has none |

Each item of `recent`:

| Field | Meaning |
|---|---|
| `id` | rule id, usable with `GET /api/alerts/{id}` |
| `name` | the name given by the user |
| `eventType` | `NEW_MATCH`, `LISTING_PRICE_CHANGED` or `VALUATION_CHANGED` |
| `propertyId` | watched property, or `null` when the rule watches a saved search |
| `savedSearchId` | watched saved search, or `null` when the rule watches a property |
| `cadence` | requested evaluation frequency, `DAILY` or `WEEKLY` |
| `paused` | the rule's own pause flag |
| `effectivePaused` | `paused` OR the linked saved search is paused; the state used for the counts |
| `createdAt` | when the rule was configured; the list is ordered by this, newest first |
| `updatedAt` | last rename, reschedule, pause or resume |

The two pause flags are the same ones `GET /api/alerts/{id}` returns, computed the same way, so the
dashboard and the rule page never disagree.

Rules:

- **ownership**: every query is filtered by the current user's id; other users' rules are never read;
- **empty stays empty**: a user without rules gets `count 0`, `activeCount 0`, `pausedCount 0` and `recent []`;
- **bounded**: `recent` never exceeds `limit`; the counts are unaffected by `limit`;
- **effective state**: a rule on a paused saved search counts as paused even if its own flag is `false`
  (SCRUM-67: "the linked search's paused setting also pauses its rules");
- **rules only**: nothing from `alert_outbox` is read or counted;
- a user seen for the first time gets their user row created during this call (same behaviour as
  every other personal endpoint); the card then shows zero counts.

Dates are serialized the way the dashboard cards serialize `LocalDateTime`, as
`[year, month, day, hour, minute, second, nanos]` arrays (project-wide Jackson configuration; the
alert CRUD endpoints opt into ISO strings per field, the cards do not).

### Examples

User with three rules: one active (NEW_MATCH on an active search), one paused by itself (on a
property), one paused through its saved search:

```
GET /api/dashboards/user/alerts
Authorization: Bearer <token of dash_user_a>

{ "scope": "CURRENT_USER", "generatedAt": [2026, 10, 30, 15, 22, 40, 322732900],
  "status": "OK", "error": null, "count": 3, "activeCount": 1, "pausedCount": 2, "limit": 5,
  "recent": [
    { "id": 3, "name": "Valuation on paused search", "eventType": "VALUATION_CHANGED",
      "propertyId": null, "savedSearchId": 6, "cadence": "DAILY", "paused": false, "effectivePaused": true,
      "createdAt": [2026, 10, 30, 15, 21, 25, 444708000], "updatedAt": [2026, 10, 30, 15, 21, 25, 444708000] },
    { "id": 2, "name": "Price change on property 4", "eventType": "LISTING_PRICE_CHANGED",
      "propertyId": 4, "savedSearchId": null, "cadence": "DAILY", "paused": true, "effectivePaused": true,
      "createdAt": [2026, 10, 30, 15, 21, 24, 675346000], "updatedAt": [2026, 10, 30, 15, 21, 24, 675346000] },
    { "id": 1, "name": "New Miami matches", "eventType": "NEW_MATCH",
      "propertyId": null, "savedSearchId": 5, "cadence": "DAILY", "paused": false, "effectivePaused": false,
      "createdAt": [2026, 10, 30, 15, 21, 23, 503665000], "updatedAt": [2026, 10, 30, 15, 21, 23, 503665000] } ] }
```

Same user, `limit=2` (counts unchanged, list trimmed):

```
GET /api/dashboards/user/alerts?limit=2

{ ..., "count": 3, "activeCount": 1, "pausedCount": 2, "limit": 2,
  "recent": [ { "id": 3, ... }, { "id": 2, ... } ] }
```

Another user at the same moment (ownership):

```
GET /api/dashboards/user/alerts
Authorization: Bearer <token of dash_user_b>

{ "scope": "CURRENT_USER", "generatedAt": [...], "status": "OK", "error": null,
  "count": 0, "activeCount": 0, "pausedCount": 0, "limit": 5, "recent": [] }
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
(`500`, or `409` on some endpoints). Either keep the subjects by importing the users with their
stored `keycloak_user_id` (`kcadm.sh create partialImport -r geozentra -f users.json`, a JSON with
`users[].id` set; `create users -s id=...` ignores the id), or use new usernames and emails.

A token is then obtained with the password grant (the Postman collection does this in its first
requests):

```
POST http://localhost:8090/realms/geozentra/protocol/openid-connect/token
client_id=geozentra-postman&grant_type=password&username=dash_user_a&password=Test1234!
```

To give a user rules for the test, use the existing endpoints with that user's token: two saved
searches through `POST /api/saved-searches` (one with `"paused": true`), then three rules through
`POST /api/alerts`: `NEW_MATCH` on the active search, `LISTING_PRICE_CHANGED` on an existing
property with `"paused": true`, and `VALUATION_CHANGED` on the paused search. Expected card:
`count 3`, `activeCount 1`, `pausedCount 2`. `DELETE /api/alerts/{id}` and
`DELETE /api/saved-searches/{id}` remove them afterwards; the collection stores the created ids and
deletes them in its cleanup requests.

## Implementation

`UserDashboardAlertsController.alerts` (`limit` query parameter, no logic) →
`UserDashboardAlertsService.getAlertsCard` (`UserDashboardAlertsServiceImpl`, `@Transactional`) →
`CurrentUserService.getCurrentUserEntity()` for the owner id, then three `AlertRuleRepository`
queries: `countByUser_Id` (total), `countEffectivelyPausedByUserId` (JPQL with a `LEFT JOIN` on
the saved search: `r.paused = true OR s.paused = true`) and
`findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))` (with an entity graph on the
saved search, so computing `effectivePaused` costs no extra query per rule). `activeCount` is
`count - pausedCount`.

The transaction is deliberately **not** read-only: resolving the current user inserts their row on
a first login, which Postgres rejects inside a read-only transaction (same choice as
`SavedSearchServiceImpl`).

DTO: `AlertsCardDTO` with nested `RecentAlertRule`. No schema change. The card endpoints live in one
controller class per card under `/api/dashboards/user` (favorites: SCRUM-95, saved searches:
SCRUM-96); the composed `GET /api/dashboards/user` (SCRUM-102) is expected to call the same services
and set `status`/`error` on failure. Manual checks:
`docs/postman/SCRUM-97-dashboard-alerts.postman_collection.json`.
