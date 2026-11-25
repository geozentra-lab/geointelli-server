# School assignment and school score v2 (SCRUM-203)

Each property with a parcel gets the public school it is zoned for at each level (elementary, middle, high). Its
school score v2 rates those schools by their FLDOE quality, their distance and the better schools nearby. Both are
computed from the school reference data of SCRUM-202 (`school-reference-data.md`) and stored in two new tables. The
school context and the bulk school analysis return them next to the v1 fields.

`property_school_analysis` is not changed: no column, no row, and its v1 score is computed as before. Its 400,896
rows are the sales the ML model trains on, so adding every property to it could change what the ML pipeline reads.
The old tables `public_school`, `charter_school`, `private_school` and `elementary_school_boundaries` are not touched
either.

**Rule for every value:** what cannot be known is `NULL`, never `0`, and never guessed. A school without an FLDOE
grade (`I`, `N`, `P`, or not listed) has no quality value, and that never counts as a low grade. A property in an
attendance zone whose school is not in the school data gets no school for that level, not another school.

## Tables (migration `V49__add_property_school_assignments_and_scores.sql`)

### `property_school_assignments`

One row per property and level. A level with no school at all has no row.

| Column | Meaning |
|---|---|
| `property_id` | the property (`properties.id`; deleted with it) |
| `level` | `ELEMENTARY`, `MIDDLE` or `HIGH` |
| `school_id` | the assigned school (`schools.id`); `NULL` for `UNMATCHED_ZONE` only |
| `method` | `ZONE`: an attendance zone of the school contains the property. `UNMATCHED_ZONE`: the property is in a zone whose school is not in the school data, so the level has no school and is left out of the score. `NEAREST`: no zone contains the property, so this is the nearest zoned public school of the county |
| `zone_id` | the zone (`school_attendance_zones.id`); `NULL` for `NEAREST`, and after a zones reload (see below) |
| `zone_source_type` | that zone's `source_type` (`OFFICIAL` … `UNOFFICIAL`); `NULL` for `NEAREST`. Kept here so a zones reload does not lose it |
| `distance_m` | metres on the spheroid (`geography`) from the property's point to the school; `NULL` for `UNMATCHED_ZONE` |
| `assigned_at` | when this row last changed (a run that finds the same school, zone and distance leaves the row as it is) |

Primary key `(property_id, level)`. Indexes on `school_id` and `zone_id`, which the foreign keys use when a reload
deletes schools or zones. Checks: only an `UNMATCHED_ZONE` row has no school, and then no distance; a `NEAREST` row
has no zone and no zone source type, the other rows have a source type; the distance is not negative.

### `property_school_scores`

One row per assigned property.

| Column | Meaning |
|---|---|
| `property_id` | the property (primary key; deleted with it) |
| `quality_score`, `access_score`, `choice_score` | the three parts, 0-100, `NULL` when unknown |
| `school_score` | school score v2, 0-100, over the known parts only; `NULL` when no level has an assigned school (the choice part alone is not a school score) |
| `label` | `Excellent` (80+), `Good` (60-79), `Fair` (40-59), `Limited` (under 40), from the score rounded to a whole number as the API shows it |
| `grade_year` | the FLDOE school year (spring of the year) the quality and choice parts use |
| `score_version` | `2` |
| `computed_at` | when this row last changed |

### Reloads of the SCRUM-202 data

The foreign keys never block a reload:

- **Zones reload.** Every zone of the county is replaced, with new ids, and `zone_id ON DELETE SET NULL` clears the
  link. The row keeps its school, method and `zone_source_type`. Assign the county again to link the new zones
  (on Miami-Dade every `ZONE` row is updated).
- **Schools reload.** A school missing from the new file is deleted, and `school_id ON DELETE CASCADE` deletes its
  assignments. Assign the county again so those properties get their new school.
- **Grades reload.** Nothing is deleted. Run score v2 again (`calculate-all?version=2`) so the scores use the new
  year.

## Applying V49

Apply by hand, in one transaction, after V42 and V44:

```
psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -1 -f src/main/resources/db/migration/V49__add_property_school_assignments_and_scores.sql
```

