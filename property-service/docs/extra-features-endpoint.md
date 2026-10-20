# Property extra features — `GET /api/properties/id/{id}/extra-features`

Returns the extra features (pools, decks, fences, sheds…) recorded for one property on the
county tax roll, for one roll year or across all roll years.

Authentication: the usual `Authorization: Bearer <token>` header, like every `/api` endpoint.

## Request

| Part | Name | Type | Required | Meaning |
|---|---|---|---|---|
| path | `id` | integer | yes | internal database id of the property (**not** the folio) |
| query | `year` | integer, 1900–2100 | no | return the features recorded for this roll year only |
| query | `history` | boolean, default `false` | no | return the features of every roll year, newest first |
| query | `page` | integer ≥ 0, default `0` | no | history only: page number |
| query | `size` | integer 1–200, default `50` | no | history only: page size (larger values are capped at 200) |

Modes, by what is given:

- **nothing**: every feature of the property's **highest roll year on record**;
- **`year`**: every feature of that roll year;
- **`history=true`**: every feature across all roll years, ordered by roll year descending, then by
  id, features without a roll year last; paged.

`year` and `history=true` are mutually exclusive.

Each recorded feature is one record; a feature listed in an earlier year is not assumed to exist in
a later year. Records are read by property id only (no joins), so none is repeated.

## Response

`200 OK` with:

| Field | Present | Meaning |
|---|---|---|
| `propertyId` | always | the id asked for |
| `rollYear` | single-year modes | the roll year shown (the latest one by default) |
| `history` | always | `true` for the history mode |
| `features` | always | the records; `[]` when the property has none for the scope asked |
| `page`, `size`, `totalElements`, `totalPages` | history only | paging of the returned page |

Each item of `features` is an `ExtraFeatureDTO`: `rollYear`, `description`, `featureType`, `useCode`,
`units`, `area`, `value`, `depreciatedValue`, `adjustedUnitPrice`, `percentCondition`,
`actualYearBuilt`, `message`.

### Examples

Default (latest year):

```
GET /api/properties/id/1/extra-features

{ "propertyId": 1, "rollYear": 2026, "history": false,
  "features": [
    { "rollYear": 2026, "description": "POOL", "units": 450.0, "value": 18000.0, ... },
    { "rollYear": 2026, "description": "DECK", ... },
    { "rollYear": 2026, "description": "FENCE", ... } ] }
```

One year:

```
GET /api/properties/id/1/extra-features?year=2024

{ "propertyId": 1, "rollYear": 2024, "history": false, "features": [ ... ] }
```

History, second page of 4:

```
GET /api/properties/id/1/extra-features?history=true&page=1&size=4

{ "propertyId": 1, "history": true,
  "features": [ { "rollYear": 2025, ... }, { "rollYear": 2025, ... }, { "rollYear": 2024, ... }, { "rollYear": 2024, ... } ],
  "page": 1, "size": 4, "totalElements": 9, "totalPages": 3 }
```

Existing property without features:

```
GET /api/properties/id/222/extra-features

{ "propertyId": 222, "history": false, "features": [] }
```

## Errors

| Case | Status | Body |
|---|---|---|
| no property with that id | `404` | `{ "status": 404, "error": "Not Found", "message": "No property with id 99999999999", ... }` |
| `id` not a number | `400` | Spring's type-mismatch error |
| `year` and `history=true` together | `400` | `"message": "'year' and 'history=true' cannot be combined: ..."` |
| `year` outside 1900–2100 | `400` | `"message": "'year' must be a roll year between 1900 and 2100, got 1800"` |
| `page` < 0 or `size` < 1 | `400` | `"message": "'page' must be 0 or more and 'size' at least 1"` |

## Implementation

`PropertyController.getExtraFeatures` → `ExtraFeatureService.getExtraFeatures` (rules, 400/404) →
`ExtraFeatureRepository` (three queries on `extra_features` by `property_id`) → `ExtraFeatureMapper`.
No schema change; `extra_features.roll_year` is the roll year, `idx_extra_features_property_id`
serves every query. Unit tests: `ExtraFeatureServiceImplTest`.
