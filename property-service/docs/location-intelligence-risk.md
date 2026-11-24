# Risk context — `GET /api/properties/id/{id}/location-intelligence/risk` (SCRUM-127)

Risk section of the property location intelligence (parent SCRUM-53), next to the schools (SCRUM-125) and
transport (SCRUM-126) sections. It reads the environmental reference data loaded by SCRUM-201
(`docs/environmental-reference-data.md`) and returns, for one property, three **independent** blocks:

| Block | Source | Table(s) | Question answered |
|---|---|---|---|
| `flood` | FEMA National Flood Hazard Layer | `flood_zones` | Which FEMA flood zone polygon contains the property point? |
| `hazards` | FEMA National Risk Index (NRI) | `census_tracts`, `nri_tract_risk` | Which census tract contains the point, and what are its 8 NRI hazard ratings? |
| `storms` | NOAA Storm Events | `storm_events` | How many NOAA events were recorded within 10 km since 1996, by type? |

Rules of the ticket, and how the endpoint applies them:

- **Only sourced information.** Every block carries `source` and `dataAsOf` (the download date recorded in
  `reference_dataset_loads` for that dataset and, for flood zones, that county). Nothing is scored, labelled or
  rated by GeoZentra: values are FEMA's and NOAA's own.
- **Dates and coverage gaps are identified.** A block whose source is not loaded for the property's county or state
  is `NO_COVERAGE`; a block whose source is loaded but holds nothing at the point is `NO_DATA`; both carry a `note`.
- **Missing evidence is never a safe rating.** No flood polygon at the point is reported as "not a flood-free
  determination"; a tract without an NRI row is "FEMA publishes no NRI row"; a null hazard rating is "not published
  by FEMA (insufficient data), not low risk"; zero events within the radius still shows the county-wide count.
- **Sections stay separate.** The three blocks are evaluated one after the other; a block without data never
  changes the others. A failure of the reference tables is returned inside the section (`UNAVAILABLE`), not thrown.

Scope decision (2026-11-24): the neighborhood, amenities and crime sections are postponed; SCRUM-127 delivers the
risk section only.

## Location of the property

Properties have no coordinates. The point used is `ST_PointOnSurface(parcels.geom)`, the convention of the
comparable search and the street-view matcher: a point guaranteed to lie on the parcel polygon. A property without
a usable parcel polygon (none, empty, invalid or not WGS 84) gets `status: NO_COVERAGE` at the top and no blocks.
The county comes from `properties.county_id` → `counties.fips_code` (set by V42, completed by V46).

## Response

```
GET /api/properties/id/{id}/location-intelligence/risk        no token required (public property data)
```

| Field | Meaning |
|---|---|
| `propertyId` | the property |
| `status` | `AVAILABLE` (point located, blocks evaluated), `NO_COVERAGE` (no parcel polygon), `UNAVAILABLE` (reference tables unreadable; `error` set) |
| `distanceUnit` | `m`, unit of `storms.radius` |
| `flood` | see below; absent when the top status is not `AVAILABLE` |
| `hazards` | see below |
| `storms` | see below |
| `note` | why the section is empty or incomplete; e.g. the county has no FIPS code |
| `error` | text when `UNAVAILABLE` |

### `flood`

| Field | Meaning |
|---|---|
| `status` | `AVAILABLE` polygon found; `NO_DATA` county loaded, no polygon at the point; `NO_COVERAGE` county map not loaded |
| `zone` | FEMA `FLD_ZONE`: A, AE, AH, AO, V, VE, X, D, OPEN WATER |
| `zoneSubtype` | FEMA `ZONE_SUBTY`, e.g. `FLOODWAY`, `0.2 PCT ANNUAL CHANCE FLOOD HAZARD`, `AREA OF MINIMAL FLOOD HAZARD`; null when FEMA gives none |
| `specialFloodHazardArea` | FEMA `SFHA_TF`: true inside the 1 %-annual-chance area |
| `baseFloodElevationFt`, `depthFt` | FEMA `STATIC_BFE`, `DEPTH` in feet; null when FEMA gives none (-9999 in the source) |
| `studyType` | FEMA `STUDY_TYP`, e.g. `NP` (no study), `SFHAs WITH HIGH FLOOD RISK` |
| `firmId` | FEMA map id of the county (`DFIRM_ID`) |
| `source`, `dataAsOf`, `note` | provenance; the note reports overlapping FEMA polygons (the SFHA one is shown) |

