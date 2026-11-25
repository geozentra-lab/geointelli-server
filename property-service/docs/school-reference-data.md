# School reference data (SCRUM-202)

Florida school locations, FLDOE school grades and school attendance zones in the property database, so the next
school ticket can find each property's zoned schools and score their quality. This ticket covers the tables, the
loaders, the loads and the lookup queries. `property_school_analysis`, the school score, its DTOs, services and
endpoints are unchanged, and so are the ML tables `public_school`, `charter_school`, `private_school` and
`elementary_school_boundaries`.

**Rule for every value:** what a source does not give is UNKNOWN and stored as `NULL`. An incomplete or missing
grade is `NULL`, never `0` or a low grade; a component that does not apply to a school (graduation rate of an
elementary school) is `NULL`; an empty `is_virtual` or `sector` is `NULL`, not `false` or `PUBLIC`.

## Data

The three files are built by `schools/school_quality/build_backend_inputs.py` (outside the repository) and staged
under `schools/` in the ingestion staging folder. They are never copied into the repository. The README next to
them documents every column.

| File | Rows | Coverage | Sources |
|---|---|---|---|
| `schools.csv` | 2,114 schools | 31 counties: the 30 from Alachua to Indian River, plus Miami-Dade | district GIS (`schools.zip`, corrected for 2026-27: closures, renames, moved points) and NCES Common Core of Data 2024-25 |
| `school_grades.csv` | 73,965 rows, 3,462 schools | statewide (all districts), 1999-2026 | FLDOE School Grades 2026 (`SchoolGrades26.xlsx`) |
| `school_zones.csv` | 1,292 zones | the same 31 counties | district attendance zone GIS, district maps, NCES SABS 2015-16; for Miami-Dade the M-DCPS elementary, middle and high boundary layers of Miami-Dade County GIS |

Facts found while loading, not all in the README:

- The two new schools without a state id have the keys `nces-HCPS-MORGAN` (Morgan High, Hillsborough) and
  `nces-IRC-VBMS` (Vero Beach Middle, Indian River), and district placeholders in `nces_id`.
- Besides A-F and `I`, the grades use `N` (228 rows, 2000-2003) and `P` (8 rows, 2004-2007): FLDOE codes without a
  letter grade. They are kept in `fldoe_grade`; `letter_grade` is `NULL`.
- There are no grades for 2020 (COVID), and 2021 has 619 schools (participation was optional). The components and
  `pct_total_points` exist only for 2026, the newest year of the FLDOE file.

## Tables (migration `V44__add_school_reference_data.sql`)

| Table | Key | Rows | What it holds |
|---|---|---|---|
| `schools` | `id` (bigserial); `school_key` unique | 2,114 | one row per school, open or closed, with a point |
| `school_grades` | `(state_school_id, school_year)` | 73,965 | one row per school per graded year, statewide |
| `school_attendance_zones` | `id` (bigserial) | 1,292 | one row per zone polygon, linked to its school |

Every load is also recorded in `reference_dataset_loads` (V42): dataset `SCHOOLS`, `SCHOOL_GRADES` or
`SCHOOL_ZONES`, with `fips5` empty because each file covers several counties.

### `schools`

