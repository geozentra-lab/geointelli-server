# Realtor license verification, admin side — `/api/admin/realtor-license-verifications`

Ticket SCRUM-199 (parent SCRUM-198 "Submit and manually verify real estate agent licenses", epic
SCRUM-39 "Realtor Workspace"). The applicant side (submission, own status, resubmission) is SCRUM-200,
documented in `realtor-license-verification.md`. Both sides share the table
`realtor_license_verifications` (migration V37); this ticket adds no migration.

An admin opens the queue of pending submissions, checks the state registry by hand (for Florida, the
DBPR licensee search), and records the outcome: approve when the name matches and the license is
active, reject with a reason the applicant will read, or nothing when the check is inconclusive (the
row stays pending). A decision is recorded once per submission and stores the reviewer, the decision
time and the registry source.

## Access

All endpoints are under `/api/admin/**`, which the security configuration restricts to the Keycloak
realm role `admin`:

| Situation | Status |
|-----------|--------|
| no bearer token | 401 |
| token without the realm role `admin` | 403 |
| admin | as below |

The reviewer recorded on a decision is the admin behind the bearer token (`users.id` of that account,
created on first request like any user). No request names the reviewer.

## What a decision writes

Only the decision columns of the row change; the submitted details and the automatic-check columns are
never touched by this ticket.

| Column | Approve | Reject |
|--------|---------|--------|
| `status` | `VERIFIED` | `REJECTED` |
| `decision_method` | `MANUAL` | `MANUAL` |
| `reviewed_by_user_id` | the admin | the admin |
| `decided_at` | now (UTC, microseconds) | now |
| `registry_source` | from the request | from the request |
| `rejection_reason` | null | from the request, applicant-visible |

The V37 CHECK constraints (`ck_realtor_license_decision`) refuse any other combination, so a bug in a
future client cannot store a half-decided row. Two admins deciding the same submission at the same time
are serialised by a row lock (`SELECT ... FOR UPDATE`): the second one sees a decided row and gets 409.

## Endpoints

### `GET /api/admin/realtor-license-verifications` — list

Query parameters: `status` (optional: `PENDING_REVIEW`, `VERIFIED`, `REJECTED`; omitted = all),
`page` (default 0), `size` (default 20, 1 to 100). Newest first. Response is the project's page shape:

```json
{
  "content": [ { ...submission, see below... } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
}
```

400 for a negative page, a size out of range or an unknown status value.

### `GET /api/admin/realtor-license-verifications/{id}` — detail

One submission whatever its status. 404 when the ID is unknown.

### `POST /api/admin/realtor-license-verifications/{id}/approve`

Body:

```json
{ "registrySource": "Florida DBPR licensee search" }
```

| Status | When |
|--------|------|
| 200 | decided; body is the submission with status `VERIFIED` |
| 400 | `registrySource` missing or blank, or a `rejectionReason` was sent along |
| 404 | unknown ID |
| 409 | the submission is no longer `PENDING_REVIEW` (already approved or rejected) |

### `POST /api/admin/realtor-license-verifications/{id}/reject`

Body:

```json
{ "registrySource": "Florida DBPR licensee search", "rejectionReason": "Name does not match the registry" }
```

| Status | When |
|--------|------|
| 200 | decided; body is the submission with status `REJECTED` |
| 400 | `registrySource` missing or blank, or `rejectionReason` missing or blank (max 500 characters) |
| 404 | unknown ID |
| 409 | the submission is no longer `PENDING_REVIEW` |

After a rejection the applicant reads the reason on `GET /api/users/me/realtor-license` and may submit
again; the rejected row stays as history and appears in this list with its decision.

### Submission as the admin sees it

```json
{
  "id": 12,
  "applicantUserId": 7,
  "applicantEmail": "jane@example.test",
  "licenseHolderName": "Jane Doe",
  "licensingState": "FL",
  "licenseNumber": "SL3456789",
  "licenseExpirationDate": "2030-03-31",
  "brokerageName": "Sunshine Realty LLC",
  "workEmail": "jane@sunshine-realty.example",
  "status": "VERIFIED",
  "decisionMethod": "MANUAL",
  "reviewedByUserId": 3,
  "decidedAt": "2026-11-16T10:15:30.123456Z",
  "registrySource": "Florida DBPR licensee search",
  "rejectionReason": null,
  "autoCheckResult": null,
  "autoCheckedAt": null,
  "autoCheckSource": null,
  "autoCheckNote": null,
  "submittedAt": "2026-11-16T09:58:02.481920Z"
}
```

`applicantEmail` is the account e-mail of the applicant (null if the account has none), distinct from
the submitted `workEmail`. The `autoCheck*` fields are always null today; they are reserved for a future
automatic registry check and shown so an admin would see its finding when it exists.

## Review checklist for the admin

1. Open the pending list, newest first.
2. For one submission, search the registry (Florida: DBPR licensee search) by license number.
3. Name matches and license is active: approve, with the registry you used as `registrySource`.
4. Name does not match, license inactive or expired, number unknown: reject with a reason the
   applicant can act on ("Name does not match the registry", "License expired on ...").
5. Registry unavailable or result unclear: do nothing; the submission stays pending.

## Files

| Layer | File |
|-------|------|
| Repository | `repository/RealtorLicenseVerificationRepository` (+ `findAllByOrderByIdDesc`, `findByIdForUpdate`) |
| DTOs | `dto/RealtorLicenseReviewDTO` (admin view), `dto/RealtorLicenseDecisionRequest` (approve/reject body) |
| Service | `service/RealtorLicenseReviewService`, `service/impl/RealtorLicenseReviewServiceImpl` |
| Controller | `controller/RealtorLicenseReviewController` |
| Errors | reuses `exception/RealtorLicenseNotFoundException` (404) and the global handler (400, 409) |

## Manual verification (Postman)

Collection `docs/postman/SCRUM-199-realtor-license-review.postman_collection.json`. Variables: `baseUrl`,
`admin_token` (user with realm role admin), `user_a_token` and `user_b_token` (two applicants without the
role). Requests 03 and 11 use the SCRUM-200 endpoint to create the submissions the admin decides on, so
the collection is self-contained; run it on users A and B who have no pending or verified submission.

| # | Request | Expect |
|---|---------|--------|
| 01 | GET list without token | 401 |
| 02 | GET list as user A (no admin role) | 403 |
| 03 | POST user A submits a license (SCRUM-200) | 201, id saved as `submissionA` |
| 04 | GET list `status=PENDING_REVIEW` as admin | 200, contains `submissionA` with `applicantUserId` and `applicantEmail` |
| 05 | GET detail of `submissionA` | 200, status PENDING_REVIEW, decision fields null |
| 06 | POST approve without `registrySource` | 400 |
| 07 | POST reject without `rejectionReason` | 400 |
| 08 | POST approve `submissionA` | 200, VERIFIED, MANUAL, `reviewedByUserId` and `decidedAt` set |
| 09 | POST approve `submissionA` again | 409 |
| 10 | GET /api/users/me as user A | 200, `realtorLicenseStatus` = VERIFIED |
| 11 | POST user B submits a license (SCRUM-200) | 201, id saved as `submissionB` |
| 12 | POST reject `submissionB` with reason | 200, REJECTED, reason stored |
| 13 | GET /api/users/me/realtor-license as user B | 200, REJECTED with the same reason |
| 14 | POST user B submits again (SCRUM-200) | 201, new id |
| 15 | GET list `status=VERIFIED` as admin | 200, contains `submissionA`, not `submissionB` |
| 16 | GET detail of id 999999 | 404 |
