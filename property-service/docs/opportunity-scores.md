# Investment opportunity scores — `GET /api/opportunities`, `GET /api/properties/id/{id}/opportunity-score`

Story SCRUM-76 (epic SCRUM-38 "Forecasts and opportunities"), with its subtasks:

- **SCRUM-167 — Calculate and explain opportunity scores**: the versioned scoring rules, the shared
  `OpportunityScoringService` and the live score of one property.
- **SCRUM-168 — Browse scored opportunities**: the paginated list, read from the stored output of the scoring
  service.

Depends on SCRUM-58 (ZentraValue valuations and forecasts, `property_value_predictions`) and SCRUM-64 (investment
analysis).

## What the score is, and what it is not

The opportunity score ranks properties by the **evidence that is stored today**: the ZentraValue estimate, its
forecasts, the area market outlook and the valuation confidence. Rent, financing and listing prices are part of the
score's design, but **no property has that data yet**, so those factors are reported as unavailable with a reason.

Three rules make sure missing data is never presented as a good investment:

1. **An unavailable factor adds no points.** It is not averaged away, and it is never treated as a zero cost (for
   example, unknown financing is not "no debt"). Missing data can therefore never raise a score.
2. **Coverage is always shown.** `coveragePercent` is the sum of the weights of the factors that had evidence; it
   is also the highest score that evidence allows. With today's data it is at most 50.
3. **Without essential evidence there is no score.** A property needs a supported or limited valuation with an
   estimate and a one-year forecast. Otherwise `status` is `UNAVAILABLE`, `score` is `null`, `reasons` say why,
   and the property is never ranked.

Forecasts are model trend scenarios, not guarantees, and no listing price is known: the score is a research aid,
not a recommendation to buy.

## Scoring rules — `opportunity-v1`

Every response carries `scoreVersion`. Changing a weight, a scale or the factor list requires a new version; the
refresh job then rescores every property (stored scores of another version are never listed).

Each available factor maps its evidence linearly onto a **subscore** from 0 to 100 (clamped at both ends) and earns
`points = weight × subscore / 100`, rounded to 2 decimals. The score is the sum of the points of the available
factors. The weights add up to 100.

| Code | Category | Weight | Evidence (source) | Subscore scale |
|---|---|---|---|---|
| `FORECAST_GROWTH_1Y` | MARKET | 20 | one-year forecast ÷ current estimate − 1 (`forecast_1y`, `predicted_price`) | −10 % → 0, 0 % → 50, +10 % → 100 |
| `FORECAST_GROWTH_3Y` | MARKET | 15 | three-year forecast ÷ current estimate − 1 (`forecast_3y`) | −25 % → 0, 0 % → 50, +25 % → 100 |
| `MARKET_OUTLOOK` | MARKET | 5 | one-year market outlook of the area, % (`market_outlook_1y_pct`) | −10 % → 0, +10 % → 100 |
| `VALUATION_CONFIDENCE` | VALUATION | 10 | valuation confidence score 0–100 (`confidence_score`) | the score itself |
| `RENTAL_YIELD` | RENT | 20 | rent ÷ estimate | **unavailable**: no rent estimate is stored |
| `FINANCED_CASH_FLOW` | FINANCING | 10 | cash flow after debt service | **unavailable**: needs a rent estimate and financing terms |
| `ASKING_PRICE_DISCOUNT` | PRICE | 20 | listing price vs. estimate | **unavailable**: no listing price is known |

Essential evidence (all required for `status = SCORED`):

| Missing | Reason code |
|---|---|
| no stored valuation | `NO_VALUATION` |
| valuation status `unsupported` or unknown | `VALUATION_UNSUPPORTED` |
| no estimate, or an estimate of 0 | `NO_ESTIMATE` |
| no one-year forecast | `NO_FORECAST` |

When the score is unavailable, the factors are still listed (those that could be measured keep their evidence),
so the response explains exactly what is missing.

The scales are fixed, documented ranges, not fitted to the current data. For reference, on the local data
(Miami-Dade, model `v17+v18`, data through 2026-07-26) the one-year growth runs from −9.6 % to +10.3 % between the
10th and 90th percentile, the three-year growth from −3.0 % to +18.4 %, and the market outlook is −0.17 % everywhere
(it is area-wide, so it does not separate properties of one county).

## `GET /api/properties/id/{id}/opportunity-score` (SCRUM-167)

Scores the property **now** from its current stored valuation, with the rules above. A bearer token
with the `investor` or `admin` realm role is required (SCRUM-94). Scores have no user ownership.
The valuation endpoint remains available to every authenticated account.

| Response | When |
|---|---|
| 200, `status: SCORED` | the property has the essential evidence |
| 200, `status: UNAVAILABLE`, `score: null`, `reasons` | the property exists but essential evidence is missing |
| 400 | `id` is 0, negative or not a number |
| 404 | no property has this ID |

