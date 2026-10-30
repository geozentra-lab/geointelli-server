# Property comparison — `POST /api/properties/compare`

Compares 2 to 5 user-selected properties side by side. Each property becomes one column with the
same set of fields, in the same units, taken from data that already exists (property facts, county
assessment, last sale, stored valuation, school and transportation scores). Nothing is recalculated:
no valuation and no investment formula runs in this endpoint.

Authentication: the usual `Authorization: Bearer <token>` header, like every `/api` endpoint.

## Request

```
POST /api/properties/compare
Content-Type: application/json

{ "propertyIds": [2, 3, 4] }
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `propertyIds` | array of integers | yes | internal database ids (**not** folios); 2 to 5 items; every item a positive number; no duplicates |

The order of the ids is the order of the columns in the response.

## Response

`200 OK` with one envelope and one column per requested id:

| Field | Meaning |
|---|---|
| `requestedIds` | the ids exactly as sent, same order |
| `comparedCount` | number of columns (always equals the number of ids, since an unknown id is a 404) |
| `units` | units shared by every column: `currency` USD, `area` sqft, `lotSize` sqft |
| `assessmentYear` | the latest roll year present on **every** property that has assessments; `null` when they share none |
| `warnings` | human-readable notes about the whole comparison; `[]` when there is nothing to say |
| `properties` | the columns, see below |

Each item of `properties` is a `PropertyComparisonDTO`:

| Field | Source | Meaning |
|---|---|---|
| `propertyId`, `folio`, `address`, `countyName`, `stateCode` | property | identity of the column |
| `facts` | property | `bedrooms`, `bathrooms`, `livingAreaSqft`, `lotSizeSqft`, `yearBuilt`, `propertyType` (DOR code + description), `floors`, `units` |
| `assessment` | property assessments | `year`, `totalValue`, `landValue`, `buildingValue`, `assessedValue` for the common year, or the property's own latest year when there is no common year |
| `lastSale` | property sales | `date`, `price` of the most recent sale |
| `valuation` | stored prediction | `predictedPrice`, `low`, `high`, `growthPercentage`, `confidenceScore`, `predictionDate` |
| `scores` | analyses | `schoolScore`, `transportationScore` |
| `missingFields` | computed | names of the values above that are `null` for this property |

Rules for missing data:

- a value that does not exist is `null`, never `0` or an empty string, and its path is listed in
  `missingFields` (for example `facts.yearBuilt`, `scores.schoolScore`);
- when a whole section is absent the section itself is `null` and its name alone is listed
  (`assessment`, `lastSale`, `valuation`);
- a property that lacks a valuation or a score does **not** fail the request; only an unknown id does.

Consistency rules:

- money is USD, areas and lot sizes are square feet, for every column (`units` says so once);
- the assessment shown is the same roll year for every column whenever possible. If the properties
  share no year, each column shows its own latest year, `assessmentYear` is `null` and `warnings`
  contains `"No assessment year is shared by all properties; each column shows its own latest year."`.

Dates are serialized the way every endpoint of this service serializes `LocalDate`, as
`[year, month, day]` arrays (project-wide Jackson configuration, unchanged by this feature).

### Examples

Three properties, all with a valuation (shortened to one column):

```
POST /api/properties/compare
{ "propertyIds": [2, 3, 4] }

