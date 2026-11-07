# Ingestion jobs — CSV imports and Miami-Dade ingestion (SCRUM-188)

Every CSV import and every Miami-Dade API ingestion now runs as a persisted background job. The start
endpoint validates the request, stores the uploaded files, inserts a `QUEUED` job and returns `202 Accepted`
with the job and a `Location` header. Any application instance may pick the job up; progress, errors and
retries are visible to administrators next to the Mapillary backfill jobs.

Authentication: `/api/ingestion/*` keeps the access it had before (no role required). The admin endpoints
under `/api/admin/import-jobs` need the Keycloak realm role `admin`.

## Start a job

| Endpoint | What it does | Inputs |
|---|---|---|
| `POST /api/ingestion/import/{dataset}` | One CSV file; `dataset` is `properties`, `addresses`, `assessments`, `buildings`, `lands`, `sales`, `taxes`, `extra-features`, `parcels`, `property-value-predictions` or `property-images` | `file` (multipart) or `stagedFile`; `county`, `sourceState` |
| `POST /api/ingestion/import/owners` | `owners.csv` and `property_owner.csv` in one step | `ownersFile` + `propertyOwnersFile`, or `stagedOwnersFile` + `stagedPropertyOwnersFile`; `county`, `sourceState` |
| `POST /api/ingestion/import/folder` | A whole county folder, in the usual order | `files` (several multipart parts named as the county files) or `stagedFolder`; `county`, `sourceState` |
| `POST /api/ingestion/run` | Properties for parcel folios that have none | — |
| `POST /api/ingestion/ingestbuildings`, `ingest_extrafeatures`, `ingest_sales`, `ingestaddresses` | The dataset for properties that lack it | — |
| `GET /api/ingestion/jobs/{id}` | The job with its first 1000 failed or skipped records | — |

Server file paths are no longer accepted. A file is either uploaded, or named relative to the directory in
`INGESTION_STAGING_DIRECTORY` (empty by default, which disables staged inputs). Names that leave that
directory are rejected.

Rejected before anything is stored (`400`):

- missing or blank `county`/`sourceState`, or an unknown state;
- a county that does not exist, unless the dataset is `properties` or `folder`, which create it as before;
- an empty file, a file that is not CSV, or a header missing a column the importer reads;
- a folder without one of `properties`, `addresses`, `assessments`, `buildings`, `owners`, `property_owner`,
  `lands`, `sales`, `taxes`, `extra_features`; `parcels`, `property_value_predictions` and `property_images`
  are optional;
- an unknown `dataset`.

A Miami-Dade endpoint answers `409 DATASET_JOB_ACTIVE` while a job for the same dataset is queued or running.

## The job

```json
{
  "id": "4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55",
  "jobType": "CSV_IMPORT",
  "dataset": "ADDRESSES",
  "countyId": 1, "sourceCounty": "Miami-Dade", "sourceState": "FL",
  "status": "RUNNING",
  "total": 582576, "processed": 12000, "completed": 12000, "skipped": 0, "failed": 0,
  "currentStep": "addresses.csv",
  "errorCode": null, "errorMessage": null,
  "inputStorage": "S3", "attempt": 1, "retryOfJobId": null,
  "retryable": false, "retryBlockedReason": "JOB_NOT_FINISHED",
  "createdAt": "2026-11-07T09:00:00Z", "startedAt": "2026-11-07T09:00:04Z", "finishedAt": null,
  "items": [ { "itemKey": "addresses.csv:1234", "status": "FAILED", "reason": "INVALID_ROW", "message": "..." } ]
}
```

Statuses are the ones the Mapillary backfill uses: `QUEUED`, `RUNNING`, `COMPLETED`, `PARTIAL` (finished,
some records failed), `FAILED`, `INTERRUPTED` (the worker lost its lease, was shut down, or a job with local
inputs was never claimed).

