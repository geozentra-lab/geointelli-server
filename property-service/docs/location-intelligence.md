# Location intelligence — `GET /api/properties/id/{id}/location-intelligence` (SCRUM-128)

Composed location intelligence of a property (parent SCRUM-53). One call returns the sections that each have their
own endpoint, exactly as those endpoints return them:

| Field | Section endpoint | Ticket | Doc |
|---|---|---|---|
| `schools` | `GET /api/properties/id/{id}/location-intelligence/schools` | SCRUM-125 | `school-score-v2.md` |
| `transport` | `GET /api/properties/id/{id}/location-intelligence/transport` | SCRUM-126 | — |
| `risk` | `GET /api/properties/id/{id}/location-intelligence/risk` | SCRUM-127 | `location-intelligence-risk.md` |

The composed endpoint only calls the three section services (`PropertySchoolContextService`,
`PropertyTransportContextService`, `PropertyRiskContextService`); it never reads their tables or providers itself, so
every rule of a section (statuses, sources, dates, "missing evidence is never a safe rating") is unchanged here.

**Neighborhood is not included yet.** The neighborhood section (and the amenities and crime sections) was
postponed on 2026-11-24 when SCRUM-127 was limited to the risk section, so no neighborhood endpoint or service
exists. When it is built it becomes one more field of this response, read the same way as the others.

## Who may call it

| Caller | Result |
|---|---|
| Any authenticated user (any role) | `200` |
| No token | `401` |

The data describes places, not users: no record belongs to a caller, so there is no per-user ownership check, the
same as the three section endpoints. Access comes from `SecurityConfig` (`GET /api/properties/**` needs a valid
token).

## Response

| Field | Meaning |
|---|---|
| `propertyId` | The property ID |
| `status` | `COMPLETE`, `PARTIAL` or `UNAVAILABLE` (below) |
| `schools` | School section, or `null` when it could not be read |
| `transport` | Transport section, or `null` when it could not be read |
| `risk` | Risk section, or `null` when it could not be read |
| `errors` | One entry per failing section, in the order schools, transport, risk; empty when `COMPLETE` |

`status`:

| Value | When |
|---|---|
| `COMPLETE` | Every section was read. A section that reports `NO_COVERAGE` or `NO_NEARBY_RESULTS` is an answer ("no data for this place"), not an error |
| `PARTIAL` | At least one section is in `errors`; the others are returned normally |
| `UNAVAILABLE` | All three sections are in `errors` |

Section errors (`errors[]`: `section`, `code`, `message`):

| `code` | Meaning | The section in the response |
|---|---|---|
| `SECTION_FAILED` | The section service threw (for example its table could not be read). The cause is logged on the server; the message has no internals | `null` |
| `SECTION_UNAVAILABLE` | The section answered with its own `status: UNAVAILABLE` because its data source failed (transport and risk report this themselves) | Kept as returned, with its own `error` |

The sections are read one after the other, each in its own read-only transaction; the composer is deliberately
not transactional, so a failing section cannot roll back or hide the others. Inside `risk`, a single block (flood,
hazards or storms) can be `UNAVAILABLE` while the risk section is `AVAILABLE`: that stays inside the section, as
SCRUM-127 defines it, and is not a section error.

## Errors

| Case | Answer |
|---|---|
| `id` is 0 or negative | `400` `id must be a positive number` |
| `id` is not a number (`/id/abc/...`) | `400` `id: invalid value` |
| No property has this ID | `404` `No property found with id: <id>` |
| No token | `401` |

The property is checked once, before any section is read, so an unknown ID is a `404` and never a response with
three failed sections.

## Example: complete

Property 1 on the local verification database (the school and transport rows are local test rows; the risk
section is real FEMA/NOAA data). `risk.hazards.ratings` and `risk.storms.byType` are shortened here.

