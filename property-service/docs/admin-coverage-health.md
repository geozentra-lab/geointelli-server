# Admin coverage and provider health — `GET /api/admin/coverage-health`

Ticket SCRUM-191 (parent SCRUM-92 "Review data quality, coverage and provider health"). One read-only
endpoint for administrators: how many data-quality findings there are, how much of the property catalogue each
source covers, and whether the supported providers' last recorded jobs succeeded. Every figure is counted from
recorded rows; nothing is estimated, and no provider is called.

## Access

| Situation | Status |
|-----------|--------|
| no bearer token | 401 |
| token without the Keycloak realm role `admin` | 403 |
| admin | 200 |

There are no parameters and no user-owned data, so there is no ownership check.

## Response

```json
{
  "observedAt": "2026-11-20T03:25:43Z",
  "quality": {
    "status": "AVAILABLE",
    "generatedAt": "2026-11-20T03:23:46Z",
    "totalIssues": 1946,
    "rules": [ { "rule": "SALE_DUPLICATE", "issueCount": 951, ... }, ... ],
    "reason": null
  },
  "coverage": {
    "status": "AVAILABLE",
    "countedAt": "2026-11-20T03:23:46Z",
    "totalProperties": 582589,
    "sources": [
      { "source": "ADDRESSES", "propertiesCovered": 582576, "coveragePercent": 99.99 },
      { "source": "SALES", "propertiesCovered": 552272, "coveragePercent": 94.80 },
      ...
    ],
    "reason": null
  },
  "providers": [
    { "provider": "MIAMI_DADE_API", "status": "UNAVAILABLE", "lastJobStatus": null, "lastJobAt": null,
      "lastSuccessAt": null, "lastErrorCode": null, "activeJobs": 0, "reason": "No finished job is recorded" },
    { "provider": "MAPILLARY", "status": "DEGRADED", "lastJobStatus": "INTERRUPTED",
      "lastJobAt": "2026-11-05T17:42:10Z", "lastSuccessAt": null, "lastErrorCode": "WORKER_LEASE_EXPIRED",
      "activeJobs": 0, "reason": null }
  ],
  "note": "Read-only. Quality and coverage are full-table counts, ..."
}
```

### Times

| Field | Meaning |
|-------|---------|
| `observedAt` | when this response was built; provider health is read at this moment |
| `quality.generatedAt` | when the data-quality rule counts were calculated (data-quality scan, SCRUM-189) |
| `coverage.countedAt` | when the coverage counts were taken |

### `quality`

The rule counts of the data-quality scan (`GET /api/admin/data-quality/issues`, summary only) and their sum.
`UNAVAILABLE` with a reason when they were never calculated or could not be read.

### `coverage`

For each source, the properties with at least one recorded row: `ADDRESSES`, `SALES`, `ASSESSMENTS`, `TAXES`,
`IMAGES` (`property_images`) and `VALUATIONS` (`property_value_predictions` with a predicted price).
`coveragePercent` = `propertiesCovered / totalProperties x 100`, two decimals. Rounding never claims full or no
coverage that is not there: `100.00` only when every property is covered (otherwise at most `99.99`), `0.00` only
when none is (otherwise at least `0.01`). `UNAVAILABLE` when no property is recorded (never `0%`), or before the
first calculation.

### `providers`

| Provider | Recorded jobs |
|----------|---------------|
| `MIAMI_DADE_API` | `ingestion_jobs` with `job_type = PROPERTY_INGESTION` |
| `CSV_IMPORT` | `ingestion_jobs` with `job_type = CSV_IMPORT` |
| `MAPILLARY` | `image_backfill_jobs` |

| Status | When |
|--------|------|
| `HEALTHY` | the latest finished job (any status but QUEUED or RUNNING) is `COMPLETED` |
| `DEGRADED` | it is `PARTIAL`, `FAILED` or `INTERRUPTED` (`lastErrorCode` says why) |
| `UNAVAILABLE` | no job has finished yet (`reason`: none recorded, or the first one is still running), or the job records could not be read |

`lastSuccessAt` is the end of the last `COMPLETED` job, `activeJobs` the jobs queued or running now.

## Response time: counts kept in memory and refreshed in the background

The data-quality scan and the coverage counts read whole tables (about two minutes on 580,000 properties, of
which 12–13 s for coverage). The endpoint never makes the caller wait for them:

1. The last quality and coverage blocks are kept in memory, each with its time (`generatedAt`, `countedAt`), and
   returned at once.
2. When they are older than `admin.coverage-health.refresh-seconds` (default 600), the call still returns them and
   starts one recalculation on a background thread; the next call shows the new figures. Only one recalculation
   runs at a time, however many admins call.
3. Before the first calculation ends (right after the application starts), `quality` and `coverage` are
   `UNAVAILABLE` with the reason "Being calculated in the background; try again in a few minutes".
4. A recalculation that cannot read the records keeps the last good block with its older time.
5. Provider health reads a few rows per provider and is always read live.

Nothing is calculated while nobody calls the endpoint. Each application instance keeps its own figures.

| Variable | Default | Meaning |
|----------|---------|---------|
| `ADMIN_COVERAGE_HEALTH_REFRESH_SECONDS` | `600` | age after which quality and coverage are recalculated (at least 1) |
| `DATA_QUALITY_SUMMARY_CACHE_SECONDS` | `600` | how long the data-quality scan reuses its own rule counts |

## Errors

| Status | When |
|--------|------|
| 401 | no or invalid token |
| 403 | not an admin |

Missing data is never an error: it is a block or a provider with status `UNAVAILABLE` and a `reason`.

## Files

| Part | File |
|------|------|
| Controller | `controller/AdminCoverageHealthController` |
| Service | `service/AdminCoverageHealthService`, `service/impl/AdminCoverageHealthServiceImpl` |
| Counts (SQL) | `repository/CoverageHealthReader`, `repository/JdbcCoverageHealthReader` |
| Response | `dto/AdminCoverageHealthDTO` |
| Postman | `docs/postman/SCRUM-191-admin-coverage-health.postman_collection.json` |

## Manual verification (Postman)

Set `baseUrl`, `admin_token` (realm role `admin`) and `user_token` (no admin role), then run in order.

| # | Request | Expected |
|---|---------|----------|
| 01 | GET without token | 401 |
| 02 | GET as a non-admin | 403 |
| 03 | GET as admin (first call after start-up) | 200; `quality` and `coverage` either AVAILABLE or UNAVAILABLE "Being calculated…"; providers present; answers in under a few seconds |
| 04 | GET as admin again after a few minutes | 200; `quality.generatedAt` and `coverage.countedAt` set; `totalIssues` = sum of the rules; every `coveragePercent` between 0 and 100, 100.00 only when `propertiesCovered` = `totalProperties` |
| 05 | GET as admin, provider checks | each provider HEALTHY, DEGRADED or UNAVAILABLE; UNAVAILABLE has a reason, the others a `lastJobStatus` and `lastJobAt` |