### `hazards`

| Field | Meaning |
|---|---|
| `status` | `AVAILABLE` tract has an NRI row; `NO_DATA` tract without NRI row (water-only tracts, code 9900); `NO_COVERAGE` no tract polygon contains the point |
| `tractFips`, `tractName` | the census tract (TIGER 2024, 2020 tracts) |
| `nriVersion` | e.g. `December 2025` |
| `overallScore`, `overallRating` | NRI composite over the 18 hazards; rating in Very Low, Relatively Low, Relatively Moderate, Relatively High, Very High; null = not published |
| `ratings[]` | fixed order HRCN Hurricane, CFLD Coastal flooding, IFLD Inland flooding, TRND Tornado, WFIR Wildfire, SWND Strong wind, LTNG Lightning, HWAV Heat wave; each `code`, `name`, `score`, `rating` |
| `source`, `dataAsOf`, `note` | provenance; the note states when some ratings are null |

### `storms`

| Field | Meaning |
|---|---|
| `status` | `AVAILABLE` events within the radius; `NO_DATA` state loaded, none within the radius; `NO_COVERAGE` state not loaded |
| `radius`, `fromYear` | 10 000 m and 1996 (NOAA records every event type only from 1996) |
| `eventsWithinRadius` | located events (NOAA point) within the radius since `fromYear` |
| `eventsInCounty` | all events of the property's county since `fromYear`, located or not (zone-level events have no point) |
| `lastEventDate` | most recent located event within the radius |
| `byType[]` | `eventType`, `count`, `lastEventDate`, `propertyDamageUsd` (sum of NOAA's reported property damage; null when none reported), most frequent first |
| `source`, `dataAsOf`, `note` | provenance; the note recalls that only located events are counted within the radius |

### Errors

| Case | HTTP | Body |
|---|---|---|
| `id` not positive | 400 | `id must be a positive number` |
| unknown property | 404 | `No property found with id: {id}` |
| reference tables unreadable | 200 | `status: UNAVAILABLE`, `error: Environmental reference data unavailable` |

## Example (property on an AE zone, Miami-Dade)

```json
{
  "propertyId": 1, "status": "AVAILABLE", "distanceUnit": "m",
  "flood": { "status": "AVAILABLE", "zone": "AE", "zoneSubtype": null, "specialFloodHazardArea": true,
             "baseFloodElevationFt": 9.0, "depthFt": null, "studyType": "NP", "firmId": "12086C",
             "source": "FEMA National Flood Hazard Layer (NFHL), flood hazard zones", "dataAsOf": "2026-11-14", "note": null },
  "hazards": { "status": "AVAILABLE", "tractFips": "12086010201", "tractName": "Census Tract 102.01",
               "nriVersion": "December 2025", "overallScore": 91.897066, "overallRating": "Relatively High",
               "ratings": [ { "code": "HRCN", "name": "Hurricane", "score": 96.288486, "rating": "Relatively High" }, "..." ],
               "source": "FEMA National Risk Index (NRI), census tract", "dataAsOf": "2026-11-11", "note": null },
  "storms": { "status": "AVAILABLE", "radius": 10000, "fromYear": 1996, "eventsWithinRadius": 39, "eventsInCounty": 1025,
              "lastEventDate": "2026-06-26",
              "byType": [ { "eventType": "Thunderstorm Wind", "count": 13, "lastEventDate": "2026-06-26", "propertyDamageUsd": 46000 }, "..." ],
              "source": "NOAA NCEI Storm Events Database", "dataAsOf": "2026-11-11",
              "note": "Only events with coordinates are counted within the radius; county-wide events include those without" },
  "note": null, "error": null
}
```

## Implementation

| Layer | File |
|---|---|
| Controller | `controller/PropertyRiskContextController.java` |
| Service | `service/PropertyRiskContextService.java`, `service/impl/PropertyRiskContextServiceImpl.java` (one read-only transaction; the note texts are its constants) |
| Repository | `repository/RiskReferenceReader.java`, `repository/JdbcRiskReferenceReader.java` (native SQL on the V42 tables: `ST_Intersects` on the GIST indexes, `ST_DWithin` on the geography index; parts of one FEMA polygon merged by `(source_file, source_row)`) |
| DTO | `dto/RiskContextDTO.java` with nested `FloodZoneDTO`, `HazardRatingsDTO`, `HazardRatingDTO`, `StormHistoryDTO`, `StormTypeCountDTO` |
| Migrations | `V46__fix_saint_county_fips_codes.sql`, `V47__widen_flood_zones_study_typ.sql` (defects of V42 found while loading, see the SCRUM-201 doc) |

No automated tests (ticket). Ownership checks: not applicable, the endpoint reads public property data without a
token, like the schools and transport sections.

## Manual checks (Postman)

`docs/postman/SCRUM-127-risk-context.postman_collection.json`. Variables: `baseUrl`, and one property id per case
(`idSfha`, `idZoneX`, `idOpenWater`, `idNoPolygon`, `idNoParcel`, `idNoStorms`, `idCountyNotLoaded`). On a database
without such properties, pick ids with `SELECT` on `parcels`, `flood_zones` and `census_tracts` as in the verification
log below.

| # | Case | Expected |
|---|---|---|
| 01 | property inside a Special Flood Hazard Area | 200, top `AVAILABLE`; `flood.specialFloodHazardArea = true`, zone and elevation set; every block has `source` and `dataAsOf` |
| 02 | property in zone X | 200, `flood.zone = X`, `specialFloodHazardArea = false`, `baseFloodElevationFt = null` |
| 03 | property in open water | 200, `flood.zone = OPEN WATER`, `hazards.status = NO_DATA` with the water-tract note |
| 04 | point with no FEMA polygon | 200, `flood.status = NO_DATA`, note "not a flood-free determination"; other blocks unaffected |
| 05 | property without parcel | 200, top `NO_COVERAGE`, no blocks, note |
| 06 | point with no located storm within 10 km | 200, `storms.status = NO_DATA`, `eventsWithinRadius = 0`, `eventsInCounty > 0` |
| 07 | county whose flood map is not loaded | 200, `flood.status = NO_COVERAGE`, `hazards` and `storms` still `AVAILABLE` |
| 08 | `id = 0` | 400 |
| 09 | unknown id | 404 |

## Verification log

2026-11-24, local Docker PostGIS (`scrum200-pg`) with V42 + V46 + V47 applied, loaded through the SCRUM-201 import
endpoint with the real Florida files: census tracts 5,160, NRI 5,114, storm events 41,437, flood zones of Miami-Dade
(3,637 polygons) and Broward (17,535). Test parcels created as small polygons around chosen points
(`ST_Buffer(point, 0.0002)`); the Palm Beach property has a parcel but its county's flood map is not loaded.

| # | Property / point | Result |
|---|---|---|
| 01 | 1, Miami-Dade, inside an AE polygon (-80.4759, 25.5834) | AVAILABLE; flood AE, sfha true, bfe 9.0, study NP, firm 12086C, asOf 2026-11-14; hazards tract 12086010201 Relatively High (91.9), 8 ratings; storms 39 within 10 km, 1,025 in county, last 2026-06-26 |
| 02 | 2, Miami-Dade, zone X (-80.4767, 25.5808) | flood X / AREA OF MINIMAL FLOOD HAZARD, sfha false, bfe null |
| 03 | 3, Miami-Dade, sea tract 12086990000 | flood OPEN WATER, sfha false; hazards NO_DATA, "FEMA publishes no NRI row for this tract"; storms 19 within radius |
| 04 | 4, Broward, ocean tract 12011990000 | flood NO_DATA, "No FEMA flood zone polygon at this point; this is not a flood-free determination"; hazards NO_DATA; storms 75 |
| 05 | 5, no parcel | NO_COVERAGE, "No parcel polygon for this property: the location cannot be evaluated", blocks absent |
| 06 | 6, Miami-Dade, Everglades tract 12086011408 | flood D; hazards Very High (98.8); storms NO_DATA, 0 within radius, 1,025 in county |
| 07 | 7, Palm Beach tract 12099000102 | flood NO_COVERAGE, "FEMA flood map of this county not loaded", dataAsOf null; hazards Relatively Moderate; storms 64 |
| 08 | id 0 | 400 "id must be a positive number" |
| 09 | id 999 | 404 "No property found with id: 999" |

The same SQL was exercised on RDS (dev) for Baker property 597416 before the endpoint existed: "X / AREA OF MINIMAL
FLOOD HAZARD, sfha=false, bfe=NULL". A run of the collection against RDS is pending (the laptop's VPN blocks RDS).