| Column | Notes |
|---|---|
| `id` | stable: a reload updates the row of the same `school_key`, so a later ticket can reference it |
| `school_key` | the source's stable key (`state_school_id`, else `nces-<id>`) |
| `state_school_id` | FLDOE `DD-SSSS` (district + school number), unique when present, the key of `school_grades`; `NULL` for the 2 new schools |
| `nces_id`, `fips5`, `county`, `name` | `fips5` is the 5-digit county FIPS (no foreign key: `counties` holds only the loaded counties) |
| `sector` | `PUBLIC` or `CHARTER`; `NULL` for 4 schools missing from NCES |
| `level` | NCES level `PRE_K`, `ELEMENTARY`, `MIDDLE`, `HIGH`, `COMBINATION`; a K-8 is `ELEMENTARY`, so use the grade range to know what a school teaches; `NULL` for 39 |
| `school_type` | `REGULAR`, `ALTERNATIVE`, `SPECIAL_EDUCATION`, `CAREER_TECHNICAL` |
| `lowest_grade`, `highest_grade` | -1 = Pre-K, 0 = kindergarten, 1-12 |
| `is_virtual`, `is_active` | `is_active = false` for the 48 closed schools: kept for history, never to be assigned or scored |
| `enrollment`, `free_reduced_lunch_pct` | NCES 2024-25 |
| `address`, `city`, `zip`, `in_schools_zip`, `status_note` | as given |
| `geom` | `geometry(Point, 4326)` from `longitude`/`latitude` |
| `source_file`, `source_row`, `loaded_at` | where the row came from |

Indexes: GIST on `geom` and on `geom::geography` (nearest-school and radius searches in metres), `fips5`, the unique
`school_key` and the partial unique `state_school_id`.

### `school_grades`

| Column | Notes |
|---|---|
| `state_school_id`, `school_year` | primary key; `school_year` is the spring of the year (2026 = 2025-26) |
| `district_number`, `school_number`, `school_name` | checked against `state_school_id` at load |
| `letter_grade` | `A`-`F`, `NULL` when FLDOE gave no letter |
| `fldoe_grade` | FLDOE's code as published: `A`-`F`, `I` (incomplete, too few students tested), `N`, `P` |
| `is_baseline` | `true` for 2015 and 2023, baseline years after a standards change |
| `fldoe_school_type`, `is_charter`, `is_title_i`, `alternative_ese_center` | 2026 rows only |
| `grade3_ela_achievement` ... `college_career_acceleration` | the 12 grade components, percent 0-100, 2026 only, `NULL` when a component does not apply |
| `total_points_earned`, `total_components`, `pct_total_points` | `pct_total_points` = points / (components x 100) x 100: FLDOE's continuous quality value, which the letter is cut from |
| `pct_tested`, `pct_econ_disadvantaged` | 2026 only |
| `statewide_percentile` | 0-100, computed after each load (below) |

No foreign key to `schools`: grades cover every district, schools only the 31 counties. Join on
`state_school_id`. Index `(school_year, fldoe_school_type)`.

**Statewide percentile.** After every grades load, for the school years of the file:

```
round(100 * percent_rank() OVER (PARTITION BY school_year, fldoe_school_type ORDER BY pct_total_points))
```

on the rows that have `pct_total_points`, a `fldoe_school_type` and a letter grade. 0 is the lowest score of its type
that year and 100 the highest. Rows without points (every year before 2026, the `I` schools) keep `NULL`, and so
does a school alone in its group (no rank is possible). Compare only within the same type and year: the 2026
cutoffs changed, and high schools have more components than elementary schools.

### `school_attendance_zones`

| Column | Notes |
|---|---|
| `id`, `fips5`, `county` | |
| `level` | `ELEMENTARY`, `MIDDLE`, `HIGH` or `COMBINATION` (Calhoun's Altha PK-12, Gilchrist's Trenton and Bell 6-12: they count for every level their school teaches) |
| `school_key` | the source's link to `schools` |
| `school_id` | resolved from `school_key` at load and after every schools load; `NULL` for the 3 zones without a school (Ave Maria Elementary, Woodham Middle, Shadowlawn Elementary) |
| `zone_school_name`, `school_match`, `source_school_id` | how the source names and matched the school |
| `source`, `source_type`, `zone_year` | `source_type` ranks how far a zone can be trusted, best first: `OFFICIAL`, `OFFICIAL_RECONSTRUCTED`, `COUNTY_WIDE_SINGLE_SCHOOL`, `NCES_SABS_2015_16` (2015-16 boundaries), `UNOFFICIAL` |
| `method_fr`, `note_fr` | how the zone was made, in French as in the source |
| `geom` | `geometry(MultiPolygon, 4326)`, repaired at load: `ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(geom_wkt, 4326)), 3))` |

