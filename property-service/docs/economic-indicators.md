# Economic indicators

Story SCRUM-133, part of SCRUM-55 (market, rent and economic evidence). It imports the county economic indicators
that the MVP market outputs read: employment, population and migration (SOW §23 "Economic Data"). Nothing is
calculated here; growth rates are left to the market outputs. Market evidence (SCRUM-131) is stored in its own table
with the same `observation_kind` and geography conventions.

Tables: migration `V50__add_economic_indicators.sql` (V45 to V49 are taken by master and other branches). It needs
V42 (`counties.fips_code`). Flyway is off: apply it by hand in one transaction:

```
psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -1 -f src/main/resources/db/migration/V50__add_economic_indicators.sql
```

It creates `economic_indicators` (the 7 definitions below), `economic_indicator_observations` and
`economic_indicator_loads`; no existing table or row changes. Until it is applied, the imports answer
`409 ... apply migration V50 first`.

## Supported indicators

| Code | Unit | Frequency | Period | Source |
|---|---|---|---|---|
| `LABOR_FORCE` | PERSONS | MONTHLY | the month | BLS LAUS |
| `EMPLOYMENT` | PERSONS | MONTHLY | the month | BLS LAUS |
| `UNEMPLOYMENT_RATE` | PERCENT | MONTHLY | the month | BLS LAUS |
| `POPULATION` | PERSONS | ANNUAL | July 1 | Census population estimates |
| `NET_MIGRATION` | PERSONS | ANNUAL | July 1 to June 30 | Census population estimates |
| `NET_DOMESTIC_MIGRATION` | PERSONS | ANNUAL | July 1 to June 30 | Census population estimates |
| `NET_INTERNATIONAL_MIGRATION` | PERSONS | ANNUAL | July 1 to June 30 | Census population estimates |

- **Geography:** the county (`geographyLevel` `COUNTY`, `geographyCode` = 5-digit FIPS code, `12086` =
  Miami-Dade). The code joins `counties.fips_code`. It is the smallest geography where each series is published
  for every location: finer ones exist only for part of the territory, or not monthly or yearly (table below).

| Finer geography | Published for | Why not imported |
|---|---|---|
| City (BLS LAUS) | labor force, employment, unemployment, cities of 25,000 people or more | partial: places outside those cities (Miami-Dade unincorporated areas) have no value; no city table to link to |
| City (Census PEP) | population only | no migration; no city table to link to |
| Census tract (Census ACS) | employment, unemployment, population | 5-year averages (not monthly or yearly), large margins of error, the Census data API needs a key; no migration |
- **Actual or estimate** (`observationKind`, same meaning as SCRUM-131): `ACTUAL` = observed by the source;
  `ESTIMATE` = modelled or preliminary. Every value of this story is an `ESTIMATE`: BLS publishes county LAUS
  values as model-based estimates (the last month is also preliminary, footnote `P`), and the Census Bureau
  re-estimates every year since 2020 in each new vintage. Neither source publishes an observed county count.
- **Monthly and annual are never mixed:** BLS values are monthly, not seasonally adjusted; the BLS annual
  averages (period `M13`) are not imported. Census values are annual.
- Census migration of 2020 is not imported: it covers April 1 to June 30, 2020 only, not a year.

## Sources

| Source | What is read | Authorization |
|---|---|---|
| BLS LAUS | `https://api.bls.gov/publicAPI/v1/timeseries/data/` (v2 with `BLS_API_KEY`), series `LAUCN{fips}00000000{03,05,06}` | U.S. federal statistics, public domain |
| Census population estimates | `https://www2.census.gov/programs-surveys/popest/datasets/2020-{vintage}/counties/totals/co-est{vintage}-alldata.csv` | U.S. federal statistics, public domain |

`BLS_API_KEY` is optional (free registration at `https://data.bls.gov/registrationEngine/`). Without it the BLS
API takes 25 series per request and 25 requests a day: one Florida import (67 counties × 3 series) uses 9 requests.
With it: 50 series per request and 500 requests a day.

**Not imported**, because no authorized source is available to the service yet:

