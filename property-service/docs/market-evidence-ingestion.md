# Market evidence — sale and asking-price observations (SCRUM-131)

Market evidence is what a provider reports about a **market**, not about one property: how many homes sold
in a ZIP code last month and at what median price, how many were offered for sale and at what asking price.
It is stored separately from the per-property `sales` table, so the two are never confused.

Part of SCRUM-55 "Ingest market, rent and economic evidence". Rent (SCRUM-132) and economic indicators
(SCRUM-133) follow the same pattern with their own tables.

## What is stored

Table `market_evidence_observations` (migration V44). One row = one source × one evidence type × one
geography × one period.

| Group | Columns | Notes |
|---|---|---|
| Evidence type | `evidence_type` | `SALE` (closed transactions) or `ASKING` (homes offered for sale). The two are never combined into one figure. |
| Provenance | `source`, `source_dataset`, `source_release_date`, `import_job_id` | Provider code, the provider's file or series name, the date the provider published it, the ingestion job that wrote the row. |
| Freshness | `imported_at`, `updated_at` | When we last wrote the row. |
| Geography | `geography_level`, `geography_code`, `geography_name`, `state_id`, `county_id` | The smallest geography the provider verifies: `STATE` (two-letter code), `COUNTY` (five-digit FIPS) or `ZIP` (five digits). `state_id`/`county_id` link to our tables when the market matches one we have configured; otherwise null. |
| Period | `period_start`, `period_end` | Inclusive dates; a monthly series uses the first and last day of the month. |
| Kind | `observation_kind` | `ACTUAL` (observed by the provider) or `ESTIMATE` (modelled or preliminary). |
| Units | `currency` | Three-letter code, `USD` by default. Prices are in that currency, per-square-foot figures per square foot, days on market in days. |
| SALE figures | `sale_count`, `median_sale_price`, `average_sale_price`, `median_sale_price_per_sqft` | Allowed only on `SALE` rows (database check). |
| ASKING figures | `active_listing_count`, `new_listing_count`, `median_asking_price`, `average_asking_price`, `median_asking_price_per_sqft`, `median_days_on_market`, `price_reduced_count` | Allowed only on `ASKING` rows (database check). |

Unique key: `(source, evidence_type, geography_level, geography_code, period_start, period_end)`. Importing
the same observation again updates the row instead of adding a copy, which is what makes repeated imports
idempotent.

Nothing financing-related is stored. A provider column such as a mortgage rate is not part of the contract
and is ignored if present in a file.

## The CSV contract: `market_evidence.csv`

UTF-8, comma separated, header row required. Column order does not matter. Unknown columns are ignored.

| Column | Required | Format | Rule |
|---|---|---|---|
| `source` | yes | 2–64 letters, digits, `_`, `.`, `-` | Provider code, stored upper-case, e.g. `REALTOR_COM_RESEARCH`. |
| `evidence_type` | yes | `SALE` or `ASKING` | Case-insensitive. |
| `geography_level` | yes | `STATE`, `COUNTY` or `ZIP` | Case-insensitive. |
| `geography_code` | yes | depends on level | `STATE`: two letters (stored upper-case). `COUNTY`: five-digit FIPS. `ZIP`: five digits. |
| `period_start`, `period_end` | yes | `yyyy-MM-dd` | `period_end` must not be before `period_start`. |
| `geography_name` | no | text | Display name from the provider, e.g. `Miami-Dade`. |
| `state_code` | no | two letters | Links `COUNTY` and `ZIP` rows to our state. Must be a known state. `STATE` rows link through their own code. |
| `county_name` | no | text | With a state, links the row to our county by name. A county we have not configured leaves the link empty (not an error). On `COUNTY` rows `geography_name` is used when `county_name` is absent. |
| `source_dataset` | no | text | Provider's file or series name. |
| `source_release_date` | no | `yyyy-MM-dd` | When the provider published the figures. |
| `observation_kind` | no | `ACTUAL` or `ESTIMATE` | Default `ACTUAL`. |
| `currency` | no | three letters | Default `USD`. |
| `sale_count` | SALE only | whole number ≥ 0 | |
| `median_sale_price`, `average_sale_price`, `median_sale_price_per_sqft` | SALE only | number ≥ 0 | Commas and `$` are tolerated. |
| `active_listing_count`, `new_listing_count`, `price_reduced_count` | ASKING only | whole number ≥ 0 | |
| `median_asking_price`, `average_asking_price`, `median_asking_price_per_sqft`, `median_days_on_market` | ASKING only | number ≥ 0 | |