A school can have several rows at one level (separate parts of its zone, 18 cases), and zones of one level can
overlap (Calhoun), so `(school, level)` is not unique. Two zones belong to closed schools (Niblack Elementary,
Bear Creek Elementary): they stay, and the lookup returns `is_active = false` so the caller can skip them. Indexes:
GIST on `geom`, `(fips5, level)`, `school_id`, `school_key`.

## Applying V44

Flyway is disabled locally; apply the migration by hand to each database, **in one transaction**, after V42:

```
psql "host=... dbname=geozentra sslmode=verify-full sslrootcert=..." -v ON_ERROR_STOP=1 -1 \
  -f src/main/resources/db/migration/V44__add_school_reference_data.sql
```

It only adds the three tables and their indexes; it changes no existing table or row. The application maps no JPA
entity to them (`ddl-auto: validate` is unaffected).

## Loading

Every load is an ingestion job of type `REFERENCE_DATA_IMPORT` (see `ingestion-jobs.md` and
`environmental-reference-data.md` for the job flow, the staging setup and the COPY loader), started with:

```
POST /api/ingestion/import/{dataset}?stagedFile=schools/<file>&sourceState=FL&downloadedOn=yyyy-MM-dd
```

| `dataset` | File | Replaces | Afterwards |
|---|---|---|---|
| `schools` | `schools/schools.csv` | the schools of every county in the file: rows are upserted on `school_key` (ids stay), and the schools of those counties that are no longer in the file are deleted | every zone is re-linked to its school by `school_key` |
| `school-grades` | `schools/school_grades.csv` | every school year in the file | the percentiles of those years are computed |
| `school-zones` | `schools/school_zones.csv` | every county in the file | |

`downloadedOn` is the date the files were built (the README has no download date): **2026-11-22** for the current
files. The files can be loaded in any order: zones loaded before their schools are linked by the schools load.
The whole file is one transaction: a malformed row, a duplicate key, a zone with no area left after repair or an
invalid stored polygon fails the job (`INVALID_ROW` with `file:row`, `LOAD_CHECK_FAILED`, `DATABASE_ERROR`) and
leaves the previous rows as they were.

Refused before a job is queued:

| Status | When |
|---|---|
| 400 | a `county` is given (each file covers several counties), `downloadedOn` missing or in the future, a column the loader reads is missing, the file is empty, its first row is in another state, `school-grades` for a state other than Florida, or a grades file whose first `state_school_id` is not `DD-SSSS` |
| 409 | V44 (or V42) not applied, or a job of the same dataset is already queued or running |

Measured on the local rehearsal (2026-11-22): schools 2 s, grades 4-6 s, zones 18-20 s.

## Refreshing each year

- **New FLDOE grades** (FLDOE publishes `SchoolGradesYY.xlsx` each summer). The new file has the components and
  `pct_total_points` for the new year only; the previous years have only their letter. **Import only the new year**:
  rebuild `school_grades.csv`, keep the rows of the new `school_year`, and import that file. It replaces that year,
  ranks it, and keeps the components and percentiles of the earlier years. Importing the full file would replace
  2026's rows with the letter-only rows of the 2027 file and drop their points.
- **Schools**: when NCES publishes a new Common Core of Data directory or a district changes its schools (openings,
  closures, renames), rebuild `schools.csv` and import it. A school keeps its id; a closed school stays with
  `is_active = false`; a school that disappears from its county's rows is deleted, and its zones keep their row with
  `school_id` `NULL` until a school with the same `school_key` is loaded again.
- **Zones**: districts redraw boundaries before a school year. Rebuild `school_zones.csv` (or a file of the changed
  counties only) and import it: each county in the file is replaced.

Every load adds a row to `reference_dataset_loads`; older rows stay as history.