{ "requestedIds": [2, 3, 4], "comparedCount": 3,
  "units": { "currency": "USD", "area": "sqft", "lotSize": "sqft" },
  "assessmentYear": 2025, "warnings": [],
  "properties": [
    { "propertyId": 2, "folio": "3049340031190",
      "address": "13670 SW 79 ST, Unincorporated County, FL 33183-0000",
      "countyName": "Miami-Dade", "stateCode": "FL",
      "facts": { "bedrooms": 3, "bathrooms": 2.0, "livingAreaSqft": 1938, "lotSizeSqft": 9690.0,
                 "yearBuilt": "1970", "propertyType": "0101 RESIDENTIAL - SINGLE FAMILY : 1 UNIT",
                 "floors": 2, "units": 1 },
      "assessment": { "year": 2025, "totalValue": 610678.0, "landValue": 386775.0,
                      "buildingValue": 186876.0, "assessedValue": 610678.0 },
      "lastSale": { "date": [2023, 8, 15], "price": 751000.0 },
      "valuation": { "predictedPrice": 734031.06, "low": 438846.44, "high": 1227766.5,
                     "growthPercentage": -2.2595, "confidenceScore": 74, "predictionDate": [2026, 6, 25] },
      "scores": { "schoolScore": null, "transportationScore": 25.0 },
      "missingFields": [ "scores.schoolScore" ] },
    { "propertyId": 3, ... },
    { "propertyId": 4, ... } ] }
```

A property without valuation, sale or scores (column for id 10 of `[1, 2, 10]`):

```
    { "propertyId": 10, "folio": "3049340031230",
      "address": "13670 SW 75 ST, Unincorporated County, FL 33183-0000",
      "countyName": "Miami-Dade", "stateCode": "FL",
      "facts": { "bedrooms": 4, "bathrooms": 2.0, "livingAreaSqft": 2215, "lotSizeSqft": 11884.0,
                 "yearBuilt": "Multiple (See Building Info.)",
                 "propertyType": "0101 RESIDENTIAL - SINGLE FAMILY : 1 UNIT", "floors": 1, "units": 1 },
      "assessment": { "year": 2025, "totalValue": 631132.0, "landValue": 436140.0,
                      "buildingValue": 190600.0, "assessedValue": 204662.0 },
      "lastSale": null,
      "valuation": null,
      "scores": { "schoolScore": null, "transportationScore": null },
      "missingFields": [ "lastSale", "valuation", "scores.schoolScore", "scores.transportationScore" ] }
```

Order is preserved: `{ "propertyIds": [5, 2, 3] }` returns columns 5, 2, 3.

## Errors

Every error uses the service's standard body `{ "status", "error", "message", "timestamp" }`.

| Case | Status | `message` |
|---|---|---|
| fewer than 2 or more than 5 ids | `400` | `propertyIds: size must be between 2 and 5` |
| an id is `null` | `400` | `propertyIds[1]: must not be null` |
| an id is zero or negative | `400` | `propertyIds[1]: must be greater than 0` |
| `propertyIds` missing (`{}`) | `400` | `propertyIds: must not be null` |
| no body, or a non-numeric id (`"abc"`) | `400` | `Invalid JSON body or unsupported field value` |
| duplicate ids | `400` | `propertyIds contains duplicates: [2]` |
| an id matches no property | `404` | `No property found with id: 999999999` |

The three bean-validation messages (size, null, positive) are Spring's defaults and follow the
server's locale; on a French machine they read `la taille doit être comprise entre 2 et 5`,
`ne doit pas être nul`, `doit être supérieur à 0`. The status code is the same everywhere.

Validation runs before any database access, so a 400 never touches the database. An unknown id
fails the whole request: there is no partial comparison.

## Implementation

`PropertyCompareController.compare` (`@Valid` body, no logic) → `PropertyCompareService.compare`
(`PropertyCompareServiceImpl`, read-only transaction) which only calls existing services:

- `PropertyService.getPropertyById` once per id, in request order (facts, assessments, sales; 404 on unknown id);
- `PropertyValuePredictionService.getPropertyValuePredictionByIds` once for all ids (its
  "none found" exception is treated as "no valuation available");
- `SchoolAnalysisService.getPropertySchoolAnalysisByIds` and
  `TransportationAnalysisService.getPropertyTransportationAnalysisByIds` once each.

DTOs: `PropertyCompareRequest`, `PropertyCompareResponse`, `PropertyComparisonDTO`. No schema
change, no new repository, no new query. Manual checks: `docs/postman/SCRUM-61-property-compare.postman_collection.json`.
