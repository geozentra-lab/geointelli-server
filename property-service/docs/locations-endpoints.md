# Supported geography — `GET /api/locations/states` and `GET /api/locations/counties`

Lists the states and counties the platform supports so a user can pick a geography
(state first, then one of its counties). Both routes read the `states` and `counties`
tables; a geography is "supported" when it is registered there.

These lists are shared reference data. They are not owned by a user, so there is no
ownership check and every caller sees the same rows.

Authentication: the usual `Authorization: Bearer <token>` header, like every `/api` endpoint
(the current security configuration permits the request without one).

Routes live in `LocationController` → `LocationService` / `LocationServiceImpl` →
`StateRepository`, `CountyRepository`, mapped with `LocationMapper`.
There is no `/api/states` or `/api/counties` route; do not add one.

## `GET /api/locations/states`

No parameters.

`200 OK` with a JSON array of `StateDTO`, sorted by state name:

| Field | Type | Meaning |
|---|---|---|
| `id` | integer | database id; use it as `stateId` for the counties route |
| `code` | string, 2 letters | USPS code, unique (for example `FL`) |
| `name` | string | state name, unique ignoring case |

```
GET /api/locations/states

[
  { "id": 1, "code": "FL", "name": "Florida" }
]
```

`[]` when no state is registered.

## `GET /api/locations/counties?stateId=`

| Part | Name | Type | Required | Meaning |
|---|---|---|---|---|
| query | `stateId` | integer | yes | `id` of a state returned by `/api/locations/states` |

`200 OK` with a JSON array of `CountyLocationDTO`, only the counties of that state, sorted
by county name:

| Field | Type | Meaning |
|---|---|---|
| `countyId` | integer | database id of the county |
| `countyName` | string | county name, unique within its state ignoring case |
| `stateCode` | string | code of the owning state |
| `stateName` | string | name of the owning state |

```
GET /api/locations/counties?stateId=1

[
  { "countyId": 51, "countyName": "Alachua", "stateCode": "FL", "stateName": "Florida" },
  { "countyId": 20, "countyName": "Baker",   "stateCode": "FL", "stateName": "Florida" },
  { "countyId": 25, "countyName": "Bay",     "stateCode": "FL", "stateName": "Florida" },
  ...
]

Ids are database ids, so they are stable but not sequential.
```

`[]` when the state exists but has no county yet.

## Errors

All errors use the shared `ApiError` body: `status`, `error`, `message`, `timestamp`
(the timestamp is serialized as epoch seconds with a fractional part).

| Case | Status | `message` |
|---|---|---|
| `stateId` missing | `400 Bad Request` | `stateId is required` |
| `stateId` not an integer (for example `abc`, `1.5`) | `400 Bad Request` | `stateId: invalid value` |
| `stateId` does not match a registered state (for example `0`, `-1`, `999999`) | `404 Not Found` | `State not found` |

```
GET /api/locations/counties?stateId=999999

{ "status": 404, "error": "404 NOT_FOUND", "message": "State not found",
  "timestamp": 1793029507.207838200 }
```

## Manual checks (Postman)

Import `docs/postman/SCRUM-47-locations.postman_collection.json` and set the collection
variable `baseUrl` (default `http://localhost:8081`). Each request carries a test script,
so the Runner reports pass/fail.

| # | Request | Expect |
|---|---|---|
| 1 | `GET {{baseUrl}}/api/locations/states` | 200; non-empty array; every item has `id`, `code` (2 upper-case letters), `name`; sorted by name; `code` and `name` unique |
| 2 | `GET {{baseUrl}}/api/locations/counties?stateId={{stateId}}` (`stateId` = an id from check 1) | 200; array; every item has `countyId`, `countyName`, `stateCode`, `stateName`; every `stateCode` equals the chosen state's code; sorted by `countyName` |
| 3 | `GET {{baseUrl}}/api/locations/counties` | 400; `message` = `stateId is required` |
| 4 | `GET {{baseUrl}}/api/locations/counties?stateId=abc` | 400; `message` = `stateId: invalid value` |
| 5 | `GET {{baseUrl}}/api/locations/counties?stateId=999999` | 404; `message` = `State not found` |
| 6 | `GET {{baseUrl}}/api/locations/counties?stateId={{emptyStateId}}` (a state with no counties, when one exists) | 200; `[]` |
| 7 | Repeat check 1 and 2 | identical ids, codes and names (stable across calls) |

Ownership: not applicable, the lists are shared reference data.

Check 6 needs a state without counties; the dev database currently only has states with
counties, so it is skipped unless such a state is seeded.

## Verification log

Run on 2026-10-26 against the dev database (RDS), app started locally with the `local`
profile on port 8081. Data: 1 state (`FL` / Florida, id 1) with 67 counties.

| # | Request | Result |
|---|---|---|
| 1 | `GET /api/locations/states` | 200, `[{"id":1,"code":"FL","name":"Florida"}]`; sorted; codes/names unique |
| 2 | `GET /api/locations/counties?stateId=1` | 200, 67 counties, every `stateCode` = `FL`, sorted by name, ids unique |
| 3 | `GET /api/locations/counties` | 400, `stateId is required` |
| 4 | `GET /api/locations/counties?stateId=abc` | 400, `stateId: invalid value` |
| 5 | `GET /api/locations/counties?stateId=999999` | 404, `State not found` |
| 6 | state without counties | skipped: every registered state has counties |
| 7 | `GET /api/locations/states` repeated | identical response (stable ids/codes/names) |

Conclusion: both routes meet the acceptance criteria; no code change was needed beyond Javadoc.