After a load, refresh the school assignments and school score v2 (SCRUM-203, `school-score-v2.md`): new grades
need `POST /api/property-school-analysis/calculate-all?version=2`; reloaded schools or zones need
`POST /api/property-school-analysis/assign?fips5=…` for each county. A zones reload clears the stored `zone_id` of
the county's assignments, and a schools reload deletes the assignments of the schools it deletes, until the county
is assigned again.

## Data vintage

| Data | Vintage |
|---|---|
| School list and points | district GIS corrected for 2026-27; NCES CCD 2024-25 (enrollment, free/reduced lunch) |
| Grades | FLDOE School Grades 2026; letters back to 1999; components 2026 only |
| Zones | per zone in `zone_year` (mostly 2026-27; 67 zones from NCES SABS 2015-16, `source_type` `NCES_SABS_2015_16`) |

Query (d) below gives the latest load of each dataset.

## Lookup queries for the next ticket

All four take one property. The point is `ST_PointOnSurface` of the property's parcel (inside the polygon even for
L-shaped lots); a property with several parcels (1,645 in Miami-Dade) uses the largest. `parcels` is reached by
`property_id`, which is indexed (`folio` is not).

### (a) Parcel to the zones containing it, with each zone's school and latest grade

```sql
WITH p AS (
    SELECT ST_PointOnSurface(pa.geom) AS pt FROM parcels pa
    WHERE pa.property_id = :property_id ORDER BY ST_Area(pa.geom) DESC LIMIT 1)
SELECT z.id AS zone_id, z.level, z.source_type, z.zone_year, z.school_id, s.name, s.sector, s.is_active,
    g.school_year, g.letter_grade, g.fldoe_grade, g.pct_total_points, g.statewide_percentile
FROM p
JOIN school_attendance_zones z ON ST_Intersects(z.geom, p.pt)
LEFT JOIN schools s ON s.id = z.school_id
LEFT JOIN LATERAL (
    SELECT gr.school_year, gr.letter_grade, gr.fldoe_grade, gr.pct_total_points, gr.statewide_percentile
    FROM school_grades gr WHERE gr.state_school_id = s.state_school_id
    ORDER BY gr.school_year DESC LIMIT 1) g ON true
ORDER BY z.level,
    array_position(ARRAY['OFFICIAL', 'OFFICIAL_RECONSTRUCTED', 'COUNTY_WIDE_SINGLE_SCHOOL', 'NCES_SABS_2015_16',
        'UNOFFICIAL'], z.source_type),
    ST_Area(z.geom);
```

Several zones of one level can contain the point: the first row per level is the best source, then the smallest
zone. `school_id` `NULL` (zone without a school) or `is_active = false` (closed school) means: fall back to the
nearest school (b). The latest grade row can be an `I` (letter `NULL`): it is "not graded by FLDOE", not a bad grade.
`COMBINATION` zones count for every level their school's grade range covers.

Property 556 (9401 SW 97 CT, folio 3050050290190): Kendale Elementary (A, 78 points, percentile 93), Glades Middle
(A, 68, 65), Miami Killian Senior High (B, 61, 28).

```
Sort (actual time=5.727..5.732 rows=3 loops=1)
  -> Nested Loop Left Join (actual time=3.459..5.623 rows=3 loops=1)
     -> Nested Loop (actual time=3.028..4.959 rows=3 loops=1)
        -> Limit -> Sort -> Index Scan using idx_comparable_parcels_property on parcels pa (rows=1)
        -> Index Scan using idx_school_attendance_zones_geom on school_attendance_zones z (actual time=1.826..3.749 rows=3 loops=1)
              Index Cond: (geom && (st_pointonsurface(pa.geom)))
              Filter: st_intersects(geom, (st_pointonsurface(pa.geom)))   Rows Removed by Filter: 2
     -> Index Scan using schools_pkey on schools s (loops=3)
     -> Limit -> Index Scan Backward using school_grades_pkey on school_grades gr (loops=3)
Execution Time: 7.363 ms   (first run; 0.40 ms on average over 1,000 parcels)
```

