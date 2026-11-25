# Environmental reference data (SCRUM-201)

Florida environmental reference data in the property database, read by the property risk context (SCRUM-127):
FEMA flood zones, census tracts, the FEMA National Risk Index (NRI) per tract and NOAA storm events. This ticket
covers the tables, the importers, the loads and the lookup queries; the per-property results, scores, DTOs and
endpoints belong to SCRUM-127.

**Rule for every value:** what a source does not give is UNKNOWN and stored as `NULL`: an empty cell, FEMA's
`-9999`/`-99999` "none", the NRI ratings `No Rating`, `Insufficient Data`, `Not Applicable` (and the `0` score
FEMA writes next to `No Rating`), an unrecognised NOAA time zone. It is never replaced by a default or by a
"safe"/"low risk" value. A point that lies in no flood zone has no flood evidence; it is not "zone X".

## Tables (migration `V42__add_environmental_reference_data.sql`)

| Table | Key | Rows (Florida) | What it holds |
|---|---|---|---|
| `flood_zones` | `id` (surrogate) | 594,095 source polygons, cut into parts | FEMA NFHL layer 28 flood hazard zones |
| `census_tracts` | `tract_fips` | 5,160 | TIGER/Line 2024 tract polygons (2020 tracts) |
| `nri_tract_risk` | `tract_fips` | 5,114 | FEMA NRI v1.20 (December 2025) per tract |
| `storm_events` | `event_id` | 41,437 | NOAA NCEI Storm Events 1950-2026 |
| `reference_dataset_loads` | `id` | one per load | data vintage: source, download date, file, rows |
| `counties.fips_code` | unique | 67 | 5-digit county FIPS that links counties to the files |

### `flood_zones`

One row per **part** of a FEMA zone polygon. Each polygon is repaired and cut before it is stored:
`ST_Multi(ST_Subdivide(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(geom_wkt, 4326)), 3), 256))`.
Parts of at most 256 vertices keep point lookups fast (one Citrus polygon has over a million vertices). The parts
of one FEMA polygon share `(source_file, source_row)`; regroup them with `ST_Union(geom) GROUP BY source_file,
source_row`. `FLD_AR_ID` is not unique in FEMA's data (22 rows: 21 in Hardee, 1 in Collier repeat an ID with a
different polygon), so every source row is kept under a surrogate `id`. A polygon with no area left after repair
keeps one row with `geom` NULL, so no source row is lost.

| Column | Source | Notes |
|---|---|---|
| `source_file`, `source_row` | file name, 1-based data row | identify the FEMA polygon |
| `fips5` | `fips5` | county; must be the county of the import |
| `dfirm_id`, `fld_ar_id` | `DFIRM_ID`, `FLD_AR_ID` | |
| `fld_zone` | `FLD_ZONE` | A, AE, AH, AO, VE (high risk), X, D (undetermined), OPEN WATER |
| `zone_subty` | `ZONE_SUBTY` | `0.2 PCT ANNUAL CHANCE FLOOD HAZARD...` = moderate (500-year) X zone |
| `sfha` | `SFHA_TF` | `T` = inside the Special Flood Hazard Area (high risk); NULL if empty |
| `static_bfe`, `depth`, `velocity` | `STATIC_BFE`, `DEPTH`, `VELOCITY` | `-9999`/`-99999` = none = NULL |
| `len_unit`, `vel_unit`, `v_datum` | `LEN_UNIT`, `VEL_UNIT`, `V_DATUM` | units/datum of the numbers |
| `dual_zone`, `study_typ`, `version_id`, `source_cit` | same | `SOURCE_CIT` = FEMA study id |
| `geom` | `geom_wkt` | `geometry(MultiPolygon, 4326)`, GIST `idx_flood_zones_geom` |

Index `idx_flood_zones_fips5 (fips5, source_row)` serves the county replacement and the regrouping.

### `census_tracts`

`tract_fips` (11 digits: state 2 + county 3 + tract 6), `fips5`, `name`, `land_area_m2`, `water_area_m2`,
`geom geometry(MultiPolygon, 4326)` (repaired with `ST_MakeValid`, GIST `idx_census_tracts_geom`). 46 tracts are
water only (`land_area_m2` 0); they have no NRI row.

