# Rent evidence: asking and achieved rent observations (SCRUM-132)

Rent evidence is what a provider reports about rents in an **area**, not about one property or one lease. An
example is the median rent paid in a census tract over 2019-2023, or the median asking rent of 2-bedroom
listings in a ZIP code last month. Each row records the source, the geography, the period, the currency and
whether the amounts are monthly or annual.

Part of SCRUM-55 "Ingest market, rent and economic evidence". Built like market evidence (SCRUM-131,
`market-evidence-ingestion.md`), on the same ingestion job framework (`ingestion-jobs.md`).

## What is stored

Table `rent_evidence_observations` (migration V50). One row = one source × one rent type × one geography ×
one period × one rent period × one bedroom count.

| Group | Columns | Notes |
|---|---|---|
| Rent type | `evidence_type` | `ASKING` = rent advertised by landlords (listings). `ACHIEVED` = rent actually paid by tenants (census gross rent, registries, lease surveys). The two are never combined into one figure. |
| Provenance | `source`, `source_dataset`, `source_release_date`, `import_job_id` | Provider code, the provider's table or file, the date the provider published it, the ingestion job that wrote the row. |
| Freshness | `imported_at`, `updated_at` | When we last wrote the row. |
| Geography | `geography_level`, `geography_code`, `geography_name`, `state_id`, `county_id` | `STATE` (two letters), `COUNTY` (five-digit FIPS), `ZIP` (five digits) or `TRACT` (eleven-digit tract FIPS). `state_id` / `county_id` link to our tables when the area matches one we have configured; otherwise null. |
| Period | `period_start`, `period_end` | Inclusive dates. A month, a year, or a multi-year survey window (ACS 5-year: 2019-01-01 to 2023-12-31). |
| Kind | `observation_kind` | `ACTUAL` (observed: listings, leases) or `ESTIMATE` (survey estimate, modelled or preliminary value). |
| Units | `currency`, `rent_period` | `currency`: three letters, `USD` by default. `rent_period`: `MONTHLY` or `ANNUAL`, **required**. It is the unit of every amount on the row. |
| Segment | `bedrooms` | 0 (studio) to 20; null = all sizes together. |
| Figures | `median_rent`, `average_rent`, `median_rent_per_sqft`, `median_rent_moe`, `sample_count` | Amounts per `rent_period`, in `currency`. At least one of the first three is required. `median_rent_moe` is the provider's margin of error on the median. `sample_count` is the number of listings, leases or survey units, when the provider publishes it. |

Unique key: `(source, evidence_type, geography_level, geography_code, period_start, period_end, rent_period,
bedrooms)`, declared `NULLS NOT DISTINCT` so two "all sizes" rows also count as the same observation. It
needs PostgreSQL 15 or later. Importing the same observation again updates the row instead of adding a copy.

### Monthly and annual are never mixed

- Every amount is stored exactly as the provider published it, in the row's `rent_period`. No converted value
  is stored.
- A MONTHLY row and an ANNUAL row of the same area and period are two separate observations.
- The only conversion is explicit and done on read: `GET /api/rent-evidence?...&asMonthly=true` divides the
  amounts of ANNUAL rows by 12. It returns them with `rentPeriod = MONTHLY` and `convertedFrom = ANNUAL`, so
  a converted value is never mistaken for a published one. Without `asMonthly`, `convertedFrom` is always
  null.

## The CSV contract: `rent_evidence.csv`

UTF-8, comma separated, header row required. Column order does not matter. Unknown columns are ignored.

| Column | Required | Format | Rule |
|---|---|---|---|
| `source` | yes | 2–64 letters, digits, `_`, `.`, `-` | Provider code, stored upper-case, e.g. `US_CENSUS_ACS`. |
| `evidence_type` | yes | `ASKING` or `ACHIEVED` | Case-insensitive. |
| `geography_level` | yes | `STATE`, `COUNTY`, `ZIP` or `TRACT` | Case-insensitive. |
| `geography_code` | yes | depends on level | `STATE`: two letters (stored upper-case). `COUNTY` and `ZIP`: five digits. `TRACT`: eleven digits. |
| `period_start`, `period_end` | yes | `yyyy-MM-dd` | `period_end` must not be before `period_start`. |
| `rent_period` | yes | `MONTHLY` or `ANNUAL` | Unit of every amount on the row. |
| `median_rent`, `average_rent`, `median_rent_per_sqft` | at least one | number ≥ 0 | In the row's `rent_period` and `currency`. Commas and `$` are tolerated. |
| `median_rent_moe` | no | number ≥ 0 | Needs a `median_rent` on the same row. |
| `sample_count` | no | whole number ≥ 0 | |
| `bedrooms` | no | whole number 0–20 | Empty = all sizes. |
| `geography_name` | no | text | Display name, e.g. `Miami Beach`. |
| `state_code` | no | two letters | Must be a known state. On `COUNTY` and `TRACT` rows it must agree with the county found by FIPS. |
| `county_name` | no | text | `ZIP` rows only, with `state_code`: links the row to our county by name. |
| `source_dataset` | no | text | Provider's table or file name. |
| `source_release_date` | no | `yyyy-MM-dd` | When the provider published the figures. |
| `observation_kind` | no | `ACTUAL` or `ESTIMATE` | Default `ACTUAL`. |
| `currency` | no | three letters | Default `USD`. |

