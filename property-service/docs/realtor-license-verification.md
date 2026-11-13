# Realtor license verification, applicant side — `/api/users/me/realtor-license`

Ticket SCRUM-200 (parent SCRUM-198 "Submit and manually verify real estate agent licenses", epic
SCRUM-39 "Realtor Workspace"). The admin side (list pending, approve, reject) is SCRUM-199 and is
not part of this document; it builds on the same table.

A signed-in user who works as a real estate agent submits the details of their license. GeoZentra
checks the state registry (for Florida, the DBPR licensee search) and records a decision. The user
can read their status at any time and, after a rejection, submit corrected details as a new record.

The feature is independent of the persona: `PATCH /api/users/me` with `persona: REALTOR` selects
the dashboard; the license submission is a separate record, and a user may submit before or after
choosing the persona. Nothing is unlocked by a verified license yet (permission configuration is
deferred by the epic).

## Data model

Table `realtor_license_verifications` (migration V37), one row per submission.

| Column group | Columns | Written by |
|--------------|---------|------------|
| Submission | `user_id`, `license_holder_name`, `licensing_state`, `license_number`, `license_expiration_date`, `brokerage_name`, `work_email`, `submitted_at` | this ticket, fixed at creation |
| Automatic check | `auto_check_result` (MATCH, NO_MATCH, INCONCLUSIVE), `auto_checked_at`, `auto_check_source`, `auto_check_note` | a future registry integration; informative only |
| Decision | `status` (PENDING_REVIEW, VERIFIED, REJECTED), `decision_method` (MANUAL, AUTOMATIC), `reviewed_by_user_id`, `decided_at`, `registry_source`, `rejection_reason` | SCRUM-199 (MANUAL) or a future automatic check (AUTOMATIC) |

Database rules:

- A pending row has no decision columns. A decided row has `decision_method`, `decided_at` and
  `registry_source`; a MANUAL decision also has the reviewer; an AUTOMATIC decision also has an
  automatic check result; a REJECTED row has a non-empty `rejection_reason`.
- A user has at most one PENDING_REVIEW row (partial unique index), so two concurrent submissions
  cannot both succeed.
- Rows are never deleted or edited by the applicant. A resubmission is a new row; the rejected row
  stays as history.

The three verification modes the table supports, chosen later by policy in the service that decides:
manual only (today), automatic only, or both (the automatic check records its finding, the row stays
pending, an admin decides with that finding in view).

## Endpoints

All three require a bearer token (Keycloak). The owner is always the user identified by the token;
no request names a user, and a user cannot read or create anyone else's submission. A request without
a token gets 401.

### `POST /api/users/me/realtor-license` — submit

Body:

| Field | Type | Rule |
|-------|------|------|
| `licenseHolderName` | string | required, 1 to 160 characters, trimmed |
| `licensingState` | string | required, two letters, upper-cased; only `FL` is accepted today |
| `licenseNumber` | string | required, 1 to 40 characters, trimmed; no format check (the registry check decides) |
| `licenseExpirationDate` | date `yyyy-MM-dd` | optional; must not be before today (UTC) |
| `brokerageName` | string | optional, max 160; blank becomes null |
| `workEmail` | string | optional, valid e-mail, max 254; blank becomes null |

Responses:

| Status | When |
|--------|------|
| 201 | created; body is the submission (see below), `Location: /api/users/me/realtor-license` |
| 400 | missing or invalid field, unsupported state, expiration date in the past, unreadable JSON |
| 401 | no bearer token |
| 409 | the user already has a PENDING_REVIEW or VERIFIED submission (also when two submissions race) |

Example:

```json
{
  "licenseHolderName": "Jane Doe",
  "licensingState": "FL",
  "licenseNumber": "SL3456789",
  "licenseExpirationDate": "2027-03-31",
  "brokerageName": "Sunshine Realty LLC",
  "workEmail": "jane@sunshine-realty.example"
}
```

### `GET /api/users/me/realtor-license` — my latest submission

Returns the newest submission whatever its status. 404 when the user never submitted.

