# Realtor dashboard, tasks and pipeline card — `GET /api/dashboards/realtor/tasks-pipeline`

Ticket SCRUM-110 (parent SCRUM-86 "Deliver realtor dashboard"). Depends on SCRUM-79, which added
follow-up tasks (`/api/tasks`) and the property-deal pipeline (`/api/transactions`).

The card answers two questions for a realtor:

- **What do I have to do?** Task counts by status, the tasks that are already overdue, and the tasks
  due within a documented date range (default: the next 7 days).
- **Where are my deals?** Deal counts per pipeline stage.

The counts are the same numbers as `GET /api/tasks/summary` and `GET /api/transactions/pipeline`:
the card calls those two services instead of recomputing anything. Only the two bounded task lists
are new queries.

It is one tile of the realtor dashboard. SCRUM-112 will later compose it with the other tiles into
`GET /api/dashboards/realtor`; this endpoint stays available on its own.

## Request

`GET /api/dashboards/realtor/tasks-pipeline?limit=5&days=7`

| Parameter | Type | Default | Rule |
|-----------|------|---------|------|
| `limit` | integer | 5 | 1 to 20. Maximum number of items in **each** task list (`overdue`, `dueSoon`). |
| `days` | integer | 7 | 1 to 90. Length of the due-soon window, counted from now. |

Authentication: bearer token (Keycloak). The card is always built for the user identified by the
token; there is no way to ask for another user's card.

## Scope and ownership

**Scope is the current user.** Tasks and deals carry a `user_id`, the owner who created them. The
two SCRUM-79 services the card reuses already filter on the current user, and the two new list
queries filter on the same owner id, so the card can only contain the caller's own rows. `scope` in
the response is always `CURRENT_USER`.

**Persona is not checked.** The persona (`USER`, `RESIDENTIAL_INVESTOR`, `REALTOR`, `BANK_LENDER`)
only selects which dashboard the front end shows; the CRM endpoints are open to every persona, and
this card follows the same rule. A non-realtor simply gets zeros and empty lists.

**Organization scope.** The ticket mentions "owner/organization scope". The CRM has no organization
concept today (tasks and deals belong to one user only), so the card is owner-scoped.

## The date range

| Term | Definition |
|------|------------|
| now | the moment the card is built, in UTC, from the shared `crmWorkClock` bean (the same clock `/api/tasks/summary` uses), truncated to microseconds |
| window | from `now` (inclusive) to `now + days` (inclusive); returned as `tasks.window.from` / `tasks.window.to` |
| active task | status `OPEN` or `IN_PROGRESS` |
| overdue | active **and** `dueAt < now` |
| due soon | active **and** `now <= dueAt <= now + days` |

Rules:

- A task is either overdue or due soon or neither, never both, because the two ranges do not overlap.
- `COMPLETED` and `CANCELLED` tasks are counted in `summary` but never listed, even if their due date
  is in range.
- Tasks **without a due date** are counted in `summary` but never listed, because they have nothing to
  compare with the window. Use `GET /api/tasks?status=OPEN` to see them.
- Both lists are ordered by due date, earliest first, so the most urgent item is first.
- Both lists are bounded in the database (`LIMIT limit`). `tasks.dueSoonCount` is the unbounded number
  of due-soon tasks and `tasks.summary.overdue` the unbounded number of overdue tasks, so the UI can
  show "5 of 12".

## How the numbers are computed

| Field | Source | Meaning |
|-------|--------|---------|
| `tasks.summary.*` | `CrmTaskService.summary()` (= `GET /api/tasks/summary`) | total, open, inProgress, completed, cancelled, overdue, from all persisted tasks of the user |
| `tasks.dueSoonCount` | `COUNT(*)` of active tasks with `dueAt` in the window | due-soon tasks before applying `limit` |
| `tasks.overdue` | first `limit` active tasks with `dueAt < now`, by `dueAt` ascending | the list |
| `tasks.dueSoon` | first `limit` active tasks with `dueAt` in the window, by `dueAt` ascending | the list |
| `pipeline.byStage` | `CrmTransactionService.pipeline()` (= `GET /api/transactions/pipeline`) | one number per stage; all six stages present, 0 when empty |
| `pipeline.total` | same service | all persisted deals of the user (sum of `byStage`) |
| `pipeline.open` | `PROSPECT + VIEWING + OFFER + UNDER_CONTRACT` | deals still in progress; `CLOSED` and `CANCELLED` are terminal |