| Indicator | Why |
|---|---|
| Household income (Census ACS) | the Census data API now requires a key |
| GDP by county (BEA) | the BEA API requires a key |
| Mortgage rate (Freddie Mac PMMS) | national only; its terms of use must be approved first |

## Import (admin)

Both imports need the `admin` realm role. Each one is a provider adapter: it reads the provider (outside any
transaction), then stores everything in one transaction and returns the counts. They are synchronous: a Florida
import takes a few seconds.

```
POST /api/admin/economic-indicators/import/bls-laus?state=FL&fips=12086&fromYear=2025&toYear=2026
POST /api/admin/economic-indicators/import/census-population?state=FL&vintage=2025
```

| Parameter | Import | Rule |
|---|---|---|
| `state` | both | required; code or name of a state with county FIPS codes (`FL`) |
| `fips` | BLS | optional; one county of the state (`12086`); omitted for every county of the state in `counties` |
| `fromYear`, `toYear` | BLS | optional; default the current year and the two before; 1990 or later, `toYear` not after the current year, at most 10 years |
| `vintage` | Census | optional; default the previous year; 2021 to the current year, and published by the Census Bureau |

Response (the real Florida counts of vintage 2025 imported after vintage 2024):

```
{
  "source": "CENSUS_PEP", "stateFips": "12", "counties": 67,
  "sourceUrl": "https://www2.census.gov/.../co-est2025-alldata.csv", "sourceVintage": "2025",
  "periodFrom": "2020-07-01", "periodTo": "2025-07-01",
  "received": 1407, "inserted": 268, "revised": 1070, "unchanged": 69, "olderVintage": 0, "missingValues": 0,
  "sourceMessages": [], "importedAt": "2026-11-25T09:00:00.123456Z"
}
```

`received = inserted + revised + unchanged + olderVintage`. Here: 268 new values (year 2025), and 1,070 of the
1,139 values of 2020 to 2024 re-estimated by the new vintage.

| Count | Meaning |
|---|---|
| `inserted` | a period never stored before |
| `revised` | a stored period whose value or `observationKind` changed |
| `unchanged` | a stored period with the same value; only `lastVerifiedAt` moves |
| `olderVintage` | not written: the stored value comes from a newer vintage |
| `missingValues` | the source gave no value; nothing is stored |

## Revisions without duplicates

There is one row per indicator, county and period (unique key). Importing a period again updates its row:

- the old value goes to `previousValue`, `revisionCount` increases and `revisedAt` is set;
- a row is only replaced by the same or a newer vintage. BLS: the date the API was read. Census: the vintage
  year. Re-importing an older Census vintage changes nothing (`olderVintage`).

Real example, Miami-Dade population on July 1, 2024:

| Import | Stored value | `previousValue` | `revisionCount` |
|---|---|---|---|
| vintage 2024 | 2,838,461 | null | 0 |
| vintage 2025 | 2,812,144 | 2,838,461 | 1 |
| vintage 2025 again | 2,812,144 | 2,838,461 | 1 (unchanged) |
| vintage 2024 again | 2,812,144 | 2,838,461 | 1 (olderVintage) |

BLS publishes the last month as preliminary and revises it the next month; when the value changes, the import
counts it as a revision. Each spring, BLS also revises the previous years (annual benchmarking).

## Missing and empty data

- **A value the source does not give is not stored, never as 0.** Example: BLS published no October 2025 values
  ("Data unavailable due to the 2025 lapse in appropriations"); the import counts them in `missingValues`, and the
  month has no row.
- A series BLS does not have appears in `sourceMessages` ("Series does not exist for Series ...").
- A county or years without values return `status: EMPTY` with an empty list (below).

## Read

Public reference data (U.S. federal statistics): no token, no owner.

`GET /api/economic-indicators`: every indicator with its definition and coverage. Before the first import, the
counts are 0 and the dates null.