Row rules:

- A `SALE` row must leave every ASKING column empty and vice versa. A row that mixes the two fails the job.
- A row needs at least one figure of its own type.
- Any failing row stops the job as `FAILED` with `errorCode = INVALID_ROW` and an item
  `market_evidence.csv:<record>` naming the column. `<record>` counts data rows from 1, the header excluded
  (record 2 is the third line of the file). The batch holding the bad row (500 rows per batch) is rolled
  back as a whole; earlier committed batches stay imported. Fix the file and import it again: the rows
  already written are updated, not duplicated.

Minimal example (six rows: two months of a ZIP, one county month, one state month):

```csv
source,evidence_type,geography_level,geography_code,geography_name,state_code,county_name,period_start,period_end,source_dataset,source_release_date,observation_kind,currency,sale_count,median_sale_price,average_sale_price,median_sale_price_per_sqft,active_listing_count,new_listing_count,median_asking_price,average_asking_price,median_asking_price_per_sqft,median_days_on_market,price_reduced_count
SAMPLE_PROVIDER,SALE,ZIP,33139,Miami Beach 33139,FL,Miami-Dade,2026-09-01,2026-09-30,zip_monthly_sales,2026-10-15,ACTUAL,USD,42,615000,702350.50,598.20,,,,,,,
SAMPLE_PROVIDER,ASKING,ZIP,33139,Miami Beach 33139,FL,Miami-Dade,2026-09-01,2026-09-30,zip_monthly_inventory,2026-10-15,ACTUAL,USD,,,,,318,74,689000,815400,642.10,61,55
```

## Endpoints

| Endpoint | Who | What |
|---|---|---|
| `POST /api/admin/market-evidence/import` | realm role `admin` | Starts an import job for one `market_evidence.csv`. Multipart `file`, or `stagedFile` (name inside `INGESTION_STAGING_DIRECTORY`). Optional `county` + `sourceState` to label the job when the file is about one county. Returns `202` with the job and `Location: /api/ingestion/jobs/{id}`. |
| `GET /api/ingestion/jobs/{id}` | authenticated | Job status, counters, failed rows (shared with all ingestion jobs, see `ingestion-jobs.md`). |
| `GET /api/admin/import-jobs` | `admin` | Job list; market imports appear with `dataset = MARKET_EVIDENCE` and retry like other CSV jobs. |
| `GET /api/market-evidence` | authenticated | One market, newest period first. |

The generic `POST /api/ingestion/import/{dataset}` refuses `market-evidence` with `400` and points to the
admin endpoint, so market files cannot be imported without the admin role.

### `GET /api/market-evidence`

| Parameter | Required | Meaning |
|---|---|---|
| `level` | yes | `STATE`, `COUNTY` or `ZIP` |
| `code` | yes | Code in the level's format (`fl` is accepted for `FL`) |
| `type` | no | `SALE` or `ASKING`; both when omitted |
| `source` | no | Provider code; all when omitted |
| `from`, `to` | no | `yyyy-MM-dd`. Keeps periods that overlap the window: `period_end >= from` and `period_start <= to` |
| `page`, `size` | no | Zero-based page, size 1–200 (default 0 and 50) |

Response: the project's `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`,
`last`). Each item:

```json
{
  "id": 12, "evidenceType": "ASKING", "observationKind": "ACTUAL",
  "source": "SAMPLE_PROVIDER", "sourceDataset": "zip_monthly_inventory", "sourceReleaseDate": "2026-10-15",
  "geographyLevel": "ZIP", "geographyCode": "33139", "geographyName": "Miami Beach 33139",
  "stateId": 10, "countyId": 1,
  "periodStart": "2026-09-01", "periodEnd": "2026-09-30", "currency": "USD",
  "saleCount": null, "medianSalePrice": null, "averageSalePrice": null, "medianSalePricePerSqft": null,
  "activeListingCount": 318, "newListingCount": 74, "medianAskingPrice": 689000, "averageAskingPrice": 815400,
  "medianAskingPricePerSqft": 642.10, "medianDaysOnMarket": 61, "priceReducedCount": 55,
  "importedAt": "2026-11-20T15:02:11Z", "importJobId": "4f1c7d2e-9b2a-4c1e-8f3a-2d9e6b7c1a55"
}
```

Errors: `400` for an unknown `level`, a `code` that does not fit the level, `to` before `from`, or `size`
outside 1–200. A market with nothing imported returns `200` with an empty `content`, never `404`.

## Supported-market coverage

Coverage is whatever has been imported from an authorized source: this feature stores observations, it does
not fetch them. The smallest geography supported is the smallest the provider verifies for that series;
a ZIP series is stored as `ZIP`, a county series as `COUNTY`, never interpolated downwards.

As of 2026-11-20 no provider has been authorized, so the table is empty in every environment; the
`SAMPLE_PROVIDER` rows in the Postman collection exist only to verify the contract and should not be loaded
outside test databases. Once a provider is named, add a line here per source: provider, dataset, levels,
period granularity, licence terms and refresh cadence.

## Provider adapters and background ingestion

The import is provider-neutral on purpose: a provider adapter is a converter from the provider's file or API
to `market_evidence.csv`, followed by `IngestionLaunchService.startCsv(IngestionDataset.MARKET_EVIDENCE, …)`.
The job framework already gives background execution, batching, checkpoints, retries and the admin job list.
When an authorized source is chosen, its adapter should:

1. map the provider's geography identifier to one of our three levels and code formats;
2. map the provider's period to `period_start`/`period_end` (first and last day of the month for monthly data);
3. keep sales and listings in separate rows (`SALE` / `ASKING`), set `observation_kind = ESTIMATE` for any
   value the provider flags as preliminary or modelled, and set `source_release_date` from the provider's
   publication date;
4. skip columns outside the contract, in particular any financing-rate column;
5. hand the CSV to the launch service, or stage it under `INGESTION_STAGING_DIRECTORY` and call the admin
   endpoint with `stagedFile`.

## Manual verification

Collection: `docs/postman/SCRUM-131-market-evidence.postman_collection.json`; sample files in
`docs/postman/samples/market-evidence/`. Set `sampleDir` to that folder's absolute path. Checks:

| # | Case | Expected |
|---|---|---|
| 03 | Read ZIP 33139 before any import | `200`, `totalElements = 0` |
| 04–05 | Admin imports `market_evidence.csv` (6 rows) | `202`; job `COMPLETED`, `completed = 6`, `failed = 0` |
| 06–08 | Read ZIP 33139, filter `type=SALE`, read state `FL` with a window | SALE items have null asking figures and vice versa; filters narrow the page |
| 09–11 | Import the same file again | `202`; job `COMPLETED`; ZIP 33139 still has the same `totalElements` |
| 12 | File without `period_end` | `400` naming the missing column |
| 13 | No file | `400` |
| 14–15 | File with a negative `sale_count` on data row 2 | `202`; job `FAILED`, `errorCode = INVALID_ROW`, item `market_evidence.csv:2`, nothing written for ZIP 33140 |
| 16–17 | SALE row carrying `median_asking_price` | `202`; job `FAILED`, message says the column must be empty |
| 18–20 | `level=TOWN`, ZIP code `3313`, `size=500` | `400` each |
| 21 | Import as a non-admin user | `403` |
| 22 | Import without a token | `401` |
| 23 | `POST /api/ingestion/import/market-evidence` | `400` pointing to the admin endpoint |