### `nri_tract_risk`

`tract_fips`, `fips5`, `nri_version` (`NRI_VER`, "December 2025"), `risk_score`/`risk_rating` (overall) and
`<hazard>_score`/`<hazard>_rating` for HRCN hurricane, IFLD inland flooding, CFLD coastal flooding, TRND tornado,
WFIR wildfire, SWND strong wind, LTNG lightning, HWAV heat wave. Scores are FEMA's 0-100 percentiles, ratings
FEMA's labels (`Very Low` ... `Very High`). `raw jsonb` keeps every one of the ~470 source columns as text, so any
other NRI value (expected annual loss, social vulnerability, resilience, other hazards) is available without a
reload. Not every TIGER tract has an NRI row (see the counts below) and no foreign key ties the two tables, so
either can be refreshed alone.

| Source rating | Stored rating | Stored score |
|---|---|---|
| `Very Low` ... `Very High` | as given | as given |
| `No Rating` (score `0.000000`) | NULL | NULL — the 0 is not a low score |
| `Insufficient Data`, `Not Applicable` (score empty) | NULL | NULL |
| empty (3 tracts, overall risk only) | NULL | the given score |

### `storm_events`

`event_id`, `episode_id`, `state_fips`, `fips5` (county rows; NULL for zone rows), `cz_type` (C county, Z forecast
zone, M marine zone), `cz_fips`, `cz_name`, `event_type`, `begin_at`, `end_at`, `cz_timezone`, injuries and deaths
(direct, indirect), `damage_property_usd`, `damage_crops_usd`, `magnitude` (wind knots or hail inches),
`magnitude_type`, `tor_f_scale`, `flood_cause`, `geom geometry(Point, 4326)` from `BEGIN_LAT`/`BEGIN_LON` (NULL when
NOAA has no coordinates). Indexes: GIST on `geom` and on `geom::geography` (radius searches in metres), `(fips5,
begin_at)`, `state_fips`.

- Times are built from `BEGIN_YEARMONTH`/`BEGIN_DAY`/`BEGIN_TIME` (HHmm) and the offset in `CZ_TIMEZONE`
  (`EST-5` = UTC-5, `CST` = UTC-6, ...); `BEGIN_DATE_TIME` has two-digit years and is not used. `2400` is midnight
  at the end of the day. An unrecognised zone gives NULL.
- Damage text is converted to dollars: `25.00K` = 25,000, `1.5M` = 1,500,000, `B` = billion. Empty = not reported
  (NULL); `0`/`0.00K` is a recorded zero. A unit without an amount (`K`, once: event 5163217, a 2000 flash flood)
  is NULL.
- 88 county rows have no valid county code (`fips5` 12000, 12066, 12139; CZ_FIPS 0 in 1950-1979 and a few 1990s
  zone lists); they are kept as given.
- Coverage caveat from NOAA: 1950-1954 tornado only, 1955-1995 tornado, thunderstorm wind and hail only, all 48
  event types from 1996. A count of zero means nothing was recorded, not that nothing happened.

### `reference_dataset_loads`

One row per imported file: `dataset`, `source` (publisher and URL), `downloaded_on`, `file`, `fips5` (flood zones),
`rows_read`, `rows_loaded`, `job_id`, `loaded_at`. The latest row per dataset (per county for flood zones) is the
data vintage to show next to a risk result (query d).

### `counties.fips_code`

V42 adds `counties.fips_code CHAR(5)` (unique, 5 digits) and fills it for every Florida county by name. Names are
compared without case, spaces or punctuation and with `Saint`/`St.` alike, so `St. Johns`, `Saint Johns`, `DeSoto`,
`De Soto`, `Miami-Dade` and `Miami Dade` all match. The migration **fails** (and rolls back) when a Florida county
row has no match, listing the names.

## Applying V42

Flyway is disabled locally; apply the migration by hand to each database, **in one transaction**:

```
psql "host=... dbname=geozentra sslmode=verify-full sslrootcert=..." -v ON_ERROR_STOP=1 -1 \
  -f src/main/resources/db/migration/V42__add_environmental_reference_data.sql
```