Each deal is stored in exactly one stage, so nothing is counted twice.

## Response

```
{
  "scope": "CURRENT_USER",
  "generatedAt": "2026-11-10T11:05:12.123456",
  "status": "OK",
  "error": null,
  "limit": 5,
  "tasks": {
    "summary": { "total", "open", "inProgress", "completed", "cancelled", "overdue" },
    "window": { "days": 7, "from": "<instant>", "to": "<instant>" },
    "dueSoonCount": <long>,
    "overdue": [ { "id", "title", "status", "dueAt", "clientId", "leadId", "propertyId", "transactionId" } ],
    "dueSoon":  [ { same shape } ]
  },
  "pipeline": {
    "total": <long>,
    "open": <long>,
    "byStage": { "PROSPECT": n, "VIEWING": n, "OFFER": n, "UNDER_CONTRACT": n, "CLOSED": n, "CANCELLED": n }
  }
}
```

Envelope fields, shared with the other dashboard cards:

| Field | Meaning |
|-------|---------|
| `scope` | always `CURRENT_USER` |
| `generatedAt` | server time when the card was built (freshness), local date-time string |
| `status` | `OK` when the data loaded; `ERROR` is reserved for the composed dashboard (SCRUM-112) |
| `error` | why the card failed; `null` when `status` is `OK` |
| `limit` | the limit that was applied |

Task item: `id`, `title`, `status` (always `OPEN` or `IN_PROGRESS` in these lists), `dueAt`, and
the linked `clientId`, `leadId`, `propertyId`, `transactionId` (each `null` when not linked).
Description is omitted. Detail: `GET /api/tasks/{id}`; deals: `GET /api/transactions/{id}`;
filtered lists: `GET /api/tasks?status=OPEN`, `GET /api/transactions?stage=OFFER`.

Two date formats appear on purpose: `generatedAt` is a `LocalDateTime` like every other card
(`2026-11-10T11:05:12.123456`), while `dueAt`, `window.from` and `window.to` are UTC instants, as
SCRUM-79 stores them (`2026-11-17T09:00:00Z`).

### Examples

Empty user (no tasks, no deals):

```json
{
  "scope": "CURRENT_USER",
  "generatedAt": "2026-11-10T11:05:12.123456",
  "status": "OK",
  "error": null,
  "limit": 5,
  "tasks": {
    "summary": { "total": 0, "open": 0, "inProgress": 0, "completed": 0, "cancelled": 0, "overdue": 0 },
    "window": { "days": 7, "from": "2026-11-10T11:05:12.123456Z", "to": "2026-11-17T11:05:12.123456Z" },
    "dueSoonCount": 0,
    "overdue": [],
    "dueSoon": []
  },
  "pipeline": {
    "total": 0,
    "open": 0,
    "byStage": { "PROSPECT": 0, "VIEWING": 0, "OFFER": 0, "UNDER_CONTRACT": 0, "CLOSED": 0, "CANCELLED": 0 }
  }
}
```

User with data (real response from the local verification, see below):

```json
{
  "scope": "CURRENT_USER",
  "generatedAt": "2026-11-10T16:41:26.6905319",
  "status": "OK",
  "error": null,
  "limit": 5,
  "tasks": {
    "summary": {
      "total": 5,
      "open": 4,
      "inProgress": 0,
      "completed": 1,
      "cancelled": 0,
      "overdue": 1
    },
    "window": {
      "days": 7,
      "from": "2026-11-10T16:41:25.431565Z",
      "to": "2026-11-17T16:41:25.431565Z"
    },
    "dueSoonCount": 1,
    "overdue": [
      {
        "id": 1,
        "title": "Call back Amina about the offer",
        "status": "OPEN",
        "dueAt": "2026-11-08T16:40:41Z",
        "clientId": null,
        "leadId": null,
        "propertyId": null,
        "transactionId": null
      }
    ],
    "dueSoon": [
      {
        "id": 2,
        "title": "Schedule a viewing",
        "status": "OPEN",
        "dueAt": "2026-11-13T16:40:42Z",
        "clientId": null,
        "leadId": null,
        "propertyId": null,
        "transactionId": null
      }
    ]
  },
  "pipeline": {
    "total": 3,
    "open": 2,
    "byStage": {
      "PROSPECT": 1,
      "VIEWING": 1,
      "OFFER": 0,
      "UNDER_CONTRACT": 0,
      "CLOSED": 0,
      "CANCELLED": 1
    }
  }
}
```