It only creates the two tables and their indexes, so it does not lock any existing table for long. V45 to V48 are
taken by other branches not on master yet: V45 SCRUM-118 (listing sources), V46 SCRUM-127 (Saint county names), V47
the `flood_zones.study_typ` widening, V48 kept for SCRUM-131 (market evidence). So this migration is V49. It needs
none of them; only V42 and V44.

The application does not need V49 to start (the tables are read and written with JDBC, not JPA). Without it, the
endpoints below answer `409 ... apply migration V49 first`, and the school context returns the v1 fields only.

## Assignment rules

For each property of the county with a parcel:

1. **Point.** `ST_PointOnSurface` of its largest parcel, a point that is always inside the parcel.
2. **Levels a school teaches.** A school teaches a level when it offers at least two of the level's grades
   (elementary K-5, middle 6-8, high 9-12). So a K-8 center is elementary and middle, a 6-12 school middle and high,
   a 5-8 school only middle, an 8-12 school only high. A school without a grade range falls back to its NCES level,
   and a `COMBINATION` school teaches every level.
3. **Zone (`ZONE` or `UNMATCHED_ZONE`).** The zones of the county of that level, or `COMBINATION` zones (Calhoun,
   Gilchrist): those of an active school that teaches the level, and those without a school. If several contain the
   point: the best `source_type` first (`OFFICIAL`, `OFFICIAL_RECONSTRUCTED`, `COUNTY_WIDE_SINGLE_SCHOOL`,
   `NCES_SABS_2015_16`, `UNOFFICIAL`), then the smaller zone, then the zone id. When the chosen zone has a school,
   that is the assigned school (`ZONE`). When it has none (`UNMATCHED_ZONE`), the zone still says the property belongs
   to a school, one the school data does not have (Shadowlawn Elementary in Miami-Dade, ID `13-4961`, never graded by
   FLDOE and not in NCES 2024-25), so the level gets no school rather than a guessed one. A closed school's zone
   (`is_active = false`) is out of date and skipped (2 zones, in Collier and Columbia).
4. **Nearest (`NEAREST`).** When no zone contains the point for the level: the nearest active, regular, in-person
   public school of the county that teaches the level and has an attendance zone of that level. Magnet and other
   schools without a zone are never assigned (Miami-Dade has 29 such active public high schools). If the county has
   no zone of that level at all, any such public school qualifies. If none exists, the level has no row.
5. **Write.** The new rows are compared with the stored rows of the county's properties. Only changed rows are
   updated, missing rows inserted, and rows that no longer exist (a lost parcel, a level with no school any more)
   deleted, all in one transaction. A second run on unchanged data writes nothing.

The county statements are in `service/impl/SchoolScoringSql`. They put the points and the candidate zones in
temporary tables and match zones to points through a GIST index on the points, so each zone polygon is prepared once
for all its points.

## School score v2

Weighted average with `WeightedScore.average`: an unknown part is skipped and the other weights are rescaled, so a
missing value never counts as 0.

```
school score = 0.60 × quality + 0.25 × access + 0.15 × choice
each part    = 0.40 × elementary + 0.30 × middle + 0.30 × high
```

- **Quality**: the assigned school's FLDOE `statewide_percentile` for the grade year. The percentile ranks
  `pct_total_points` among the schools of the same FLDOE type (Elementary, Middle, High, Combination) and year, so a
  K-8 center is ranked among combination schools. It is unknown when the school has no grade that year.
- **Access**: distance to the assigned school: 100 within 1 mile, 75 within 2, 50 within 5, 25 within 10, else 0. A
  `NEAREST` school 12 miles away gives a known 0, not an unknown value.
- **Choice**: the best `statewide_percentile` among the other schools within 3 miles that teach the level: active,
  in-person, regular, public or charter, in any county. These are the schools a family can apply to instead of its
  zoned school (charter lottery, magnet application, controlled open enrollment where a school has seats); none is
  guaranteed, which is why choice weighs 15%. The assigned school does not count. With no such school within 3 miles
  the choice is a known 0; when there are some but none is graded it is unknown. The 3 miles are great circles on
  the mean-radius sphere, the same as PostGIS `ST_Distance(geography, geography, false)`. The search runs in memory
  (`SchoolChoiceIndex`, a 0.1 degree grid), because the same radius search in SQL took about 6 minutes for
  Miami-Dade.