### (b) Parcel to the nearest active schools of a level and sector

```sql
WITH p AS (
    SELECT ST_PointOnSurface(pa.geom)::geography AS pt FROM parcels pa
    WHERE pa.property_id = :property_id ORDER BY ST_Area(pa.geom) DESC LIMIT 1)
SELECT s.id, s.name, s.level, s.sector, round(ST_Distance(s.geom::geography, p.pt)) AS meters
FROM p CROSS JOIN LATERAL (
    SELECT * FROM schools s
    WHERE s.is_active AND s.level = :level AND s.sector = :sector
    ORDER BY s.geom::geography <-> p.pt
    LIMIT 3) s
ORDER BY meters;
```

A KNN search on the geography index: distances are true metres (EPSG:3857, which the old school SQL used, overstates
them by 11-15 % in Florida). To follow what a school teaches rather than its NCES level (a K-8 is `ELEMENTARY`),
filter on the grade range instead, for example `s.lowest_grade <= 6 AND s.highest_grade >= 8` for middle school.

```
Nested Loop (actual rows=3 loops=1)
  -> Limit -> Sort -> Index Scan using idx_comparable_parcels_property on parcels pa (rows=1)
  -> Limit (actual rows=3 loops=1)
     -> Index Scan using idx_schools_geog on schools s (actual rows=3 loops=1)
           Order By: ((geom)::geography <-> ((st_pointonsurface(pa.geom))::geography))
           Filter: (is_active AND level = 'ELEMENTARY' AND sector = 'PUBLIC')   Rows Removed by Filter: 12
Execution Time: 0.295 ms   (45-86 ms for the first geography query of a new connection; 0.27 ms on average over 1,000 parcels)
```

### (c) Active schools within a radius

```sql
WITH p AS (
    SELECT ST_PointOnSurface(pa.geom)::geography AS pt FROM parcels pa
    WHERE pa.property_id = :property_id ORDER BY ST_Area(pa.geom) DESC LIMIT 1)
SELECT s.id, s.name, s.level, s.sector, round(ST_Distance(s.geom::geography, p.pt)) AS meters
FROM p JOIN schools s ON ST_DWithin(s.geom::geography, p.pt, :radius_meters)
WHERE s.is_active
ORDER BY meters;
```

```
Sort (actual time=0.215..0.217 rows=18 loops=1)
  -> Nested Loop (actual time=0.089..0.205 rows=18 loops=1)
     -> Limit -> Sort -> Index Scan using idx_comparable_parcels_property on parcels pa (rows=1)
     -> Index Scan using idx_schools_geog on schools s (actual time=0.024..0.085 rows=18 loops=1)
           Index Cond: ((geom)::geography && _st_expand((st_pointonsurface(pa.geom))::geography, 3000))
           Filter: (is_active AND st_dwithin(...))   Rows Removed by Filter: 5
Execution Time: 0.564 ms   (radius 3,000 m)
```

### (d) Data vintage

```sql
SELECT DISTINCT ON (dataset) dataset, source, downloaded_on, file, rows_loaded, loaded_at
FROM reference_dataset_loads
WHERE dataset IN ('SCHOOLS', 'SCHOOL_GRADES', 'SCHOOL_ZONES')
ORDER BY dataset, loaded_at DESC, id DESC;

SELECT max(school_year) FILTER (WHERE letter_grade IS NOT NULL) AS latest_graded_year,
       max(school_year) FILTER (WHERE pct_total_points IS NOT NULL) AS latest_year_with_points
FROM school_grades;
```

Execution 0.28 ms (a sequential scan of a few rows). Show it next to a school result, with each zone's `zone_year`.

## Verification log

### 2026-11-22, local rehearsal

