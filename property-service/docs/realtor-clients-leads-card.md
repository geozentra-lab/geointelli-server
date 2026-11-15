# Realtor dashboard, clients and leads card — `GET /api/dashboards/realtor/clients-leads`

Ticket SCRUM-108 (parent SCRUM-86 "Deliver realtor dashboard"). Depends on SCRUM-78, which added the
CRM: contacts (`/api/clients`) and leads (`/api/leads`).

The card gives a realtor a one-glance summary of their CRM:

- how many contacts they have, and the newest ones;
- how many leads they have, split by the stage stored on each lead, and the newest ones.

It is one tile of the realtor dashboard. SCRUM-112 will later compose this card with the other tiles
into `GET /api/dashboards/realtor`; this endpoint stays available on its own so the card can be
refreshed independently.

## Request

`GET /api/dashboards/realtor/clients-leads?limit=5`

| Parameter | Type | Default | Rule |
|-----------|------|---------|------|
| `limit` | integer | 5 | 1 to 20. Maximum number of items in **each** recent list (clients and leads). |

Authentication: bearer token (Keycloak). The card is always built for the user identified by the
token; there is no way to ask for another user's card.

## Scope and ownership

**Scope is the current user.** Every CRM row has a `user_id`, the owner who created it. All four
queries behind this card filter on that owner, so the card can only ever contain the caller's own
contacts and leads. `scope` in the response is always `CURRENT_USER`.

**Persona is not checked.** A user chooses a persona (`USER`, `RESIDENTIAL_INVESTOR`, `REALTOR`,
`BANK_LENDER`) through their profile, and the persona only selects which dashboard the front end
shows. The CRM endpoints of SCRUM-78 are open to every persona, so this card follows the same rule:
a non-realtor who calls it is not refused, they simply get zeros and empty lists (or their own CRM
data, if they created some).

**Organization scope.** The ticket mentions "owner/organization scope". The CRM has no organization
concept today (contacts and leads belong to one user only), so the card is owner-scoped. If the CRM
gains organizations later, this card should follow the CRM's rule rather than invent its own.

## How the numbers are computed

All numbers come straight from the stored rows. Nothing is recomputed, cached or estimated.

| Field | Query | Meaning |
|-------|-------|---------|
| `clients.total` | `COUNT(*) FROM crm_clients WHERE user_id = me` | all contacts of the user |
| `clients.recent` | newest `limit` contacts by `created_at` | contacts, newest first |
| `leads.byStage` | `COUNT(*) ... GROUP BY stage` | one number per stored stage |
| `leads.total` | sum of `byStage` | all leads of the user |
| `leads.open` | `NEW + CONTACTED + QUALIFIED` | leads still in progress |
| `leads.recent` | newest `limit` leads by `created_at`, with their client | leads, newest first |

Rules:

- `byStage` always contains the five stages `NEW`, `CONTACTED`, `QUALIFIED`, `CONVERTED`, `LOST`, in
  that order, with `0` for stages that have no lead. The database only returns stages that have at
  least one row; the service fills the missing ones with zero.
- Each lead is stored in exactly one stage, so `total` equals the sum of `byStage` and no lead is
  counted twice. `CONVERTED` and `LOST` are terminal; they are counted but not "open".
- "Recent" means **recently created**. Editing a contact or advancing a lead's stage does not move
  it up the list; the lead's current stage is shown, though.
- Both recent lists are bounded in the database (`LIMIT`), not in Java.

## Response

```
{
  "scope": "CURRENT_USER",
  "generatedAt": "2026-11-10T10:15:30.123456",
  "status": "OK",
  "error": null,
  "limit": 5,
  "clients": {
    "total": <long>,
    "recent": [ { "id", "name", "email", "phone", "createdAt" } ]
  },
  "leads": {
    "total": <long>,
    "open": <long>,
    "byStage": { "NEW": n, "CONTACTED": n, "QUALIFIED": n, "CONVERTED": n, "LOST": n },
    "recent": [ { "id", "clientId", "clientName", "propertyId", "title", "source", "stage", "createdAt" } ]
  }
}
```

Envelope fields, shared with the other dashboard cards:

| Field | Meaning |
|-------|---------|
| `scope` | always `CURRENT_USER` |
| `generatedAt` | server time when the card was built (freshness), ISO-8601 string |
| `status` | `OK` when the data loaded; `ERROR` is reserved for the composed dashboard (SCRUM-112), which may show a failed card instead of failing the whole page |
| `error` | why the card failed; `null` when `status` is `OK` |
| `limit` | the limit that was applied |

Recent client item: `id`, `name`, `email` (normalized, or `null`), `phone` (or `null`), `createdAt`.
Contact notes are deliberately omitted from the card. Detail: `GET /api/clients/{id}`.

Recent lead item: `id`, `clientId`, `clientName` (for display, so the UI needs no second call),
`propertyId` (or `null`), `title`, `source`, `stage`, `createdAt`. Detail: `GET /api/leads/{id}`,
and `GET /api/clients/{clientId}` for the contact.

Dates are ISO-8601 strings such as `2026-11-10T10:15:30.123456` (the project serializes
`LocalDateTime` as strings).

### Examples

Empty user (no CRM data yet):

```json
{
  "scope": "CURRENT_USER",
  "generatedAt": "2026-11-10T10:15:30.123456",
  "status": "OK",
  "error": null,
  "limit": 5,
  "clients": { "total": 0, "recent": [] },
  "leads": {
    "total": 0,
    "open": 0,
    "byStage": { "NEW": 0, "CONTACTED": 0, "QUALIFIED": 0, "CONVERTED": 0, "LOST": 0 },
    "recent": []
  }
}
```