Response of the local property 95340 (a `limited` valuation, so it is still scored); the points are
7.90 + 9.03 + 2.46 + 7.00 = 26.39 of the 50 that today's evidence allows:

```
{
  "propertyId": 95340,
  "scoreVersion": "opportunity-v1",
  "status": "SCORED",
  "score": 26.39,
  "maxScore": 100,
  "coveragePercent": 50.00,
  "factors": [
    { "code": "FORECAST_GROWTH_1Y", "label": "One-year forecast growth", "category": "MARKET", "weight": 20,
      "status": "AVAILABLE", "signal": -2.10, "signalUnit": "PERCENT", "subscore": 39.50, "points": 7.90,
      "explanation": "One-year forecast -2.1% against the current estimate ($950,628 to $930,657). Scale: -10% scores 0, +10% scores 100." },
    { "code": "FORECAST_GROWTH_3Y", ..., "signal": 5.09, "subscore": 60.19, "points": 9.03, ... },
    { "code": "MARKET_OUTLOOK", ..., "signal": -0.17, "subscore": 49.13, "points": 2.46, ... },
    { "code": "VALUATION_CONFIDENCE", ..., "signal": 70.00, "signalUnit": "SCORE", "subscore": 70.00, "points": 7.00,
      "explanation": "The valuation's confidence score is 70 of 100 (medium confidence); it is used as the subscore." },
    { "code": "RENTAL_YIELD", "label": "Rental yield", "category": "RENT", "weight": 20, "status": "UNAVAILABLE",
      "signal": null, "signalUnit": null, "subscore": null, "points": null,
      "explanation": "Unavailable: No rent estimate is stored for this property, so rental yield cannot be measured. It adds no points." },
    { "code": "FINANCED_CASH_FLOW", ..., "status": "UNAVAILABLE", "points": null, ... },
    { "code": "ASKING_PRICE_DISCOUNT", ..., "status": "UNAVAILABLE", "points": null, ... }
  ],
  "reasons": [],
  "valuation": { "estimatedValue": 950628.23, "valuationStatus": "limited", "confidence": "medium",
                 "modelVersion": "v17+v18", "dataThrough": "2026-07-26", "runId": "2026-11-14-t72415708" },
  "scoredAt": "2026-11-15T12:01:55.006205800Z",
  "notes": [ "...", "...", "..." ]
}
```

The subscore uses the unrounded evidence: the outlook is −0.1746 %, shown as `-0.17`, so its subscore is 49.13.
Without essential evidence the response has the same shape with `"status": "UNAVAILABLE"` and `"score": null`;
for example property 5756 (a valuation without forecasts) returns
`"reasons": [{ "code": "NO_FORECAST", "message": "No one-year forecast is stored for this property." }]`, and only
its valuation confidence is measured (`coveragePercent` 10.00). Property 21 has no valuation: `NO_VALUATION`, all
seven factors unavailable, `coveragePercent` 0.00 and `valuation: null`.

| Field | Meaning |
|---|---|
| `score` | points earned out of `maxScore`; `null` when `status` is `UNAVAILABLE` |
| `coveragePercent` | weights of the factors with evidence; the score never exceeds it |
| `factors[]` | all seven factors, always in the order of the table above |
| `factors[].signal` | the measured evidence (`PERCENT` or `SCORE`); `null` when unavailable |
| `factors[].points` | `null` when unavailable: it adds nothing and is not a zero cost |
| `reasons[]` | `{code, message}` of the missing essential evidence; empty when scored |
| `valuation` | the stored valuation used (`null` without one); `runId` identifies the valuation run |

## `GET /api/opportunities` (SCRUM-168)

Pages through the **stored** scores of the current `scoreVersion`, only `status = SCORED`. Requires a bearer token with the `investor` or `admin` realm role (SCRUM-94). No user ownership applies.

| Parameter | Type | Default | Rule |
|---|---|---|---|
| `page` | integer | 0 | 0 or greater; the page must start within the first 10,000 results (`page × size` below 10,000) |
| `size` | integer | 20 | 1 to 100 |
| `countyId` | integer | — | positive; only properties in this county |
| `stateId` | integer | — | positive; only properties in this state |
| `minScore` | decimal | — | 0 to 100 |
| `minValue`, `maxValue` | decimal | — | 0 or greater; `minValue` ≤ `maxValue`; range of the **estimated value** (there is no listing price) |
| `sort` | text | `SCORE_DESC` | `SCORE_DESC`, `FORECAST_GROWTH_1Y_DESC`, `VALUE_ASC`, `VALUE_DESC` (case-insensitive) |

Every order ends with the property ID, so pages never overlap; the non-score orders use the score as the second key.