Database: `geozentra_scrum201_e2e_test`, a local scratch copy of the property database (582,589 Miami-Dade
properties, 590,895 parcels), with V41, V43 and V44 applied by hand. Loaded through the import endpoint with
`downloadedOn=2026-11-22`. Neither the application's local database nor RDS was written.

| Check | Expected | Result |
|---|---|---|
| Schools | 2,114 | 2,114 (31 counties; 2,066 active, 48 closed; 1,676 public, 434 charter, 4 unknown) |
| Grade rows | 73,965 (3,462 schools) | 73,965 (3,462) |
| Zones | 1,292 | 1,292 |
| Zone geometry | valid | 1,292 valid, 0 empty, all `MULTIPOLYGON` |
| Spatial indexes | exist | `idx_schools_geom`, `idx_schools_geog`, `idx_school_attendance_zones_geom` (GIST) |
| Active, regular, in-person E/M/H/C schools; with a 2026 grade (README) | 1,728; 1,701 | 1,728; 1,701 |
| Zones without a school | 3 | 3: Ave Maria Elementary (Collier), Woodham Middle (Escambia), Shadowlawn Elementary (Miami-Dade) |
| Zones of closed schools | 2 | 2: Niblack Elementary, Bear Creek Elementary |
| Grades without a letter | I 22, N 228, P 8 | 258 rows with `letter_grade` `NULL`, codes kept in `fldoe_grade` |
| 2026 letters | A 1,741, B 884, C 793, D 24, F 6, I 14 | same (I as `NULL`) |
| Percentiles | every row with points | 3,448 of 3,448, none outside 2026, none against the order of `pct_total_points` |
| Re-import of the three files | no duplicates | identical school ids, zone links, grades and percentiles after a second and a third load |
| Re-import of one county or one year | that scope only | covered by `SchoolReferenceDataDatabaseTest` (fixture) |
| Lookup (a) for one parcel | < 100 ms | 7.4 ms (first run), 0.40 ms average, 15.9 ms max over 1,000 random parcels |
| Lookup (b) | < 100 ms | 0.3 ms warm, 45-86 ms for the first geography query of a connection; 0.27 ms average, 1.6 ms max over 1,000 |
| Lookups (c), (d) | < 100 ms | 0.56 ms, 0.28 ms |

Zones per county (elementary / middle / high / combination): Alachua 20/9/6/0, Baker 3/1/1/0, Bay 21/9/6/0, Bradford
3/1/1/0, Brevard 51/15/14/0, Broward 128/39/27/0, Calhoun 2/1/1/1, Charlotte 10/4/3/0, Citrus 11/4/3/0, Clay 27/7/7/0,
Collier 34/11/9/0, Columbia 7/2/2/0, DeSoto 3/1/1/0, Dixie 1/1/1/0, Duval 82/18/13/0, Escambia 31/8/6/0, Flagler
5/2/2/0, Franklin 1/1/1/0, Gadsden 5/3/1/0, Gilchrist 2/0/0/2, Glades 2/2/1/0, Gulf 2/2/2/0, Hamilton 1/1/1/0, Hardee
5/1/1/0, Hendry 4/2/2/0, Hernando 11/6/5/0, Highlands 9/4/3/0, Hillsborough 131/37/28/0, Holmes 4/4/4/0, Indian River
8/4/2/0, Miami-Dade 224/53/34/0.

2026 statewide percentile per FLDOE school type (schools, quartiles):

| Type | Schools | Min | P25 | Median | P75 | Max | Points range |
|---|---|---|---|---|---|---|---|
| Combination | 590 | 0 | 22 | 50 | 73 | 100 | 25-95 |
| Elementary | 1,789 | 0 | 24 | 48 | 75 | 100 | 14-96 |
| High | 508 | 0 | 25 | 45 | 72 | 99 | 38-100 |
| Middle | 561 | 0 | 24 | 49 | 73 | 100 | 39-93 |