Counters: `total` is the number of records the job handles — CSV rows, counted file by file as each file
starts, or folios selected at start. `processed = completed + skipped + failed`. `completed` records were
written; `skipped` were intentionally not written (a folio whose property already exists, an image the
property already has, a broken image URL); `failed` records errored. `items` keeps the first 1000 failed
and skipped records with a reason code and a short message without server paths.

## How a job runs

- A poller on every instance (`ingestion.jobs.poll-delay-millis`, 5 s) claims the oldest `QUEUED` job with an
  atomic update; only one instance wins. One job runs at a time per instance.
- The worker renews a 10-minute lease every 30 s. Every progress write carries
  `WHERE worker_id = me AND lease_expires_at > now()`; if it updates nothing, the worker stops.
- CSV rows commit in batches of `ingestion.jobs.csv-batch-size` (500). The batch's rows and the job's
  checkpoint are written in the **same transaction**, so a worker that lost its lease cannot commit data,
  and there is no window between saving data and recording progress.
- Image rows (`property_images.csv`) download the image before the transaction and save it inside it.
  Miami-Dade folios are fetched outside any transaction and written in a short one afterwards.
- `owners.csv` + `property_owner.csv` load in one transaction, as the previous import did: owner rows carry
  no source ID that the second file could be matched against later.
- A job whose lease expired is marked `INTERRUPTED` (`WORKER_LEASE_EXPIRED`) by the next poll or by the
  admin listing. A job with local inputs that nobody claimed within `ingestion.jobs.queue-deadline-seconds`
  becomes `INTERRUPTED` (`QUEUE_DEADLINE_EXPIRED`).
- When a job `COMPLETED`, its stored inputs are deleted. The inputs of failed, partial and interrupted jobs
  stay for a retry.

## What changed in failure behaviour

A failing CSV file no longer rolls back the whole file. Batches before the failing one stay committed; the
job is `FAILED` with `errorCode` naming the cause, `errorMessage` naming `file:row`, and an item for the row.
That is what makes a job resumable without holding a transaction open for a 500,000-row file.

Existing properties are never overwritten. A `properties.csv` row whose (county, folio) already exists fails
its batch with `DUPLICATE_FOLIO`, which is the unique constraint the import always had, now reported. Related
files (`addresses.csv`, `sales.csv`, …) append rows, as before; importing the same file twice appends twice.
Parent–child links and county/state mapping use the unchanged importer code.

Error codes: `INVALID_ROW`, `MISSING_PROPERTY_MAPPING`, `DUPLICATE_FOLIO`, `DATABASE_CONSTRAINT`,
`DATABASE_ERROR`, `INPUT_UNREADABLE`, `BROKEN_IMAGE` (skipped), `DUPLICATE_IMAGE` (skipped),
`SOURCE_API_UNAVAILABLE`, `SOURCE_API_CLIENT_ERROR`, `SOURCE_API_RESPONSE_INVALID`, `INGESTION_ERROR`,
`WORKER_LEASE_EXPIRED`, `WORKER_LEASE_LOST`, `WORKER_STOPPED`, `WORKER_ERROR`, `QUEUE_DEADLINE_EXPIRED`.

## Retries — `POST /api/admin/import-jobs/{id}/retry`

A retry creates a **new** job (`retryOfJobId` points back, `attempt` increases) so history is never
rewritten. The old job must be `FAILED`, `PARTIAL` or `INTERRUPTED`.

| Job | What the retry does |
|---|---|
| CSV single file | Resumes after the last committed batch of the same stored file. `total`, `completed` and `skipped` continue; `failed` restarts at 0. |
| CSV folder | Resumes at the current file and batch. |
| Owners (alone or inside a folder) | Reruns the owners step, which rolled back entirely. |
| Miami-Dade | Re-selects what is still missing and processes it; counters restart. Folios already loaded are skipped by the service itself. |

A retry only helps with transient failures (database outage, lost lease, shutdown). A bad row fails again at
the same row: fix the file and start a new import.