It needs PostGIS (already installed: `parcels.geom`). It only adds tables and one column; it changes no existing
row except `counties.fips_code`. The application does not map the new tables to JPA entities (`ddl-auto: validate`
is unaffected).

## Loading

Every load is an ingestion job (see `ingestion-jobs.md`) of type `REFERENCE_DATA_IMPORT`, started with the existing
import endpoint and visible in `GET /api/admin/import-jobs?type=REFERENCE_DATA_IMPORT`:

```
POST /api/ingestion/import/{dataset}?stagedFile=...&sourceState=FL&downloadedOn=yyyy-MM-dd[&county=...]
```

| `dataset` | File (relative to the staging directory) | `county` | Replaces |
|---|---|---|---|
| `census-tracts` | `florida_location/census_tracts/florida_all.csv` | omitted | the state's tracts |
| `nri-tracts` | `florida_location/nri/tracts/florida_all.csv` | omitted | the state's NRI rows |
| `flood-zones` | `fema_flood_zones/<fips5>_<county>.csv` | the county of the file | that county's zones |
| `storm-events` | `florida_location/noaa_storm_events/florida_all.csv` | omitted | the state's events |

`downloadedOn` is the date in the source folder's README (`Downloaded:`). Rejected with 400 before anything is
stored: a missing or future `downloadedOn`, a missing county for flood zones or a county for the statewide
datasets, an unknown county or state, a file missing a column the importer reads, an empty file, and a file whose
first row belongs to another county (flood zones) or state. 409 when V42 is missing or a statewide job of the same
dataset is already queued or running.

How one job runs:

1. The worker counts the file's rows (`total`) and records `LOADING`.
2. In **one transaction**: an advisory lock on the scope (two loads of one county cannot interleave); a temporary
   staging table; every row is normalised in Java (the rules above; a malformed row fails the file with its row
   number) and streamed into the staging table with PgJDBC `COPY ... FROM STDIN WITH (FORMAT csv, HEADER)`; the
   scope's current rows are deleted; `INSERT ... SELECT` moves the rows into the target table (repairing and cutting
   flood polygons); the loader checks that every source row was stored and every polygon is valid; the load is
   recorded in `reference_dataset_loads`; and the job's counters and checkpoint are written with the same commit.
3. Any failure rolls the whole file back: the county or state keeps its previous rows. The job is `FAILED` with
   `INVALID_ROW` (`file:row`), `DATABASE_ERROR`, `LOAD_CHECK_FAILED`, `INPUT_UNREADABLE` or `SCOPE_UNAVAILABLE`.
   A retry (`POST /api/admin/import-jobs/{id}/retry`) loads the same stored file again.

Rows never pass through JPA entities, and the CSV parser has no field size limit (the longest row is 32 MB).

### Running a load locally

`yarn start:local` loads `.env` (dotenv-cli) and docker-compose reads it as `env_file`; a plain `mvn
spring-boot:run` or an IDE launch does not, so set the variables there in the shell or the run configuration.
`.env.example` has the lines to copy:

| Variable | Value |
|---|---|
| `INGESTION_STAGING_DIRECTORY` | the data root, e.g. `C:\Users\...\geointelli.ai\florida location` (quote it: the path has spaces) |
| `INGESTION_INPUT_STORAGE` | `local` for one local instance (the default `s3` copies every staged file to S3 first) |
| `INGESTION_INPUT_DIRECTORY` | where job inputs are copied (deleted when the job completes); needs room for the largest file (Polk, 721 MB) |

With local inputs a queued job must be claimed within `INGESTION_JOBS_QUEUE_DEADLINE_SECONDS` (600 s) and one job
runs at a time, so start the county files one after the other (wait for `COMPLETED`), or raise the deadline. For
Docker, bind-mount the data root and point `INGESTION_STAGING_DIRECTORY` at the mount.

The source files are never copied into the repository.

## Refreshing

- **Flood zones** change with every effective FEMA map revision (LOMR). Re-download a county file and import it:
  the county's rows are replaced in one transaction, without duplicates; the other counties are untouched.
- **NRI**: FEMA publishes versions (v1.20, December 2025). Import the new tract file; `nri_version` shows which.
- **Census tracts**: TIGER is yearly but tracts change with each decennial census (2020 tracts until 2030).
- **Storm events**: NOAA updates the current year monthly; import the new statewide file.