Miami-Dade coverage, all 581,755 properties with a parcel, one set-based query (47 s): 578,755 lie in an elementary,
a middle and a high zone; 4,047 are in Shadowlawn's zone (no school: fall back to the nearest); 2,808 lie in no zone.
Those 2,808 are vacant government land, unclassified acreage and submerged land in the Everglades (1,559 + 980 + 81
of them; 2,796 have no ZIP), 11 km from the nearest zone at the median: no residential parcel is outside the zones.

### Spot check against the M-DCPS school locator

Ten single-family properties, one per municipality, with the zoned school of each level (best source first). To be
checked by hand on the M-DCPS school locator (an interactive site); record the outcome in the last column.

| Folio | Address | Elementary | Middle | High | Locator |
|---|---|---|---|---|---|
| 0341070186071 | 1400 Madrid St, Coral Gables 33134 | Coral Gables Preparatory Academy | Ponce de Leon Middle | Coral Gables Senior High | to check |
| 2822100790028 | 1500 Island Blvd, Aventura 33160 | Aventura Waterways K-8 Center | Highland Oaks Middle | Dr. Michael M. Krop Senior High | to check |
| 3421040420230 | 19203 NW 33 Ct, Miami Gardens 33056 | Brentwood Elementary | Carol City Middle | Miami Carol City Senior High | to check |
| 3660090300180 | 20627 SW 91 Ct, Cutler Bay 33189 | Dr. Edward L. Whigham Elementary | Cutler Bay Middle | Miami Southridge Senior High | to check |
| 1079280170630 | 2458 SE 26 Ter, Homestead 33035 | Gateway Environmental K-8 Learning Center | Homestead Middle | Homestead Senior High | to check |
| 0232270080040 | 2940 Alton Rd, Miami Beach 33140 | North Beach Elementary | Miami Beach Nautilus Middle | Miami Beach Senior High | to check |
| 0420280470130 | 3299 W 76 St, Hialeah 33018 | Ernest R. Graham K-8 Academy | Hialeah Gardens Middle | Hialeah Gardens Senior High | to check |
| 0131270100951 | 3424 NW 19 Ave, Miami 33142 | Kelsey L. Pharr Elementary | Georgia Jones Ayers Middle | Miami Jackson Senior High | to check |
| 3041310191976 | 7700 SW 53 Ct, unincorporated 33143 | Sunset Elementary | Ponce de Leon Middle | Coral Gables Senior High | to check |
| 3530290821240 | 9817 NW 31 St, Doral 33172 | John I. Smith K-8 Center | Ruben Dario Middle | J.C. Bermudez Doral Senior High | to check |

The Miami-Dade zones are the M-DCPS boundary layers themselves, so a difference would mean a boundary changed after
the layers were downloaded, or a point that falls on a boundary.

## Files

| Part | File |
|---|---|
| Migration | `db/migration/V44__add_school_reference_data.sql` |
| Datasets | `enums/IngestionDataset` (`SCHOOLS`, `SCHOOL_GRADES`, `SCHOOL_ZONES`) |
| Row checks | `service/impl/ReferenceDataRows` (`Schools`, `SchoolGrades`, `SchoolZones`) |
| SQL | `service/impl/ReferenceDataSql` (replacement, upsert, zone repair and linking, percentile) |
| Load | `service/impl/ReferenceDataImportServiceImpl` (the SCRUM-201 COPY loader) |
| Launch checks | `service/impl/IngestionLaunchServiceImpl`, `service/impl/ReferenceDataScopeServiceImpl.hasTable` |
| Tests | `SchoolReferenceDataRowsTest` (rows and SQL), `IngestionLaunchServiceImplTest` (launch checks), `SchoolReferenceDataDatabaseTest` (PostGIS, opt-in with `ENVIRONMENTAL_TEST_DB_URL`) |
| Fixture | `src/test/resources/school-data` |
| Postman | `docs/postman/SCRUM-202-school-reference-data.postman_collection.json` |