Refused with `409` and a reason code at the start of the message:

| Code | Meaning |
|---|---|
| `JOB_NOT_FINISHED` | the job is queued or running |
| `JOB_COMPLETED` | nothing to retry |
| `RETRY_ALREADY_ACTIVE` | a retry of this job is queued or running |
| `INPUT_ON_OTHER_INSTANCE` | inputs are local to another instance; retry there or re-upload |
| `INPUT_UNAVAILABLE` / `INPUT_CHANGED` | the stored file is gone or differs from what the job read |
| `DATASET_JOB_ACTIVE` | another Miami-Dade job for the dataset is active |
| `RETRY_NOT_SUPPORTED` | Mapillary backfill jobs cannot be retried; start a new backfill |

The listing's `retryable`/`retryBlockedReason` use database facts only; the input check happens on the
actual retry.

## Admin endpoints

- `GET /api/admin/import-jobs?page=0&size=20&type=CSV_IMPORT&status=FAILED` — backfill, CSV and Miami-Dade
  jobs together, newest first. `type` is `MAPILLARY_IMAGE_BACKFILL`, `CSV_IMPORT` or `PROPERTY_INGESTION`.
  Backfill jobs keep their numeric `jobId` and all their previous fields; ingestion jobs have a UUID `id` and
  `jobId: null`. `untrackedJobTypes` now lists only `PROPERTY_IMAGE_CSV_IMPORT` (the legacy
  `/api/images/import/*` loader).
- `GET /api/admin/import-jobs/{id}` — a number reads a backfill job with its `results`, a UUID reads an
  ingestion job with its `items`.
- `POST /api/admin/import-jobs/{id}/retry` — see above.

## Inputs and multiple instances

| `INGESTION_INPUT_STORAGE` | Where files go | Who can run or retry the job |
|---|---|---|
| `s3` (default) | `s3://$INGESTION_S3_BUCKET/ingestion/inputs/{jobId}/` (bucket defaults to the images bucket) | any instance |
| `local` | `$INGESTION_INPUT_DIRECTORY/{jobId}/` on the instance that received the upload | that instance only, identified by `INGESTION_INSTANCE_ID` (default: host name) |

Jobs record where their inputs live; the poller never offers a local-input job to another instance, and
a retry from another instance is refused with `INPUT_ON_OTHER_INSTANCE`.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `INGESTION_INPUT_STORAGE` | `s3` | `s3` or `local` |
| `INGESTION_S3_BUCKET` | the images bucket | bucket for stored inputs |
| `INGESTION_S3_PREFIX` | `ingestion/inputs` | key prefix |
| `INGESTION_INPUT_DIRECTORY` | `ingestion-inputs` | local store directory |
| `INGESTION_STAGING_DIRECTORY` | empty (disabled) | directory whose files may be named instead of uploaded |
| `INGESTION_JOBS_ENABLED` | `true` | whether this instance runs jobs |
| `INGESTION_INSTANCE_ID` | host name | stable instance name for local inputs |
| `INGESTION_JOBS_POLL_DELAY_MILLIS` | `5000` | poll interval |
| `INGESTION_JOBS_LEASE_SECONDS` | `600` | lease length; heartbeat every 30 s |
| `INGESTION_JOBS_QUEUE_DEADLINE_SECONDS` | `600` | how long a local-input job may wait unclaimed |
| `INGESTION_CSV_BATCH_SIZE` | `500` | rows per transaction |
| `INGESTION_API_WORKERS` | `10` | parallel Miami-Dade calls per job (batches of 50, 2 s pause, 3 attempts) |
| `INGESTION_MAX_UPLOAD_SIZE` / `INGESTION_MAX_REQUEST_SIZE` | `1024MB` / `4096MB` | multipart limits |

Migration `V30__add_ingestion_jobs.sql` creates `ingestion_jobs` and `ingestion_job_items`. Like the other
migrations in this repository it has to be applied to each database by hand.