Paging is limited to the first 10,000 results because an offset costs a scan of every
skipped row (8 ms at offset 10,000, 4.3 s at 400,000 on the local data). `totalElements` still reports the full
count, and the filters reach the rest.

Response of `GET /api/opportunities?size=1` on the local data:

```
{
  "scoreVersion": "opportunity-v1",
  "status": "OK",
  "filters": { "countyId": null, "stateId": null, "minScore": null, "minValue": null, "maxValue": null },
  "sort": "SCORE_DESC",
  "results": {
    "content": [
      { "propertyId": 42, "folio": "3049340040210", "address": "13761 SW 72 TER, Unincorporated County, FL 33183-0000",
        "city": "Unincorporated County", "zip": "33183-0000", "countyName": "Miami-Dade", "stateCode": "FL",
        "score": 44.49, "maxScore": 100, "coveragePercent": 50.00, "estimatedValue": 525636.50,
        "forecastGrowth1yPct": 10.32, "factors": [ ... ], "valuationRunId": "2026-11-08-6c55c6e4",
        "valuationDataThrough": "2026-07-26", "scoredAt": "2026-11-15T12:48:42.960407Z" }
    ],
    "page": 0, "size": 1, "totalElements": 416226, "totalPages": 416226, "first": true, "last": false
  },
  "notes": [ "...", "...", "..." ]
}
```

`status` tells an empty page apart:

| Status | Meaning |
|---|---|
| `OK` | at least one property matches (a page past the end is still `OK`, with `totalElements`) |
| `NO_MATCHES` | scored properties exist, but the filters exclude all of them |
| `NO_ELIGIBLE_OPPORTUNITIES` | scores are stored, but no property has the essential evidence |
| `NOT_SCORED` | no score of the current version is stored yet: run the refresh |

Each listed score was stored by the refresh job at `scoredAt` from the valuation run `valuationRunId`. The live
score of the same property (endpoint above) is calculated with the same service and rules; it can only differ when
the valuation was re-run after `scoredAt` and the refresh has not caught up yet.

## Stored scores and the refresh job

Table `property_opportunity_scores` (migration **V40**, see the note below): one row per property with a stored
valuation, with the score, status, coverage, factors and reasons (JSONB), the property's county and state, the
estimate and one-year growth (for filtering and sorting), the valuation run, model version and data date, and
`scored_at`. Rows are written only by the refresh job; the API reads them through a read-only entity.

`POST /api/admin/opportunity-scores/refresh?mode=INCREMENTAL|FULL` (admin role, synchronous):

- `INCREMENTAL` (default) scores properties without a stored score, with a score of another `scoreVersion`, or whose
  valuation `run_id` changed since they were scored.
- `FULL` rescores every property with a valuation.
- Both remove the scores of properties that no longer have a valuation.
- Properties are processed in batches of `opportunity-scores.refresh.batch-size` (default 1000) in property-ID order,
  each batch in its own transaction; an interrupted run continues on the next incremental run.
- 200 with `{mode, scoreVersion, processed, scored, unavailable, removed, startedAt, finishedAt, durationMillis}`;
  400 for an unknown mode; 409 while another run is in progress in the same instance.
- A failure is logged with its stack trace and the last stored property ID; the stored batches are kept.

The call is **synchronous**. On the local data (420,061 valuations) a FULL run took 460–483 s, about 1.1 s per
batch of 1,000, almost all of it writing the rows and their browse indexes; an incremental run with nothing to do
took 6 s. A client or proxy with a shorter timeout gives up, but the run continues on the server, and a new call
gets 409 until it ends. For large runs, prefer the scheduled incremental refresh, or a client without a timeout.
A full rewrite leaves as many dead rows as it wrote until autovacuum reclaims them; meanwhile browsing is slower
(the page count went from 0.3 s to 0.9 s locally).

Result of the first FULL run on the local data: 416,226 `SCORED` (scores from 13.38 to 44.49, average 30.51, all
with coverage 50) and 3,835 `UNAVAILABLE` (3,775 `NO_FORECAST`, 60 `VALUATION_UNSUPPORTED`).

Scheduled incremental refresh (off by default):

| Setting | Environment variable | Default |
|---|---|---|
| `opportunity-scores.refresh.enabled` | `OPPORTUNITY_SCORES_REFRESH_ENABLED` | `false` |
| `opportunity-scores.refresh.initial-delay-millis` | `OPPORTUNITY_SCORES_REFRESH_INITIAL_DELAY_MILLIS` | 60000 |
| `opportunity-scores.refresh.poll-delay-millis` | `OPPORTUNITY_SCORES_REFRESH_DELAY_MILLIS` | 900000 (15 min) |
| `opportunity-scores.refresh.batch-size` | `OPPORTUNITY_SCORES_BATCH_SIZE` | 1000 |

