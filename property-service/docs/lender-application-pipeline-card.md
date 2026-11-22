# Lender dashboard: application pipeline card — `GET /api/dashboards/lender/application-pipeline`

Story SCRUM-113, part of the lender dashboard (SCRUM-87). Depends on the organization-scoped lender applications of
SCRUM-82.

## What it answers

"How many of my organization's applications are in each stage right now?" The card counts the recorded applications
of one organization by their current stage. It is read-only and only reads stored applications.

## Request

| Parameter | Required | Meaning |
|---|---|---|
| `organizationId` | yes | organization whose applications are counted; the current user must belong to it |

A bearer token is required.

## Reporting period

The card is a **snapshot of the current stages**, taken at `generatedAt` (`reportingPeriod.type: CURRENT_STATUS`,
`reportingPeriod.asOf` = `generatedAt`, UTC). It is not filtered by date:

- every recorded application of the organization is counted, whatever its creation date;
- each application is counted **once**, in the stage it is in now. An application that went from `SUBMITTED` to
  `APPROVED` is counted only in `APPROVED`;
- the counts are not movements over a period (for approvals and fundings over a period, see the approved-funded card,
  `GET /api/dashboards/lender/approved-funded`).

## Stages

The supported stages are those of the SCRUM-82 applications (`LenderApplicationStage`), always listed in this order,
with 0 when no application is in them: `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `FUNDED`, `REJECTED`,
`WITHDRAWN`.

The counts come from one database aggregation (the SCRUM-82 pipeline query, `GROUP BY stage`); they do not depend on
the pagination of the application list. `totalApplications` is the sum of the stages.

## Response

```
{
  "organizationId": 42,
  "status": "OK",
  "error": null,
  "generatedAt": "2026-11-19T10:00:00Z",
  "reportingPeriod": {
    "type": "CURRENT_STATUS",
    "asOf": "2026-11-19T10:00:00Z",
    "description": "Snapshot of every recorded application by its current stage at asOf; not filtered by date"
  },
  "totalApplications": 9,
  "stages": [
    { "stage": "DRAFT", "count": 2 },
    { "stage": "SUBMITTED", "count": 3 },
    { "stage": "UNDER_REVIEW", "count": 1 },
    { "stage": "APPROVED", "count": 1 },
    { "stage": "FUNDED", "count": 1 },
    { "stage": "REJECTED", "count": 0 },
    { "stage": "WITHDRAWN", "count": 1 }
  ],
  "note": "Recorded applications of the organization counted by their current stage, as of generatedAt. ..."
}
```

| Field | Meaning |
|---|---|
| `status` | `OK` with at least one application, `EMPTY` without any (200, every count 0), `ERROR` only in the composed dashboard (SCRUM-117) when the card could not load |
| `error` | why the card failed; `null` unless `status` is `ERROR` |

## Access and errors

Only members of the organization (owner included) can read the card. A foreign organization returns the same 404 as
a missing one, so the card does not reveal which organizations exist.

| Response | When |
|---|---|
| 200 | the user belongs to the organization (`OK` or `EMPTY`) |
| 400 | `organizationId` is missing, not a number, 0 or negative |
| 401 | no bearer token |
| 404 | the organization does not exist or the user is not a member |

Errors use the shared `ApiError` body:

```
{ "status": 400, "error": "Bad Request", "message": "organizationId must be positive", "timestamp": "..." }
```

## Manual checks

Postman collection: `docs/postman/SCRUM-113-lender-application-pipeline.postman_collection.json`. Run the folders in
order with the Collection Runner. It needs a running service, a Keycloak realm and two users: A, who creates a new
organization and its applications, and B, who is not a member. Each run creates a new organization, so it can be
repeated.