## Errors

| Case | Status | Body |
|------|--------|------|
| `limit` below 1 or above 20 | 400 | `{"status": 400, "error": "Bad Request", "message": "limit must be between 1 and 20, got 0", "timestamp": "..."}` |
| `days` below 1 or above 90 | 400 | `{"status": 400, "error": "Bad Request", "message": "days must be between 1 and 90, got 91", "timestamp": "..."}` |
| `limit` or `days` not a number | 400 | `{"status": 400, "error": "Bad Request", "message": "days: invalid value", "timestamp": "..."}` |
| no bearer token | 401 | `{"status": 401, "error": "401 UNAUTHORIZED", "message": "A bearer token is required to identify the current user", "timestamp": "..."}` |
| invalid or expired token | 401 | empty body |

Errors are produced by the shared handlers (`GlobalExceptionHandler`), so the body is the project's
standard `ApiError` shape. This card adds no handler of its own. An unknown user with a valid token
is created on first call, like everywhere else, and gets the empty card.

## Verifying locally

Checked manually with Postman; see
[`docs/postman/SCRUM-110-realtor-tasks-pipeline.postman_collection.json`](postman/SCRUM-110-realtor-tasks-pipeline.postman_collection.json).
The collection needs a Keycloak token for two users (`tokenA`, the realtor; `tokenB`, another user),
the `baseUrl` of the running service, an owned `clientId` of user A and an existing `propertyId`.

What the collection checks, in order:

1. **Empty card** for user A: zeros, six stage keys all `0`, empty lists, window of 7 days.
2. **Create test data as user A** through the existing SCRUM-79 endpoints:
   - `POST /api/tasks` five times: due 2 days ago (overdue), due in 3 days (due soon), due in 30
     days (outside the 7-day window), no due date, and one more due in 1 day that is then
     `PATCH`ed to `COMPLETED`;
   - `POST /api/transactions` three times (clientId, propertyId, title), then `PATCH` one to
     `VIEWING` and one to `CANCELLED`.
3. **Card for user A**: `summary` total 5 / open 4 / completed 1 / overdue 1; `dueSoonCount` 1;
   `overdue` has the 2-days-ago task; `dueSoon` has the 3-days task only; the 30-day, no-date and
   completed tasks are listed nowhere; `pipeline` total 3, open 2, PROSPECT 1 / VIEWING 1 /
   CANCELLED 1.
4. **Date range**: `days=30` moves the 30-day task into `dueSoon` (`dueSoonCount` 2, ordered by due
   date); `days=90` and `days=1` accepted; `days=0`, `days=91`, `days=abc` return 400.
5. **Limits**: `limit=1` caps each list at one item; `limit=20` accepted; `limit=0`, `limit=21`
   return 400.
6. **Ownership**: user B calls the card and gets the empty card, not A's data; B reading A's task by
   id gets 404.
7. **Missing identity**: no token gives 401 with the message above; a bad token gives 401.
8. **Cleanup**: tasks can be deleted (`DELETE /api/tasks/{id}`), deals cannot, so the three test
   deals stay in the local database.

## Implementation

| File | Role |
|------|------|
| `controller/RealtorDashboardTasksPipelineController.java` | `GET /api/dashboards/realtor/tasks-pipeline`; one class per card |
| `service/RealtorDashboardTasksPipelineService.java` | interface, `DEFAULT_LIMIT = 5`, `MAX_LIMIT = 20`, `DEFAULT_DAYS = 7`, `MAX_DAYS = 90` |
| `service/impl/RealtorDashboardTasksPipelineServiceImpl.java` | validates the parameters, reuses `CrmTaskService.summary()` and `CrmTransactionService.pipeline()`, runs the three owner-scoped list/count queries with the `crmWorkClock` time |
| `dto/RealtorTasksPipelineCardDTO.java` | envelope plus `TasksSection`, `TaskSummary`, `DueWindow`, `TaskItem`, `PipelineSection` |
| `repository/CrmTaskRepository.java` | added `findByUserIdAndStatusInAndDueAtBeforeOrderByDueAtAsc`, `findByUserIdAndStatusInAndDueAtBetweenOrderByDueAtAsc`, `countByUserIdAndStatusInAndDueAtBetween` |

No new tables, migrations or automated tests (per ticket). The SCRUM-79 services are not modified.