```json
{
  "propertyId": 1,
  "status": "COMPLETE",
  "schools": {
    "propertyId": 1, "status": "AVAILABLE", "distanceUnit": "m", "assignedSchools": [],
    "schoolScoreV2": null, "schoolScoreV2Label": null, "qualityScore": null, "accessScore": null,
    "choiceScore": null, "gradeYear": null, "scoreVersion": null,
    "nearestPublicSchoolDistance": 640.0, "nearestCharterSchoolDistance": 1850.0, "schoolsWithin1km": 3,
    "schoolScore": 65, "schoolScoreLabel": "Good", "publicSchoolDistanceScore": 80,
    "charterSchoolDistanceScore": 45, "schoolDensityScore": 60,
    "source": "GeoZentra school analysis (sales ML dataset)", "note": null
  },
  "transport": {
    "propertyId": 1, "status": "AVAILABLE", "distanceUnit": "m",
    "transport": [
      { "category": "RAIL", "nearestDistance": 2400.0 },
      { "category": "BUS", "nearestDistance": 180.0 },
      { "category": "HIGHWAY", "nearestDistance": 1300.0 }
    ],
    "busStopsWithin500m": 4, "transportationScore": 56, "transportationScoreLabel": "Fair",
    "source": "GeoZentra transportation analysis (sales ML dataset)", "error": null,
    "note": "Score based on known values only, unknown values: PEOPLE_MOVER"
  },
  "risk": {
    "propertyId": 1, "status": "AVAILABLE", "distanceUnit": "m",
    "flood": {
      "status": "AVAILABLE", "zone": "AE", "specialFloodHazardArea": true, "baseFloodElevationFt": 9.0,
      "studyType": "NP", "firmId": "12086C",
      "source": "FEMA National Flood Hazard Layer (NFHL), flood hazard zones", "dataAsOf": "2026-11-14"
    },
    "hazards": {
      "status": "AVAILABLE", "tractFips": "12086010201", "nriVersion": "December 2025",
      "overallRating": "Relatively High",
      "ratings": [ { "code": "HRCN", "name": "Hurricane", "score": 96.288486, "rating": "Relatively High" } ],
      "source": "FEMA National Risk Index (NRI), census tract", "dataAsOf": "2026-11-11"
    },
    "storms": {
      "status": "AVAILABLE", "radius": 10000, "fromYear": 1996, "eventsWithinRadius": 39, "eventsInCounty": 1025,
      "byType": [ { "eventType": "Thunderstorm Wind", "count": 13, "lastEventDate": "2025-03-25" } ]
    }
  },
  "errors": []
}
```

A property with no school or transport data is still `COMPLETE`: those sections answer `NO_COVERAGE` with their
`note` (local properties 2–7).

## Example: partial

The same property while the school table cannot be read (the school service throws) and the transport data source
is down (the transport service answers `UNAVAILABLE` itself). Risk is returned normally (shortened).

```json
{
  "propertyId": 1,
  "status": "PARTIAL",
  "schools": null,
  "transport": {
    "propertyId": 1, "status": "UNAVAILABLE", "distanceUnit": "m", "transport": [],
    "busStopsWithin500m": null, "transportationScore": null, "transportationScoreLabel": null,
    "source": "GeoZentra transportation analysis (sales ML dataset)",
    "error": "Transport data source unavailable", "note": null
  },
  "risk": { "propertyId": 1, "status": "AVAILABLE", "distanceUnit": "m", "flood": { "...": "..." } },
  "errors": [
    { "section": "schools", "code": "SECTION_FAILED", "message": "The schools section could not be read" },
    { "section": "transport", "code": "SECTION_UNAVAILABLE", "message": "Transport data source unavailable" }
  ]
}
```

When the risk reference data cannot be read either, `risk` comes back with `status: UNAVAILABLE` and
`error: "Environmental reference data unavailable"`, a third entry is added to `errors`, and `status` is
`UNAVAILABLE`.

## Verification (local Docker, 2026-11-26)

Run against a local PostgreSQL 16 copy of the schema on master c51b62a, with Keycloak on `localhost:8090`
(normal user `lic_user_a`, admin `lic_admin`). RDS was not used. Property 1 got local test rows in
`property_school_analysis` and `property_transportation_analysis`; its risk section is real FEMA/NOAA data.
Postman collection: `docs/postman/SCRUM-128-location-intelligence.postman_collection.json` (10 requests, 10 / 10
passed; set `password` and `adminPassword` before running). The complete and partial answers below are saved as
examples on its request 03.

| Case | Result |
|---|---|
| Property 1 (school, transport and risk data) | `200` `COMPLETE`, `errors` empty, the three sections as their own endpoints return them |
| Property 5 (no school, transport or parcel data) | `200` `COMPLETE`, each section `NO_COVERAGE` with its note |
| Properties 2, 3, 4, 6, 7 | `200` `COMPLETE` (schools and transport `NO_COVERAGE`, risk `AVAILABLE`) |
| Same property read by the admin | `200`, same answer (any role) |
| School table renamed, transport table renamed (both restored after) | `200` `PARTIAL`; `schools` null with `SECTION_FAILED`; `transport` kept with `status UNAVAILABLE` and `SECTION_UNAVAILABLE`; `risk` returned |
| Same, plus the flood, tract and storm tables renamed (restored after) | `200` `UNAVAILABLE`, three entries in `errors`; risk kept with `Environmental reference data unavailable` |
| Unknown property `999999999` | `404` `No property found with id: 999999999` |
| `id` 0, `-5` | `400` `id must be a positive number` |
| `id` `abc` | `400` `id: invalid value` |
| No token | `401` |

Existing tests still pass: `PropertyLocationIntelligenceControllerTest` (5, one `@MockBean` added for the new
service) and `SecurityPolicyTest` (55).