### `GET /api/users/me/realtor-license/history` — all my submissions

Newest first, including rejected ones. Empty array when the user never submitted.

### Submission response

```json
{
  "id": 12,
  "licenseHolderName": "Jane Doe",
  "licensingState": "FL",
  "licenseNumber": "SL3456789",
  "licenseExpirationDate": "2027-03-31",
  "brokerageName": "Sunshine Realty LLC",
  "workEmail": "jane@sunshine-realty.example",
  "status": "PENDING_REVIEW",
  "rejectionReason": null,
  "submittedAt": "2026-11-13T10:15:30.123456Z",
  "decidedAt": null
}
```

`rejectionReason` is filled only when `status` is `REJECTED`. The reviewer, the registry source and
the automatic check are never exposed to the applicant.

### Profile: `GET /api/users/me` and `PATCH /api/users/me`

The profile response gains `realtorLicenseStatus`: `PENDING_REVIEW`, `VERIFIED`, `REJECTED` or `null`
when the user never submitted. It is the status of the newest submission. `persona` is unchanged and
independent.

## Status lifecycle

```
POST ──> PENDING_REVIEW ──(SCRUM-199 approve / automatic MATCH)──> VERIFIED
              │
              └──(SCRUM-199 reject with reason / automatic NO_MATCH)──> REJECTED ──(new POST)──> PENDING_REVIEW (new row)
```

An inconclusive check leaves the row PENDING_REVIEW. A VERIFIED row blocks new submissions; when a
license expires, a future job may require a new submission (the expiration date is stored for that).

## Files

| Layer | File |
|-------|------|
| Migration | `db/migration/V37__add_realtor_license_verifications.sql` |
| Entity | `entity/RealtorLicenseVerification`, enums `RealtorLicenseVerificationStatus`, `RealtorLicenseDecisionMethod`, `RealtorLicenseAutoCheckResult` |
| Repository | `repository/RealtorLicenseVerificationRepository` (owner queries plus a by-status page for SCRUM-199) |
| DTOs | `dto/RealtorLicenseSubmissionRequest`, `dto/RealtorLicenseVerificationDTO`; `dto/UserDTO.realtorLicenseStatus` |
| Service | `service/RealtorLicenseVerificationService`, `service/impl/RealtorLicenseVerificationServiceImpl` |
| Controller | `controller/RealtorLicenseVerificationController`; `controller/UserController` fills the profile field |
| Errors | `exception/RealtorLicenseNotFoundException` (404), `advice/RealtorLicenseExceptionHandler` (race → 409) |

## Manual verification (Postman)

Collection `docs/postman/SCRUM-200-realtor-license.postman_collection.json`. Variables: `baseUrl`,
`access_token` (user A), `access_token_b` (a second user, for ownership). The collection does not
need the SCRUM-199 endpoints: the "after rejection" case is prepared with one SQL statement, shown in
the request description, that rejects user A's pending row the way the admin endpoint will.

| # | Request | Expect |
|---|---------|--------|
| 01 | GET latest before any submission | 404 |
| 02 | GET history before any submission | 200, `[]` |
| 03 | POST valid submission | 201, status PENDING_REVIEW, trimmed values, `decidedAt` null |
| 04 | GET latest | 200, same id as 03 |
| 05 | GET /api/users/me | 200, `realtorLicenseStatus` = PENDING_REVIEW, `persona` unchanged |
| 06 | POST again while pending | 409 |
| 07 | POST missing required fields | 400, message names the fields |
| 08 | POST state `NY` | 400 |
| 09 | POST expiration date in the past | 400 |
| 10 | POST invalid e-mail | 400 |
| 11 | POST without token | 401 |
| 12 | GET latest as user B | 404 (B does not see A's submission) |
| 13 | GET history as user B | 200, `[]` |
| 14 | (after the SQL rejection) GET latest | 200, status REJECTED, `rejectionReason` filled |
| 15 | POST corrected submission after rejection | 201, new id, PENDING_REVIEW |
| 16 | GET history | 200, two rows, newest first, the old one still REJECTED |