How rows link to our states and counties:

- A `STATE` row links through its own code.
- `COUNTY` and `TRACT` rows link through the first five digits of the code (county FIPS,
  `counties.fips_code`), which also gives the state. Tract `12086000107` → county `12086` → Miami-Dade, FL.
- `ZIP` rows link through `state_code` and `county_name`.
- A county we have not configured leaves `county_id` empty. That is not an error.

Row rules:

- A failing row stops the job as `FAILED` with `errorCode = INVALID_ROW` and an item `rent_evidence.csv:<record>`
  naming the column. For example: `rent_evidence.csv:2: rent_period: expected one of [MONTHLY, ANNUAL], got
  'WEEKLY'`. `<record>` counts data rows from 1, the header excluded.
- The batch holding the bad row (500 rows per batch) is rolled back as a whole. Earlier committed batches stay
  imported. Fix the file and import it again: rows already written are updated, not duplicated.
- A file missing a required column, or an empty (0-byte) file, is refused with `400` before any job starts.
- A file with a header and no rows completes with 0 rows.

Example:

```csv
source,evidence_type,geography_level,geography_code,state_code,period_start,period_end,rent_period,currency,observation_kind,median_rent,median_rent_moe,sample_count,source_dataset,source_release_date
US_CENSUS_ACS,ACHIEVED,TRACT,12086000107,FL,2019-01-01,2023-12-31,MONTHLY,USD,ESTIMATE,2094,236,,"ACS 2019-2023 5-year, table B25064 median gross rent",2024-12-12
```

## Supported sources

| Source code | Rent type | Status |
|---|---|---|
| `US_CENSUS_ACS` | ACHIEVED | **Proposed first source, pending product-owner confirmation.** US Census Bureau American Community Survey, 2019-2023 5-year estimates, table B25064 (median gross rent of renter-occupied units paying cash rent), per Florida census tract, monthly, USD, released 2024-12-12. Public federal data. |
| — | ASKING | **No supported asking-rent source yet.** Listing providers (Zillow, Realtor.com and similar) need authorization before their data can be loaded. Until one is approved, every area reports `ASKING` in `missingTypes`. |

### Preparing the Census file

The Census Bureau writes codes in place of missing values. The Florida file was prepared as follows:

| Census value | Meaning | Tracts | What we did |
|---|---|---|---|
| median `-666666666`, MOE `-222222222` | Too few sample cases to estimate | 377 | Row left out → the tract reports `NO_COVERAGE` |
| median `3501`, MOE `-333333333` | The median falls in the open top interval, "$3,500 or more" | 51 | Row left out: 3501 is a floor, not a median |
| renter-occupied units | The universe of the table, itself an estimate | all | Not used as `sample_count`, which stays empty |

Result: 4,732 of 5,160 Florida tracts, median rents $286 to $3,467 a month. The 428 tracts left out are
listed with their reason in `excluded_tracts.csv` next to the staged file.

## Endpoints

| Endpoint | Who | What |
|---|---|---|
| `POST /api/admin/rent-evidence/import` | realm role `admin` | Starts an import job for one `rent_evidence.csv`. Multipart `file`, or `stagedFile` (path inside `INGESTION_STAGING_DIRECTORY`). Optional `county` + `sourceState` label the job when the file covers one county: both or neither. Returns `202` with the job and `Location: /api/ingestion/jobs/{id}`. No token `401`, not admin `403`, `409` while another rent evidence import is queued or running or when V50 is not applied. |
| `GET /api/ingestion/jobs/{id}` | realm role `admin` | Job status, counters, failed rows (shared with all ingestion jobs). |
| `GET /api/rent-evidence` | authenticated (any valid token, `401` without one) | Rent observations of one geography, newest period first, with a coverage report. |

The generic `POST /api/ingestion/import/rent-evidence` is refused with `400`, pointing to the admin endpoint.

The import answers `409` instead of queuing a job when the table `rent_evidence_observations` is missing
("apply migration V50 first") or when another rent evidence import is already queued or running
(`DATASET_JOB_ACTIVE`): two imports at once could both insert the same new observation. `county` without
`sourceState`, or the reverse, is a `400` ("give both or neither").

Ownership: the observations describe areas, not users, so no record belongs to a caller. The read needs a
valid token of any role (`SecurityConfig`, as for `GET /api/market-evidence`) but has no per-user check. The
write and the job status are restricted to administrators by `/api/admin/**` and `/api/ingestion/**`.

### `GET /api/rent-evidence`