Every refresh adds a row to `reference_dataset_loads`; older rows stay as history.

## Lookup queries for SCRUM-127

_To be written and measured on RDS in step 6._

## Verification log

### Full Florida load on RDS (dev), 2026-11-24

Run by SCRUM-127 through `POST /api/ingestion/import/{dataset}` from a laptop (`INGESTION_INPUT_STORAGE=local`,
`INGESTION_STAGING_DIRECTORY` = the data root), one job at a time, after V42 had been applied by its author as
`postgres`. Every job ended `COMPLETED` with `completed = total` and `failed = 0`; every row of
`reference_dataset_loads` has `rows_read = rows_loaded`.

| Dataset | File(s) | Downloaded | Rows read / loaded | Time | Checks on RDS |
|---|---|---|---|---|---|
| `census-tracts` | `census_tracts/florida_all.csv` | 2026-11-12 | 5,160 / 5,160 | 39 s | 5,160 distinct `tract_fips`, 67 counties, 0 NULL or invalid geometries, SRID 4326 |
| `nri-tracts` | `nri/tracts/florida_all.csv` | 2026-11-11 | 5,114 / 5,114 | 25 s | version "December 2025"; "No Rating" stored as NULL (3 overall, 2,413 coastal flood, 1,007 wildfire); every NRI row joins a tract; the 46 tracts without an NRI row are water-only tracts (code 9900) |
| `storm-events` | `noaa_storm_events/florida_all.csv` | 2026-11-11 | 41,437 / 41,437 | 14 s | 1950-2026; 28,905 with a point; `fips5` NULL for the 7,901 forecast-zone (`cz_type` Z) and 1 marine (`cz_type` M) events, which NOAA assigns to no county |
| `flood-zones` | 67 county files, 7.9 GB | 2026-11-14 | 594,095 / 594,095 polygons | 7 s (Baker) to 521 s (Citrus) per county, about 2.5 h in total | 3,295,775 parts, max 256 vertices, 0 invalid, 11 parts with `geom` NULL (polygons with no area after repair), `sfha` never NULL, `static_bfe` NULL where FEMA gives none; parts per county match `rows_loaded` of the load record |

Table sizes after the load: `flood_zones` 6,046 MB, `nri_tract_risk` 42 MB, `census_tracts` 22 MB, `storm_events` 17 MB.

Point lookup checked on RDS: three Baker properties resolve through `ST_PointOnSurface(parcels.geom)` to
"X / AREA OF MINIMAL FLOOD HAZARD, sfha=false, bfe=NULL". A point can fall in two parts of the same FEMA polygon
(parts share `source_file`, `source_row`), so a lookup must group by those two columns.

Invalid input and missing data (collection folders 2 and 3, plus 1.6) against the same app and RDS: 12 of 12 as
specified, nothing stored. Note that 3.1 (staged file that does not exist) answers 400, as the collection expects.

Two defects found and fixed during the load:

- **V46** `fix_saint_county_fips_codes` (SCRUM-127 branch): V42 left `counties.fips_code` NULL for "SaintJohns"
  and "SaintLucie" (names stored without a space; the V42 regex requires one). The V42 guard should have rolled the
  migration back, so V42 was evidently not run with `-1 -v ON_ERROR_STOP=1`. V42 itself is left as it is (it is
  already applied); V46 sets the two codes with the same rule and the space made optional (`\s+` -> `\s*`).
- **V47** `widen_flood_zones_study_typ` (on master, #236, and integration-testing, #235):
  `flood_zones.study_typ VARCHAR(20)` is too short for FEMA `STUDY_TYP` values up to 28 characters ("SFHAs WITH
  MEDIUM FLOOD RISK"); 43 county files failed with "value too long for type character varying(20)". Widened to
  VARCHAR(50) on RDS by the table owner; every other text column fits its V42 size (measured over all 67 files).

Operational note: the app polls queued ingestion jobs only when `alerts.enabled=true` (scheduling is enabled in
`AlertSchedulingConfig`); otherwise a job runs only if its own submission finds the worker idle, so county files
must be submitted strictly one after the other.
