# User dashboard, recent-activity card — `GET /api/dashboards/user/activity`

Recent-activity card of the normal-user dashboard (SCRUM-84 / SCRUM-101): a bounded, newest-first
list of the things the current user did in the application.

**Where the activity comes from.** The project keeps no activity log: no table records "user X did Y
at time Z". What every personal record does carry is a `created_at` and an `updated_at` (the
`AuditableEntity` base class). The card is derived from those stored timestamps on the records the
user owns:

| Record | Table | Items it can produce |
|---|---|---|
| favorite | `property_favorites` | `FAVORITE_ADDED` at `created_at` (favorites are never edited) |
| saved search | `saved_searches` | `SAVED_SEARCH_CREATED` at `created_at`, `SAVED_SEARCH_UPDATED` at `updated_at` |
| alert rule | `alert_rules` | `ALERT_RULE_CREATED`, `ALERT_RULE_UPDATED` |
| portfolio | `portfolios` | `PORTFOLIO_CREATED`, `PORTFOLIO_UPDATED` |
| portfolio holding | `portfolio_holdings` (owned through the portfolio) | `HOLDING_ADDED`, `HOLDING_UPDATED` |

Every item is one real stored row and one real stored time. Nothing is synthesized, and nothing of
other users is read.

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
| `limit` | the bound that was applied |
| `recent` | activity items, newest first, at most `limit`; `[]` when the user owns no records |

There is no total `count`, unlike the favorites, saved-search and alerts cards: those totals are one
cheap `COUNT(*)`, whereas a count of "every activity ever" would mean reading every row of five tables.

Each item of `recent`:

| Field | Meaning |
|---|---|
| `type` | one of the nine values below |
| `occurredAt` | the stored `created_at` (created/added types) or `updated_at` (updated types) of the record |
| `entityId` | id of the record: the favorite, saved search, alert rule, portfolio or holding; usable with the matching `GET` endpoint |
| `label` | readable summary: the saved search, rule or portfolio name; `property <id>` for a favorite; `property <id> in <portfolio name>` for a holding |
| `propertyId` | the property involved (favorite, holding, alert rule on a property); `null` otherwise |
| `portfolioId` | for holdings, the portfolio they belong to; `null` for every other type |

`type` values:

| Type | Meaning | Timestamp used |
|---|---|---|
| `FAVORITE_ADDED` | the user favorited a property (`POST /api/properties/{id}/favorite`) | `created_at` |
| `SAVED_SEARCH_CREATED` | the user saved a search | `created_at` |
| `SAVED_SEARCH_UPDATED` | the user renamed, paused, resumed or rescheduled a saved search | `updated_at` |
| `ALERT_RULE_CREATED` | the user configured an alert rule | `created_at` |
| `ALERT_RULE_UPDATED` | the user renamed, paused, resumed or rescheduled a rule | `updated_at` |
| `PORTFOLIO_CREATED` | the user created a portfolio | `created_at` |
| `PORTFOLIO_UPDATED` | the user renamed a portfolio or changed its description | `updated_at` |
| `HOLDING_ADDED` | the user added a property to one of their portfolios | `created_at` |
| `HOLDING_UPDATED` | the user edited a holding (price, financing, occupancy, …) | `updated_at` |

Rules:

- **ownership**: every query is filtered by the current user's id (for holdings, through the
  portfolio's owner); other users' records are never read;
- **empty stays empty**: a user without records gets `recent []`;
- **bounded**: `recent` never exceeds `limit`;
- **created and updated**: a record that was never modified gives exactly one item (its
  `created_at` and `updated_at` are the same instant); a record modified later gives two items, the
  "created" one at `created_at` and the "updated" one at `updated_at`. Several modifications still
  give one "updated" item, at the last modification time, because only the last `updated_at` is stored;
- **what is not shown**: anything that leaves no row behind, because nothing records it: removing a
  favorite, deleting a search, rule, portfolio or holding, logging in, viewing a property, running a
  search without saving it. A dedicated activity log fed by every write endpoint would be a separate
  ticket; this card would then only change where it reads from, not its contract;
- a user seen for the first time gets their user row created during this call (same behaviour as
  every other personal endpoint); the card then shows an empty list.

Dates (`generatedAt`, `occurredAt`) are ISO-8601 local date-times such as
`2026-11-09T12:19:30.926681`, no time zone suffix (project-wide Jackson configuration, `JacksonConfig`,
which disables `WRITE_DATES_AS_TIMESTAMPS`; the earlier card docs still describe the former array
form).

### Examples

User who favorited property 4, saved a search, created an alert on it, created a portfolio, added
property 4 to it, then renamed the search:

```
GET /api/dashboards/user/activity
Authorization: Bearer <token of dash_user_a>

{ "scope": "CURRENT_USER", "generatedAt": "2026-11-09T12:19:52.8734314",
  "status": "OK", "error": null, "limit": 5,
  "recent": [
    { "type": "SAVED_SEARCH_UPDATED", "occurredAt": "2026-11-09T12:19:30.926681", "entityId": 10,
      "label": "Miami homes (renamed)", "propertyId": null, "portfolioId": null },
    { "type": "HOLDING_ADDED", "occurredAt": "2026-11-09T12:18:17.507256", "entityId": 2,
      "label": "property 4 in Activity card test", "propertyId": 4, "portfolioId": 3 },
    { "type": "PORTFOLIO_CREATED", "occurredAt": "2026-11-09T12:18:15.563425", "entityId": 3,
      "label": "Activity card test", "propertyId": null, "portfolioId": null },
    { "type": "ALERT_RULE_CREATED", "occurredAt": "2026-11-09T12:18:13.960359", "entityId": 4,
      "label": "New Miami matches", "propertyId": null, "portfolioId": null },
    { "type": "SAVED_SEARCH_CREATED", "occurredAt": "2026-11-09T12:18:11.851762", "entityId": 10,
      "label": "Miami homes (renamed)", "propertyId": null, "portfolioId": null } ] }
```

(The `FAVORITE_ADDED` item, the oldest of the six, is cut by `limit=5`; `limit=6` shows it.
`label` always shows the record's *current* name, also on the "created" item.)

Same user, `limit=2`:

```
GET /api/dashboards/user/activity?limit=2

{ ..., "limit": 2, "recent": [ { "type": "SAVED_SEARCH_UPDATED", ... }, { "type": "HOLDING_ADDED", ... } ] }
```

Another user at the same moment (ownership):

```
GET /api/dashboards/user/activity
Authorization: Bearer <token of dash_user_b>

{ "scope": "CURRENT_USER", "generatedAt": "2026-11-09T12:18:50.6616978", "status": "OK", "error": null, "limit": 5, "recent": [] }
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

To give a user activity for the test, use the existing endpoints with that user's token, in this
order (each call is one item; property 4 must exist):

1. `POST /api/properties/4/favorite` → `FAVORITE_ADDED`
2. `POST /api/saved-searches` `{ "name": "Miami homes", "filters": { "city": "Miami" } }` → `SAVED_SEARCH_CREATED`
3. `POST /api/alerts` `{ "name": "New Miami matches", "eventType": "NEW_MATCH", "savedSearchId": <2> }` → `ALERT_RULE_CREATED`
4. `POST /api/portfolios` `{ "name": "Activity card test" }` → `PORTFOLIO_CREATED`
5. `POST /api/portfolios/<4>/holdings` `{ "propertyId": 4 }` → `HOLDING_ADDED`
6. `PATCH /api/saved-searches/<2>` `{ "name": "Miami homes (renamed)" }` → adds `SAVED_SEARCH_UPDATED`

Expected card with the default limit: the five newest items in the order 6, 5, 4, 3, 2; `limit=6`
also shows the favorite; the "created" item of the search still exists, with the new name. Clean up
with `DELETE /api/portfolios/<4>` (removes its holdings), `DELETE /api/alerts/<3>`,
`DELETE /api/saved-searches/<2>` and `DELETE /api/properties/4/favorite`; the collection stores the
created ids and deletes them in its cleanup requests. After cleanup the card is empty again, which
is also the demonstration that deletions leave no trace.

## Implementation

`UserDashboardActivityController.activity` (`limit` query parameter, no logic) →
`UserDashboardActivityService.getActivityCard` (`UserDashboardActivityServiceImpl`,
`@Transactional`) → `CurrentUserService.getCurrentUserEntity()` for the owner id, then five
owner-scoped, bounded queries with `PageRequest.of(0, limit)`:

| Repository | Method |
|---|---|
| `FavoriteRepository` | `findByUserIdOrderByCreatedAtDesc` (existing) |
| `SavedSearchRepository` | `findByUser_IdOrderByUpdatedAtDesc` |
| `AlertRuleRepository` | `findByUser_IdOrderByUpdatedAtDesc` |
| `PortfolioRepository` | `findByUser_IdOrderByUpdatedAtDesc` |
| `PortfolioHoldingRepository` | `findByPortfolio_User_IdOrderByUpdatedAtDesc` (entity graph on the portfolio, for its name) |

Each row becomes a "created" item and, when `updated_at` is after `created_at`, an "updated" item.
The items are merged in memory, sorted by `occurredAt` descending and cut to `limit`. Reading
`limit` rows per table ordered by `updated_at` is sufficient: a row's `updated_at` is never earlier
than its `created_at`, so any row able to contribute one of the newest `limit` items is among them.
At most `5 × limit` rows are read; property ids come from the foreign keys already loaded, so no
query per row is triggered.

The transaction is deliberately **not** read-only: resolving the current user inserts their row on
a first login, which Postgres rejects inside a read-only transaction (same choice as the other cards).

DTO: `ActivityCardDTO` with nested `ActivityType` and `ActivityItem`. No schema change. The card
endpoints live in one controller class per card under `/api/dashboards/user` (favorites: SCRUM-95,
saved searches: SCRUM-96, alerts: SCRUM-97, affordability: SCRUM-99); the composed
`GET /api/dashboards/user` (SCRUM-102) is expected to call the same services and set
`status`/`error` on failure. Manual checks:
`docs/postman/SCRUM-101-dashboard-activity.postman_collection.json`.
