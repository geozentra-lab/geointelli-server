# Data-quality findings, review and resolution — `/api/admin/data-quality/issues/{reference}`

Ticket SCRUM-190 (parent SCRUM-43 "Review data quality, coverage and provider health", scope SCRUM-92).
The scanner that detects the findings is SCRUM-189 (`GET /api/admin/data-quality/issues`); this ticket
lets an admin record what was decided about a finding. Migration V41 adds the table
`data_quality_issue_reviews`.

A finding is never stored: the scanner recomputes it on each request and identifies it by the reference
`RULE:entityType:entityId`, for example `SALE_DUPLICATE:SALE:9012`. A review is attached to that reference.
Reviewing a finding never changes the property, sale or assessment it points to; correcting the data is a
separate, separately authorised action, after which the scanner simply stops reporting the finding.

## Access

All endpoints are under `/api/admin/**`, restricted by the security configuration to the Keycloak realm
role `admin`:

| Situation | Status |
|-----------|--------|
| no bearer token | 401 |
| token without the realm role `admin` | 403 |
| admin | as below |

The reviewer recorded on an action is the admin behind the bearer token (`users.id`). No request names the
reviewer.

## Review statuses and supported transitions

| Status | Meaning |
|--------|---------|
| `OPEN` | reported by the scanner, not decided; also a reopened finding. Never stored: a finding without rows is OPEN |
| `IN_REVIEW` | an admin is looking at it |
| `RESOLVED` | the data was corrected elsewhere; the finding should disappear from the scanner |
| `IGNORED` | reviewed and accepted as is (for example a false positive); the scanner keeps reporting it |

| From | Allowed to |
|------|-----------|
| `OPEN` | `IN_REVIEW`, `RESOLVED`, `IGNORED` |
| `IN_REVIEW` | `RESOLVED`, `IGNORED`, `OPEN` (released) |
| `RESOLVED` | `OPEN` (reopen) |
| `IGNORED` | `OPEN` (reopen) |

Anything else, including the same status again, is refused with 409. A `reason` is required for every move
except to `IN_REVIEW`.

## What a review writes

The log is append-only. Every action inserts one row in `data_quality_issue_reviews`; no row is ever updated
or deleted, so every reviewer, time and reason is kept. The current state of a finding is its newest row.

| Column | Value |
|--------|-------|
| `issue_reference`, `rule`, `entity_type`, `entity_id` | the finding, from the reference |
| `property_id` | property concerned, copied at review time (the property itself, or the sale's or assessment's) |
| `from_status`, `to_status` | the move |
| `reason` | from the request; null only when moving to `IN_REVIEW` |
| `reviewed_by_user_id` | the admin |
| `reviewed_at` | now (UTC, microseconds) |

CHECK constraints in V41 refuse a row whose reference disagrees with its parts, a row with the same from and
to status, and a resolve, ignore or reopen without reason. Two admins acting on the same finding at the same
time are serialised by an advisory lock on the reference, so the second one sees the first one's row and gets
409 if its move is no longer allowed.

## Endpoints

### Reference validation (both endpoints)

| Status | When |
|--------|------|
| 400 | not three parts, unknown rule, entity type that is not the rule's own, or a non-positive ID |
| 404 | the property, sale or assessment with that ID does not exist |

The scanner rule is not re-run: it would scan a large table on every click. Whether the finding is still
reported is read from the scanner list.

### `GET /api/admin/data-quality/issues/{reference}` — review state and history

200 with the finding's current status and every action, oldest first. A finding never reviewed is `OPEN`
with an empty history.

### `PATCH /api/admin/data-quality/issues/{reference}` — move to a review status

Body:

```json
{ "status": "RESOLVED", "reason": "Corrected in the November re-import" }
```

| Status | When |
|--------|------|
| 200 | recorded; body is the review state after the move, with history |
| 400 | `status` missing or unknown, `reason` missing when required or longer than 500 characters, bad reference |
| 404 | unknown entity |
| 409 | the move is not supported from the current status |

### Review state as returned