**Migration number.** V40: V35 to V37 and V39 were merged first (V39 by SCRUM-151), and Flyway refuses a migration
numbered below one already applied, so this one must come after V39. V38 stays unused. The app uses
`ddl-auto: validate`, so the table must exist before the app starts.

## Errors

Shared `GlobalExceptionHandler` shape: `{status, error, message, timestamp}`.

| Case | Status | Message |
|---|---|---|
| `id` 0 or negative | 400 | `propertyId must be positive` |
| `id`, `countyId`, `minScore`, … not a number | 400 | `<name>: invalid value` |
| `page` < 0 | 400 | `page must be 0 or greater` |
| `page × size` 10,000 or more | 400 | `page is too large: only the first 10000 results can be paged; narrow the filters` |
| `size` outside 1–100 | 400 | `size must be between 1 and 100` |
| `countyId` / `stateId` ≤ 0 | 400 | `countyId must be a positive number` |
| `minScore` outside 0–100 | 400 | `minScore must be between 0 and 100` |
| `minValue` / `maxValue` < 0 | 400 | `minValue must be 0 or greater` |
| `minValue` > `maxValue` | 400 | `minValue must not exceed maxValue` |
| unknown `sort` | 400 | `sort must be one of SCORE_DESC, FORECAST_GROWTH_1Y_DESC, VALUE_ASC, VALUE_DESC` |
| unknown property | 404 | `Property not found: <id>` |
| refresh: unknown `mode` | 400 | `mode: invalid value` |
| refresh: already running | 409 | `An opportunity score refresh is already running` |

## Verifying locally

Postman collection: [`docs/postman/SCRUM-76-opportunities.postman_collection.json`](postman/SCRUM-76-opportunities.postman_collection.json).
Set `baseUrl`, a scored `propertyId`, a `propertyWithoutValuationId`, a `countyId` and, for the refresh, an admin
`refreshToken` (the collection exchanges it for an access token; no secret is stored in the file). The defaults
(95340, 21, county 1) are local Miami-Dade IDs.

1. Apply V40 to the database, start the app, run the refresh (`FULL` once).
2. Live score: a scored property (factors, coverage, version, unavailable rent/financing/price), a property without
   valuation (`UNAVAILABLE`, `NO_VALUATION`), an unknown ID (404), invalid IDs (400).
3. Browse: default page (score descending), filters and every sort, paging without overlap, `NO_MATCHES`
   (`minScore=100`, impossible with today's coverage of 50), and every invalid parameter (400).
4. Refresh: incremental after the full run processes 0 properties; a second call during a run is 409; without a
   token it is refused (401).

All four steps were run against the local database on 2026-11-15 (results above). Timings measured there with
warm caches: a browse page reads its 20 rows in about 1 ms with any order or filter; the exact `totalElements` count
costs 0.3 s on a vacuumed table (0.9 s right after a full rewrite) and is most of the response time.

Ownership: none applies — scores are derived from public property data and contain nothing user-specific.

## Implementation

| File | Role |
|---|---|
| `db/migration/V40__add_property_opportunity_scores.sql` | stored scores; one index per order (score, growth, value) and per county/state filter, each ending in the full sort order where values tie |
| `service/OpportunityScoringService.java`, `impl/OpportunityScoringServiceImpl.java` | rules `opportunity-v1`; live score of one property; `score(signals)` shared with the refresh |
| `service/OpportunityBrowseService.java`, `impl/OpportunityBrowseServiceImpl.java` | validated filters and orders over the stored scores |
| `service/OpportunityScoreRefreshService.java`, `impl/OpportunityScoreRefreshServiceImpl.java` | batch scoring (incremental or full), one run at a time |
| `repository/OpportunityScoreJdbcRepository.java` | the evidence query (one for both paths), batch upsert, orphan removal |
| `repository/PropertyOpportunityScoreRepository.java`, `entity/PropertyOpportunityScore.java` | read-only browse query and property details |
| `controller/OpportunityController.java` | the two investor/admin endpoints |
| `controller/AdminOpportunityScoreController.java` | `POST /api/admin/opportunity-scores/refresh` |
| `config/OpportunityScoreRefreshScheduler.java` | optional scheduled incremental refresh |
| `dto/Opportunity*.java` | request and response records |

Automated tests (added on request, although the tickets ask for manual verification only):
`OpportunityScoringServiceImplTest`, `OpportunityBrowseServiceImplTest`, `OpportunityScoreRefreshServiceImplTest`,
`OpportunityControllerTest`, `AdminOpportunityScoreControllerTest`, `OpportunityScoreJdbcRepositoryTest` (the values
the batch upsert binds, including valuations without forecasts), and the opt-in PostgreSQL test
`OpportunityScoreJdbcRepositoryDatabaseTest` (set `OPPORTUNITY_TEST_DB_URL` to a dedicated local database whose name
ends with `_test`).