- **Why 3 miles.** On 10,000 Miami-Dade properties, the choice part per level (average / spread / no school in
  range):

  | Level | 2 miles | 3 miles | 5 miles |
  |---|---|---|---|
  | Elementary | 81 / 24 / 5% | 89 / 20 / 4% | 95 / 15 / 2% |
  | Middle | 76 / 29 / 7% | 88 / 22 / 4% | 95 / 16 / 2% |
  | High | 65 / 38 / 21% | 85 / 26 / 6% | 94 / 17 / 3% |

  Within 5 miles a property sees about 42 elementary, 26 middle and 14 high schools, so almost every one has a
  top-percentile school and choice says nothing. Within 2 miles a fifth of the properties have no high school at
  all and get 0, too harsh where high schools are far apart.
- **A level without a school** (`UNMATCHED_ZONE`, or no row) has no quality and no access value, so it is left out of
  those parts and the other levels' weights are rescaled. Its choice part still counts. A property without any
  assigned school has no score at all.
- **Grade year**: the newest `school_year` with statewide percentiles (2026). It is stored in `grade_year`, and the
  API shows each assigned school's grade of that year.
- Parts and score are stored with 2 decimals, and the label comes from the stored score rounded to a whole number,
  so the label always matches the number the API shows.

Example, property 556: elementary Kendale (A, percentile 93, 1.5 km), middle Glades (A, 65, 3.1 km), high Miami
Killian (B, 28, 1.3 km).

- quality 0.4×93 + 0.3×65 + 0.3×28 = 65.1
- access 0.4×100 + 0.3×75 + 0.3×100 = 92.5
- choice 99: the best other school of each level within 3 miles is in the 99th percentile
- score 0.6×65.1 + 0.25×92.5 + 0.15×99 = 77.0, Good

Example of a zone without a school, property 205322 (298 NW 48 St, in the Shadowlawn zone): elementary
`UNMATCHED_ZONE` "Shadowlawn Elementary", no school; middle Jose de Diego (B, percentile 33, 1.5 km) and high Miami
Edison (C, 15, 1.4 km), both zoned.