```
{ "code": "UNEMPLOYMENT_RATE", "name": "Unemployment rate", "unit": "PERCENT", "frequency": "MONTHLY",
  "geographyLevel": "COUNTY", "source": "BLS_LAUS", "sourceName": "...", "sourceUrl": "https://www.bls.gov/lau/",
  "coverage": { "counties": 67, "observations": 2211, "estimateObservations": 2211,
                "firstPeriod": "2024-01-01", "lastPeriod": "2026-10-31",
                "lastVerifiedAt": "2026-11-25T09:00:00.123456Z", "lastRevisedAt": "2026-11-25T09:00:00.123456Z" } }
```

`GET /api/economic-indicators/{code}/observations?fips=12086&fromYear=2024&toYear=2026`: the values of one county,
oldest first. The years filter on the end of the period (migration 2024 = July 2023 to June 2024).

```
{ "indicatorCode": "POPULATION", "unit": "PERSONS", "frequency": "ANNUAL", "geographyCode": "12086",
  "fromYear": 2024, "toYear": 2026, "status": "OK",
  "observations": [ { "geographyCode": "12086", "geographyName": "Miami-Dade County", "period": "2024",
      "periodStart": "2024-07-01", "periodEnd": "2024-07-01", "value": 2812144.00, "observationKind": "ESTIMATE",
      "sourceSeriesId": "POPESTIMATE2024", "sourceVintage": "2025", "sourceUrl": "https://www2.census.gov/...",
      "previousValue": 2838461.00, "revisionCount": 1, "ingestedAt": "...", "revisedAt": "...",
      "lastVerifiedAt": "..." } ] }
```

## Supported-market coverage

| Indicators | Geography | Periods |
|---|---|---|
| BLS (3) | the counties of the state that have a FIPS code in `counties`; Florida: all 67 after V42 | 1990 onwards, at most 10 years per import; the last month is usually preliminary |
| Census (4) | every county of the state in the Census file; Florida: 67 | population 2020 onwards; migration 2021 onwards |

Only Florida counties have FIPS codes today (V42), so only Florida can be imported. `GET /api/economic-indicators`
shows what is actually stored.

## Errors

| Response | When |
|---|---|
| 400 | `state` missing or unknown; `fips` not 5 digits or not a county of the state; years not numbers or outside the rules; `vintage` outside 2021 to the current year, or not published yet; `fips` missing on the read endpoint |
| 401 | import without a token |
| 403 | import with a token without the `admin` role |
| 404 | unknown indicator code on the read endpoint |
| 409 | migration V50 not applied |
| 502 | the BLS API or the Census file is unavailable, the BLS daily limit is reached, or the answer is not in the expected format; nothing is stored |

## Tests

| Test | What it checks |
|---|---|
| `BlsLausProviderServiceImplTest` | reading a BLS answer: months, `M13` skipped, `-` missing, every value an `ESTIMATE` |
| `CensusPopulationProviderServiceImplTest` | reading a Census file: counties of the state only, 2020 migration skipped, Latin-1 names, empty cells, wrong format |
| `EconomicIndicatorImportServiceImplTest` | import rules and defaults, counts, one import time, a period given twice |
| `EconomicIndicatorServiceImplTest` | read rules: `OK`, `EMPTY`, 400, 404 |
| `EconomicIndicatorSecurityTest` | admin role on the imports (401, 403), public reading, error statuses |
| `EconomicIndicatorDatabaseTest` | V50 and the SQL on PostgreSQL: revision without duplicate, older vintage, filters, constraints. Opt-in, like `MarketEvidenceDatabaseTest`: it runs only with `ENVIRONMENTAL_TEST_DB_URL` set to a local database whose name ends with `_test` (with `DB_USER` and `DB_PASSWORD`), in a schema of its own that it drops |

None of them calls the BLS API or the Census Bureau.

## Manual checks

Postman collection: `docs/postman/SCRUM-133-economic-indicators.postman_collection.json`. Set `baseUrl`,
`admin_token` (Keycloak realm role `admin`) and `user_token` (a user without it), then run folders 1 to 4 in
order (folder 5 is optional). It imports Miami-Dade only from the BLS (2 requests) and Census vintages 2024 then
2025 to show a revision. The data is public and owned by no user, so the access checks are on the admin role,
not on ownership.