| Parameter | Required | Meaning |
|---|---|---|
| `level` | yes | `STATE`, `COUNTY`, `ZIP` or `TRACT` |
| `code` | yes | In the level's format; a lower-case state code is accepted |
| `type` | no | `ASKING` or `ACHIEVED`; both when omitted. Coverage is reported for the requested types. |
| `source` | no | Provider code |
| `rentPeriod` | no | `MONTHLY` or `ANNUAL`, as published |
| `from`, `to` | no | `yyyy-MM-dd`; keeps periods that overlap the window |
| `asMonthly` | no | `true` converts ANNUAL rows to monthly (÷ 12), marked `convertedFrom = ANNUAL` |
| `page`, `size` | no | Default `0` and `50`; `size` 1 to 200 |

Response:

```json
{
  "geographyLevel": "TRACT",
  "geographyCode": "12001000201",
  "coverage": "PARTIAL",
  "availableTypes": ["ACHIEVED"],
  "missingTypes": ["ASKING"],
  "note": "No asking rent evidence for TRACT 12001000201 under these filters.",
  "observations": {
    "content": [{
      "id": 1, "evidenceType": "ACHIEVED", "observationKind": "ESTIMATE", "source": "US_CENSUS_ACS",
      "sourceDataset": "ACS 2019-2023 5-year, table B25064 median gross rent", "sourceReleaseDate": "2024-12-12",
      "geographyLevel": "TRACT", "geographyCode": "12001000201", "geographyName": null,
      "stateId": 1, "countyId": null, "periodStart": "2019-01-01", "periodEnd": "2023-12-31",
      "currency": "USD", "rentPeriod": "MONTHLY", "convertedFrom": null, "bedrooms": null,
      "medianRent": 1018.00, "averageRent": null, "medianRentPerSqft": null, "medianRentMoe": 112.00,
      "sampleCount": null, "importedAt": "2026-11-25T10:17:02Z", "importJobId": "…"
    }],
    "page": 0, "size": 50, "totalElements": 1, "totalPages": 1, "first": true, "last": true
  }
}
```

Missing coverage is always explicit:

| `coverage` | Meaning |
|---|---|
| `AVAILABLE` | Every requested rent type has at least one observation |
| `PARTIAL` | Some requested types have observations; `missingTypes` lists the others |
| `NO_COVERAGE` | Nothing for this geography under these filters; `observations.content` is empty |

Errors: `400` when the code does not fit the level, the level or another enum value is unknown, `to` is
before `from`, or the paging is out of range.

## Verification (local Docker, 2026-11-25)

Run against a local PostgreSQL 16 copy with V50 applied and three Florida counties configured (Miami-Dade,
Broward, Palm Beach), on top of master with SCRUM-131 (market evidence) and SCRUM-94 (role permissions)
merged. RDS was not reachable at the time. Postman collection:
`docs/postman/SCRUM-132-rent-evidence.postman_collection.json`, sample files under
`docs/postman/samples/rent-evidence/`. The collection's 33 requests and their tests passed (33 / 33) on a
freshly emptied table; reads use a normal user's token.

| Case | Result |
|---|---|
| Import the Census file (4,732 tracts) | `202`; job `COMPLETED` 4,732 / 4,732, 0 failed, 35 s; every row linked to FL, the 1,346 tracts of the three configured counties linked to their county |
| Import the same file again | `COMPLETED`; still 4,732 rows (updated, not duplicated) |
| Sample file (asking ZIP and tract rows, one ANNUAL and one MONTHLY county row) | `COMPLETED` 4 / 4 |
| Bad row (`rent_period = WEEKLY` on record 2) | `FAILED`, `INVALID_ROW`, `rent_evidence.csv:2: rent_period: expected one of [MONTHLY, ANNUAL], got 'WEEKLY'`; its batch rolled back |
| Missing `rent_period` column | `400` `The file rent_evidence.csv is missing the columns [rent_period]` |
| Empty file | `400` `The file rent_evidence.csv is empty` |
| Header only | `COMPLETED`, 0 rows |
| Import with no token / non-admin user | `401` / `403` |
| Read with no token | `401` |
| Second import while the Census job is running | `409` `DATASET_JOB_ACTIVE` |
| Import while the table is missing (renamed for the test) | `409` `The table rent_evidence_observations is missing: apply migration V50 first` |
| Generic import endpoint | `400` pointing to the admin endpoint |
| Read a tract with Census and sample asking rows | `AVAILABLE`, both rows kept separate |
| Read a tract with Census only | `PARTIAL`, `missingTypes = [ASKING]` |
| Read a tract left out of the Census file | `NO_COVERAGE` |
| Read county 12086 as published | ANNUAL 30000 and MONTHLY 2500 returned as two rows |
| Read county 12086 with `asMonthly=true` | ANNUAL row returned as MONTHLY 2500, `convertedFrom = ANNUAL` |
| Wrong code length, unknown level, inverted window, `size=500` | `400` each |

Before V50 is applied on RDS, confirm that RDS runs PostgreSQL 15 or later (`NULLS NOT DISTINCT`).