- quality (0.3×33 + 0.3×15) / 0.6 = 24
- access 100
- choice 99
- score 0.6×24 + 0.25×100 + 0.15×99 = 54.3, Fair. Before this rule, the nearest elementary school (Toussaint
  L'Ouverture) was scored in its place.

## Running it

Both operations are background jobs of the ingestion job framework (SCRUM-188, job type `SCHOOL_SCORING`). Both need
the admin realm role. They return `202` with the job and its `Location`; follow it with `GET /api/ingestion/jobs/{id}`.
Every county runs in its own transactions, and a retry simply runs again (only changes are written).

| Request | Job (`dataset`) | What it does |
|---|---|---|
| `POST /api/property-school-analysis/assign?fips5=12086` | `SCHOOL_ASSIGNMENT` | assigns the county's schools (one transaction), then computes its score v2 (a second one). Without `fips5`: every county with attendance zones and properties |
| `POST /api/property-school-analysis/calculate-all?version=2&fips5=12086` | `SCHOOL_SCORE_V2` | score v2 only, from the stored assignments: run it after a new FLDOE grades load. Without `fips5`: every assigned county |
| `POST /api/property-school-analysis/calculate-all` (or `version=1`) | none | the v1 score of `property_school_analysis`, unchanged: synchronous, `204`; admin role since SCRUM-94, like every `calculate-all` |

Job counters:

- `total`: the properties to handle (with a parcel for an assignment, assigned for a score).
- `completed`: properties that got a school (assignment) or a score.
- `skipped`: the others.

`currentStep` ends with a summary, for example `12086 E 577570/1239/2946 M 578757/0/2998 H 578947/0/2808
(zone/unmatched/nearest), scored 581755`: the rows per level by `ZONE`/`UNMATCHED_ZONE`/`NEAREST`, then the scored
properties. The service logs the same counts, with the inserted, updated and deleted rows and the duration.

Refused before anything is queued:

- `400`: `fips5` not 5 digits, no county with that FIPS code, `version` not 1 or 2, `recalculateExisting` with
  version 2, or `fips5` with version 1.
- `401` / `403`: no token, or no admin role. Every request above needs the role, v1 included (SCRUM-94).
- `409`: V49 missing; the county has no attendance zones or no properties (assign), or no assignment (score); a job
  of the same dataset is already queued or running.

## Reading it

`GET /api/properties/id/{id}/location-intelligence/schools` (`SchoolContextDTO`):

- `assignedSchools`, elementary then middle then high, one entry per level:
  - `level`, `schoolId`, `name`, `sector`;
  - `letterGrade`, `fldoeGrade`, `pctTotalPoints`, `statewidePercentile` and `gradeYear`, all for the score's
    grade year;
  - `distanceMeters`, `assignmentMethod` (`ZONE`, `UNMATCHED_ZONE` or `NEAREST`), `zoneSourceType`;
  - `note`:
    - `NEAREST`: "No attendance zone covers this property for this level …";
    - `UNMATCHED_ZONE`: "Zoned school not in the school data: this level is left out of the school score". The
      entry has the name the zone gives, and `schoolId`, the grade fields and `distanceMeters` are `null`;
    - a school without a grade: "Not graded by FLDOE for 2026" (with "incomplete (I), too few students tested" for
      grade I).
- `schoolScoreV2`, `schoolScoreV2Label`, `qualityScore`, `accessScore`, `choiceScore` (rounded), `gradeYear`,
  `scoreVersion`.
- `source`: `FLDOE School Grades 2026, NCES CCD 2024-25 school locations and district attendance zones` when the
  property has assigned schools, otherwise the v1 source.
- `status`:
  - `AVAILABLE` when the property has assigned schools.
  - Otherwise the v1 rules: `AVAILABLE`, or `NO_NEARBY_RESULTS` when no school is within 1 km.
  - `NO_COVERAGE` when there is neither, which for v2 means the property's county has no zones loaded or the
    property has no parcel.
- `note` names:
  - a level without a school;
  - the levels whose zoned school is not in the school data (`UNMATCHED_ZONE`);
  - the levels shown by `NEAREST`;
  - a score not computed yet, or unavailable (no level has a known school);
  - unknown parts.

  Notes about the v1 fields start with `v1 fields:` when the property also has assigned schools.
- The v1 fields stay for one release, marked deprecated in the API description. They are filled only for properties
  with a `property_school_analysis` row.

`POST /api/property-school-analysis/bulk` (`SchoolAnalysisDTO`, also used by property compare) has the same v2
fields. A property with v2 data but no v1 row is now in the result, with the v1 fields `null`. Property compare
still reads the v1 `schoolScore`.

## Refreshing

| Change | Run |
|---|---|
| New FLDOE grades (each summer, see `school-reference-data.md`) | `calculate-all?version=2` |
| Zones or schools reloaded for a county | `assign?fips5=…` (assigns and rescores) |
| New or changed parcels, new properties | `assign?fips5=…` |

## Performance

Local rehearsal (laptop, PostgreSQL 16 + PostGIS 3.5, scratch database), Miami-Dade, 581,755 properties with a
parcel, 1,745,265 assignment rows:

| Run | Assignment | Score v2 | Job |
|---|---|---|---|
| First (tables empty, two runs) | 148-315 s (inserts 1,745,265 rows; the foreign key checks take most of it) | 91-141 s (inserts 581,755 rows) | 4-8 min |
| Again, nothing changed (five runs) | 100-136 s, writes nothing | 56-135 s, writes nothing | 3-4 min |

The times vary by a factor of two between runs on the same laptop (disk cache, other load).

Assignment re-run, by step:

| Step | Time |
|---|---|
| Points of 581,755 parcels, with their GIST index | 16 s |
| Zone matches (1,736,513 rows into a temporary table): matching 12 s, spheroid distances 26 s, writing the temporary table 27 s | 76 s |
| Nearest school for the 8,752 levels no zone contains | 18 s |
| Compare with the stored rows (delete, update, insert) | 33 s |

The score v2 re-run spends 43 s on its inputs, about 30 s scoring the 30 pages of 20,000 properties in Java (choice
search included) and copying them to the database, and 9 s comparing with the stored rows.

One parcel: the same rules for a single property (query below) take under 4 ms to plan and 0.7-1.4 ms (zoned, or a
zone without a school) to about 7 ms (nearest school) to execute on a warm connection. EXPLAIN ANALYZE, second and
third run of one session: property 205322 (`UNMATCHED_ZONE` elementary) planning 1.8-1.9 ms, execution 0.7-1.0 ms;
556 (zoned) 3.2-3.7 ms and 1.4 ms; 45472 (nearest) 3.5-3.8 ms and 6.8-7.2 ms. The zone lookup is an index scan of
`idx_school_attendance_zones_geom`. The first query of a new database session takes 80-270 ms more: PostGIS sets up
its geography and geometry support once per session, and the application's pooled connections pay it once.

```sql
WITH p AS (
    SELECT ST_PointOnSurface(pa.geom) AS pt FROM parcels pa
    WHERE pa.property_id = :pid ORDER BY ST_Area(pa.geom) DESC, pa.fid LIMIT 1),
lv(level, lo, hi) AS (VALUES ('ELEMENTARY', 0, 5), ('MIDDLE', 6, 8), ('HIGH', 9, 12)),
zoned AS (
    -- zones of an active school teaching the level, and zones without a school (UNMATCHED_ZONE)
    SELECT DISTINCT ON (lv.level) lv.level, z.school_id,
        CASE WHEN z.school_id IS NULL THEN 'UNMATCHED_ZONE' ELSE 'ZONE' END AS method, z.id AS zone_id,
        z.source_type, z.zone_school_name
    FROM p
    JOIN school_attendance_zones z ON ST_Intersects(z.geom, p.pt)
    LEFT JOIN schools s ON s.id = z.school_id
    JOIN lv ON z.level = lv.level OR z.level = 'COMBINATION'
    WHERE z.fips5 = :'fips5'
      AND (z.school_id IS NULL OR (s.is_active
           AND CASE WHEN s.lowest_grade IS NULL OR s.highest_grade IS NULL THEN s.level IN (lv.level, 'COMBINATION')
                    ELSE LEAST(s.highest_grade, lv.hi) - GREATEST(s.lowest_grade, lv.lo) >= 1 END))
    ORDER BY lv.level, array_position(ARRAY['OFFICIAL', 'OFFICIAL_RECONSTRUCTED', 'COUNTY_WIDE_SINGLE_SCHOOL',
        'NCES_SABS_2015_16', 'UNOFFICIAL']::text[], z.source_type::text), ST_Area(z.geom), z.id),
nearest AS (
    SELECT lv.level, n.school_id, 'NEAREST' AS method, NULL::bigint AS zone_id, NULL::varchar AS source_type,
        NULL::varchar AS zone_school_name
    FROM p CROSS JOIN lv
    CROSS JOIN LATERAL (
        SELECT s.id AS school_id FROM schools s
        WHERE s.fips5 = :'fips5' AND s.is_active AND s.sector = 'PUBLIC' AND s.school_type = 'REGULAR'
          AND NOT coalesce(s.is_virtual, false)
          AND CASE WHEN s.lowest_grade IS NULL OR s.highest_grade IS NULL THEN s.level IN (lv.level, 'COMBINATION')
                   ELSE LEAST(s.highest_grade, lv.hi) - GREATEST(s.lowest_grade, lv.lo) >= 1 END
          AND EXISTS (SELECT 1 FROM school_attendance_zones z WHERE z.school_id = s.id AND z.fips5 = :'fips5'
                      AND (z.level = lv.level OR z.level = 'COMBINATION'))
        ORDER BY s.geom::geography <-> p.pt::geography, s.id LIMIT 1) n
    WHERE lv.level NOT IN (SELECT level FROM zoned))
SELECT a.level, coalesce(s.name, a.zone_school_name) AS name, a.method, a.source_type,
    round(ST_Distance(p.pt::geography, s.geom::geography)::numeric, 1) AS distance_m
FROM (SELECT * FROM zoned UNION ALL SELECT * FROM nearest) a
LEFT JOIN schools s ON s.id = a.school_id CROSS JOIN p
ORDER BY a.level;
```

Run it with `psql -v pid=556 -v fips5=12086`. (This short form checks "has a zone of that level" for `NEAREST`
directly, which is the county rule whenever the county has zones of every level, as Miami-Dade does. It gives the
stored rows for properties 205322, 556 and 45472.)

## Known limits

- **Choice is still high in dense Miami-Dade.** With 3 miles, 391,644 of 581,755 properties have a choice of 90 or
  more (551,176 with 5 miles), the mean is 87.5 (94.6), and 21,976 properties (Everglades, far south) have 0. It
  weighs 15%, and none of those seats is guaranteed.
- **Zones without a school.** Miami-Dade's Shadowlawn elementary zone (school ID `13-4961`) has no school in the
  school data, so 1,239 properties have no elementary school in their score. Linking the zone to the school that
  serves it now (check an address such as 298 NW 48 St on the M-DCPS school locator), or adding that school, and
  assigning the county again fixes them. Statewide, Ave Maria Elementary (Collier, new in 2026-27) and Woodham Middle
  (Escambia, NCES 2015-16 zone) are the two others.
- **Zone dates.** Zones are as recent as SCRUM-202's files (Miami-Dade: the M-DCPS 2026-27 layers). A boundary
  redrawn later needs a zones reload and a new assignment.
- **Grades of combination schools.** A K-8 or 6-12 school has one FLDOE grade and is ranked among combination
  schools; the same percentile is used for each level it teaches.
- **Counties.** Only counties with properties are assigned: Miami-Dade today. The other 30 counties of SCRUM-202 have
  schools and zones but no properties yet.

## Verification log

### 2026-11-24, local rehearsal

Scratch database `geozentra_scrum201_e2e_test` (full schema, SCRUM-202 data loaded, V49 applied, then numbered
V46), application on port 8093 with a local test token issuer.

- `assign?fips5=12086` without a token: 401; with a non-admin token: 403; as admin: 202, job `SCHOOL_SCORING` /
  `SCHOOL_ASSIGNMENT`, `Location: /api/ingestion/jobs/{id}`.
- Job completed: total 581,755, completed 581,755, skipped 0, step `12086 E 577570/1239/2946 M 578757/0/2998 H
  578947/0/2808 (zone/unmatched/nearest), scored 581755`.
- Every Miami-Dade property with a parcel has an elementary, a middle and a high row, and a score row:

  | Level | ZONE (all `OFFICIAL`) | UNMATCHED_ZONE | NEAREST (in no zone of the level) | Mean distance ZONE / NEAREST |
  |---|---|---|---|---|
  | Elementary | 577,570 | 1,239 (Shadowlawn) | 2,946 | 1.7 km / 19.9 km |
  | Middle | 578,757 | 0 | 2,998 (Everglades, government land) | 3.0 km / 29.1 km |
  | High | 578,947 | 0 | 2,808 | 3.6 km / 29.0 km |

- No assignment points to a closed school. Scores: Excellent 15,945, Good 297,071, Fair 253,232, Limited 15,507;
  the score ranges from 13.2 to 86.4 (mean 60.6); quality mean 46.7, access 78.0, choice 87.5 (spread 20.6). Every
  property has a score.
- Running the job again wrote nothing (0 inserted, updated or deleted, both steps), also after the zone tie-break
  moved from geodesic to planar area.
- Postman collection: 23 requests passed, 0 failed (jobs, a second assign while the first runs: 409, reads including
  a zone without a school, invalid input, roles, missing data).
- API: property 556 (all zoned, graded): AVAILABLE, score 77 Good, source FLDOE School Grades 2026. Property 45472
  (Everglades): every level `NEAREST`, 16-20 km, score 37 Limited, with the note. Property 130222: Parkview
  Educational Center "Not graded by FLDOE for 2026", quality from the middle and high schools only. Property 205322
  (Shadowlawn zone): elementary `UNMATCHED_ZONE` "Shadowlawn Elementary" without school, grade or distance, score
  54.3 Fair from the middle and high schools, with the note. Unknown property: 404. Bulk: v2-only properties present,
  unknown ones absent. About 25 ms per school context request.
- The single-parcel query above gives the stored rows for properties 205322, 556 and 45472, in 0.7-7 ms on a warm
  connection (see Performance).
- Tests: `SchoolScoreV2Test`, `SchoolChoiceIndexTest`, `SchoolScoringJobWorkerTest`, `IngestionLaunchServiceImplTest`,
  `SchoolAnalysisServiceImplTest`, `PropertySchoolContextServiceImplTest`, `SchoolAnalysisSecurityTest`, and the
  opt-in PostGIS test `SchoolScoringDatabaseTest` (fixture of SCRUM-202: official zone beats NCES zone, closed
  school's zone skipped, zone without school gives no school, a level without any public zoned school, the larger
  parcel, county isolation, rerun writes nothing, zones and schools reloads not blocked, score math, reader).

### Spot check against the M-DCPS school locator

The ten properties of the SCRUM-202 spot check get the same schools from the stored assignments as from SCRUM-202's
lookup query. To be checked by hand on the M-DCPS school locator (an interactive site); record the outcome in the
last column.

| Folio | Address | Elementary | Middle | High | Score | Locator |
|---|---|---|---|---|---|---|
| 0341070186071 | 1400 Madrid St, Coral Gables 33134 | Coral Gables Preparatory Academy | Ponce de Leon Middle | Coral Gables Senior High | 63.1 Good | to check |
| 2822100790028 | 1500 Island Blvd, Aventura 33160 | Aventura Waterways K-8 Center | Highland Oaks Middle | Dr. Michael M. Krop Senior High | 51.5 Fair | to check |
| 3421040420230 | 19203 NW 33 Ct, Miami Gardens 33056 | Brentwood Elementary | Carol City Middle | Miami Carol City Senior High | 63.5 Good | to check |
| 3660090300180 | 20627 SW 91 Ct, Cutler Bay 33189 | Dr. Edward L. Whigham Elementary | Cutler Bay Middle | Miami Southridge Senior High | 60.8 Good | to check |
| 1079280170630 | 2458 SE 26 Ter, Homestead 33035 | Gateway Environmental K-8 Learning Center | Homestead Middle | Homestead Senior High | 46.5 Fair | to check |
| 0232270080040 | 2940 Alton Rd, Miami Beach 33140 | North Beach Elementary | Miami Beach Nautilus Middle | Miami Beach Senior High | 64.6 Good | to check |
| 0420280470130 | 3299 W 76 St, Hialeah 33018 | Ernest R. Graham K-8 Academy | Hialeah Gardens Middle | Hialeah Gardens Senior High | 77.1 Good | to check |
| 0131270100951 | 3424 NW 19 Ave, Miami 33142 | Kelsey L. Pharr Elementary | Georgia Jones Ayers Middle | Miami Jackson Senior High | 72.4 Good | to check |
| 3041310191976 | 7700 SW 53 Ct, unincorporated 33143 | Sunset Elementary | Ponce de Leon Middle | Coral Gables Senior High | 70.7 Good | to check |
| 3530290821240 | 9817 NW 31 St, Doral 33172 | John I. Smith K-8 Center | Ruben Dario Middle | J.C. Bermudez Doral Senior High | 69.5 Good | to check |

## Files

| Part | File |
|---|---|
| Migration | `db/migration/V49__add_property_school_assignments_and_scores.sql` |
| Assignment | `service/SchoolAssignmentService`, `service/impl/SchoolAssignmentServiceImpl` |
| Score v2 | `service/SchoolScoreV2Service`, `service/impl/SchoolScoreV2ServiceImpl`, `service/impl/SchoolScoreV2` (formula), `service/impl/SchoolChoiceIndex` (3-mile search) |
| SQL | `service/impl/SchoolScoringSql` |
| Job | `enums/ImportJobType.SCHOOL_SCORING`, `enums/IngestionDataset` (`SCHOOL_ASSIGNMENT`, `SCHOOL_SCORE_V2`), `service/impl/SchoolScoringJobWorker`, `IngestionLaunchServiceImpl.startSchoolScoring` |
| API | `controller/SchoolAnalysisController` (`/assign`, `calculate-all?version=2`), `config/SecurityConfig` (admin role), `repository/SchoolScoreReader` + `JdbcSchoolScoreReader`, `dto/AssignedSchoolDTO`, `dto/SchoolContextDTO`, `dto/SchoolAnalysisDTO`, `service/impl/SchoolAnalysisServiceImpl`, `service/impl/PropertySchoolContextServiceImpl` |
| Tests | see the verification log; fixture `src/test/resources/school-data` |
| Postman | `docs/postman/SCRUM-203-school-score-v2.postman_collection.json` |