User with data (real response from the local verification, see below):

```json
{
  "scope": "CURRENT_USER",
  "generatedAt": "2026-11-10T10:14:19.8723797",
  "status": "OK",
  "error": null,
  "limit": 5,
  "clients": {
    "total": 2,
    "recent": [
      {
        "id": 2,
        "name": "Youssef Idrissi",
        "email": "youssef.idrissi@example.test",
        "phone": "+1 305 555 0102",
        "createdAt": "2026-11-10T10:13:34.965275"
      },
      {
        "id": 1,
        "name": "Amina Benali",
        "email": "amina.benali@example.test",
        "phone": "+1 305 555 0101",
        "createdAt": "2026-11-10T10:13:34.199067"
      }
    ]
  },
  "leads": {
    "total": 3,
    "open": 2,
    "byStage": {
      "NEW": 1,
      "CONTACTED": 1,
      "QUALIFIED": 0,
      "CONVERTED": 0,
      "LOST": 1
    },
    "recent": [
      {
        "id": 3,
        "clientId": 2,
        "clientName": "Youssef Idrissi",
        "propertyId": null,
        "title": "Investor asking about duplexes",
        "source": "REFERRAL",
        "stage": "NEW",
        "createdAt": "2026-11-10T10:13:38.104105"
      },
      {
        "id": 2,
        "clientId": 1,
        "clientName": "Amina Benali",
        "propertyId": null,
        "title": "Wants a valuation of her condo",
        "source": "PHONE",
        "stage": "LOST",
        "createdAt": "2026-11-10T10:13:37.130341"
      },
      {
        "id": 1,
        "clientId": 1,
        "clientName": "Amina Benali",
        "propertyId": 4,
        "title": "Looking for a 3-bed in Miami",
        "source": "WEBSITE",
        "stage": "CONTACTED",
        "createdAt": "2026-11-10T10:13:36.152438"
      }
    ]
  }
}
```

## Errors

| Case | Status | Body |
|------|--------|------|
| `limit` below 1 or above 20 | 400 | `{"status": 400, "error": "Bad Request", "message": "limit must be between 1 and 20, got 0", "timestamp": "..."}` |
| `limit` not a number (`limit=abc`) | 400 | `{"status": 400, "error": "Bad Request", "message": "limit: invalid value", "timestamp": "..."}` |
| no bearer token | 401 | `{"status": 401, "error": "401 UNAUTHORIZED", "message": "A bearer token is required to identify the current user", "timestamp": "..."}` |
| invalid or expired token | 401 | empty body |

Errors are produced by the shared handlers (`GlobalExceptionHandler`), so the body is the project's
standard `ApiError` shape: `status`, `error` (the HTTP reason), `message` (the explanation to show) and
`timestamp`. This card adds no handler of its own. An unknown user with a valid token is created on first call, like everywhere else, and gets
the empty card.

## Verifying locally

Checked manually with Postman; see
[`docs/postman/SCRUM-108-realtor-clients-leads.postman_collection.json`](postman/SCRUM-108-realtor-clients-leads.postman_collection.json).
The collection needs a Keycloak token for two users (`tokenA`, the realtor; `tokenB`, another user)
and the `baseUrl` of the running service.

What the collection checks, in order:

1. **Empty card** for user A: zeros, five stage keys all `0`, empty lists.
2. **Create test data as user A** through the existing CRM endpoints:
   - `POST /api/clients` twice (name, email, phone);
   - `POST /api/leads` three times with an `Idempotency-Key` header (clientId, title, source,
     optional propertyId);
   - `PATCH /api/leads/{id}` once to move a lead to `CONTACTED`, and once more to `LOST`.
3. **Card for user A**: `clients.total` 2, `leads.total` 3, `open` 2, `byStage` NEW 1 /
   CONTACTED 1 / LOST 1 / others 0, recent lists newest first, `clientName` present, `limit` 5.
4. **Limits**: `limit=1` returns one item per list, `limit=20` returns all, `limit=0`, `limit=21`
   and `limit=abc` return 400.
5. **Ownership**: user B calls the card and gets the empty card, not A's data.
6. **Missing identity**: no token gives 401 with the message above; a bad token gives 401.
7. **Cleanup**: leads cannot be deleted through the API and a contact with leads cannot be deleted
   (409), so the test rows stay in the local database. Use fresh users or accept the leftover rows.

## Implementation

| File | Role |
|------|------|
| `controller/RealtorDashboardClientsLeadsController.java` | `GET /api/dashboards/realtor/clients-leads`; one class per card so sibling cards and the composed endpoint do not collide |
| `service/RealtorDashboardClientsLeadsService.java` | interface, `DEFAULT_LIMIT = 5`, `MAX_LIMIT = 20` |
| `service/impl/RealtorDashboardClientsLeadsServiceImpl.java` | validates `limit`, resolves the current user, runs the four owner-scoped queries, fills the stage map |
| `dto/RealtorClientsLeadsCardDTO.java` | envelope plus `ClientsSection`, `ClientItem`, `LeadsSection`, `LeadItem` |
| `repository/CrmClientRepository.java` | added `countByUser_Id`, `findByUser_IdOrderByCreatedAtDesc` |
| `repository/CrmLeadRepository.java` | added `countByStage` (JPQL `GROUP BY stage`, projection `LeadStageCount`) and `findByUser_IdOrderByCreatedAtDesc` with `@EntityGraph("client")` |

No new tables, migrations or automated tests (per ticket). The CRM services of SCRUM-78 are not
modified; the card reads through the same repositories they use.