```json
{
  "reference": "PROPERTY_MISSING_ADDRESS:PROPERTY:345",
  "rule": "PROPERTY_MISSING_ADDRESS",
  "category": "MISSING",
  "entityType": "PROPERTY",
  "entityId": 345,
  "propertyId": 345,
  "status": "RESOLVED",
  "reviewedByUserId": 3,
  "reviewedAt": "2026-11-17T09:12:45.123456Z",
  "reason": "Corrected in the November re-import",
  "history": [
    { "id": 16, "fromStatus": "OPEN", "toStatus": "IN_REVIEW", "reason": null, "reviewedByUserId": 3, "reviewedAt": "2026-11-17T09:10:02.481920Z" },
    { "id": 17, "fromStatus": "IN_REVIEW", "toStatus": "RESOLVED", "reason": "Corrected in the November re-import", "reviewedByUserId": 3, "reviewedAt": "2026-11-17T09:12:45.123456Z" }
  ]
}
```

### Review state in the scanner list (SCRUM-189, extended)

Each issue of `GET /api/admin/data-quality/issues?rule=...` now carries `reviewStatus` (`OPEN` when never
reviewed), `reviewedByUserId`, `reviewedAt` and `reviewReason`, read for the whole page in one query. The
list itself is unchanged otherwise; it cannot filter by review status yet.

## Admin workflow

1. Open the scanner list for a rule; findings with `reviewStatus` other than `OPEN` were already handled.
2. Take a finding into review (`IN_REVIEW`), check the source data.
3. Real problem: have the data corrected by whoever is authorised, then mark `RESOLVED` with the reason.
   Not a problem: mark `IGNORED` with the reason. Not sure: release it to `OPEN`.
4. If a resolved or ignored finding turns out to be wrong later, reopen it (`OPEN`, with a reason) and start
   again. The earlier history stays visible.

## Files

| Layer | File |
|-------|------|
| Migration | `db/migration/V41__add_data_quality_issue_reviews.sql` |
| Enum | `enums/DataQualityReviewStatus` (statuses, transition table, reason rule) |
| Entity | `entity/DataQualityIssueReview` |
| Repository | `repository/DataQualityIssueReviewRepository`, `repository/DataQualityEntityRow` |
| DTOs | `dto/DataQualityReviewRequest`, `dto/DataQualityReviewEventDTO`, `dto/DataQualityIssueReviewDTO`; `dto/DataQualityIssueDTO` (+ review fields) |
| Service | `service/DataQualityReviewService`, `service/impl/DataQualityReviewServiceImpl`; `service/impl/DataQualityServiceImpl` (+ review fields on the list) |
| Controller | `controller/AdminDataQualityReviewController` |
| Errors | `exception/DataQualityEntityNotFoundException` (404) and the global handler (400, 409) |

## Manual verification (Postman)

Collection `docs/postman/SCRUM-190-data-quality-review.postman_collection.json`. Variables: `baseUrl`,
`admin_token` (user with realm role admin), `user_a_token` (a user without the role). Request 03 reads the
first `PROPERTY_MISSING_ADDRESS` finding from the scanner and uses its reference for the rest, so the
database must contain at least one property without an address that was never reviewed.

| # | Request | Expect |
|---|---------|--------|
| 01 | GET review state without token | 401 |
| 02 | GET review state as user A (no admin role) | 403 |
| 03 | GET scanner list `rule=PROPERTY_MISSING_ADDRESS&size=1` | 200, first finding saved as `ref`, has `reviewStatus` |
| 04 | GET `ref` | 200, OPEN, empty history |
| 05 | PATCH `FOO:PROPERTY:1` | 400 |
| 06 | PATCH `PROPERTY_MISSING_ADDRESS:SALE:1` | 400 (entity type is not the rule's) |
| 07 | PATCH `PROPERTY_MISSING_ADDRESS:PROPERTY:999999999` | 404 |
| 08 | PATCH `ref` RESOLVED without reason | 400 |
| 09 | PATCH `ref` IN_REVIEW | 200, IN_REVIEW, reviewer and time set, history 1 |
| 10 | PATCH `ref` IN_REVIEW again | 409 |
| 11 | PATCH `ref` RESOLVED with reason | 200, RESOLVED, history 2 |
| 12 | PATCH `ref` IGNORED | 409 (RESOLVED only reopens) |
| 13 | GET scanner list again | 200, first finding shows RESOLVED, reason and reviewer |
| 14 | PATCH `ref` OPEN with reason | 200, OPEN, history 3 |
| 15 | GET `ref` | 200, history OPEN > IN_REVIEW > RESOLVED > OPEN |
| 16 | PATCH `ref` as user A | 403 |
