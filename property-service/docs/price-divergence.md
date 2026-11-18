# Property price divergence — `GET /api/properties/id/{id}/price-divergence`

Story SCRUM-60 "Explain property price divergence". Depends on the ZentraValue valuations
(`property_value_predictions`, SCRUM-58) and their explanation factors.

## What it answers

"Why is this property's value different from the properties around it?" The endpoint compares the property's
ZentraValue estimate with three benchmarks, shows which valuation factors account for the gap, and states what is left
unexplained and how confident the explanation is. It is read-only and calls no provider.

Two rules apply everywhere:

1. **Nothing is estimated here.** Every amount comes from stored estimates and the model's own valuation factors.
   No driver percentage is calculated.
2. **Missing evidence is shown, never filled.** A value without enough evidence is `null`, its block has
   `status: UNAVAILABLE`, and `reason` says why.

## Benchmarks

| Level | Group (same as the comparable search) | Key shown in `groupKey` |
|---|---|---|
| `BUILDING` | units with the same `parent_property_id` | parent property ID |
| `COMMUNITY` | same county and subdivision (`LOWER(BTRIM(subdivision))`) | subdivision |
| `MICRO_MARKET` | same county and neighborhood (`LOWER(BTRIM(neighborhood))`) | neighborhood |

A blank or `0` subdivision or neighborhood counts as missing.

For each level, the sample is the **other** properties of the group (the property itself is excluded) whose stored
estimate has status `supported` or `limited` and a price above 0. Product decision: benchmarks use the **ZentraValue
estimates** of these properties (`basis: ZENTRAVALUE_ESTIMATES`), not completed sales.

- `medianValue`: median estimate of the sample. `divergenceUsd` = property estimate − `medianValue`;
  `divergencePct` = `divergenceUsd` × 100 ÷ `medianValue`, 2 decimals.
- `medianPricePerSqft`: median estimate per sq ft of the sample members with a heated living area. Information only.
- `valuationsFrom` / `valuationsTo`: oldest and newest prediction dates in the sample. They are equal after a full
  model run and differ when targeted refreshes revalued some members.
- A level needs **at least 5** properties in its sample. Otherwise it is `UNAVAILABLE`, with the sample size.

`primaryBenchmark` is the most specific `AVAILABLE` level (`BUILDING`, then `COMMUNITY`, then `MICRO_MARKET`).

## Drivers and unexplained residual

The model stores, for every estimate, the dollar effect of each factor (`valuation_factors`: `living_area`,
`location`, `age_and_condition`, ..., `other`). The drivers compare the property's factors with the **average**
factors of the primary benchmark sample:

- `subjectUsd`: the factor's effect on the property (0 when the model did not list it);
- `groupAverageUsd`: its average effect in the sample (a member that does not list it counts 0);
- `differenceUsd` = `subjectUsd` − `groupAverageUsd`. Factors are sorted by the size of the difference.

`explainedUsd` is the sum of the differences, and `unexplainedResidual.usd` = primary `divergenceUsd` −
`explainedUsd`. The residual also contains the gap between the sample median (used by the benchmark) and the
sample average (used by the drivers).

Sample members whose factors cannot be read are skipped. The drivers need factors for the property and for at least
5 sample members; otherwise drivers and residual are `UNAVAILABLE`.

The model's own `pct` values are relative to a county-wide baseline, not to the benchmark group, so they are not
shown here.

## Confidence

`confidence.level` is the confidence stored with the property's estimate (for example `MEDIUM`), and `reason` names
the benchmark and its sample size. It is `UNAVAILABLE` without a primary benchmark or without an estimate.

## Asking price

`askingPriceComparison` is kept separate from the valuation benchmarks. No asking price is recorded in GeoZentra, so
it is always `UNAVAILABLE` with the reason "No asking-price data is recorded".

## Access

Public, like `GET /api/properties/id/{id}/valuation`: no token is needed and no ownership applies (property data is
public county data).

| Response | When |
|---|---|
| 200 | the property exists; missing evidence is reported inside the response |
| 400 | `id` is 0, negative or not a number |
| 404 | no property has this ID |

## Example

Illustrative values (the local database has no stored valuations to show real ones):

```
{
  "propertyId": 1542,
  "folio": "0141390012345",
  "observedAt": "2026-11-18T10:00:00Z",
  "subject": { "valuationStatus": "supported", "estimate": 520000, "livingAreaSqft": 1000,
               "pricePerSqft": 520.00, "valuationDate": "2026-11-10", "valuationConfidence": "medium" },
  "benchmarks": [
    { "level": "BUILDING", "status": "UNAVAILABLE", "groupKey": "900", "basis": "ZENTRAVALUE_ESTIMATES",
      "sampleSize": 3, "medianValue": null, "medianPricePerSqft": null, "divergenceUsd": null,
      "divergencePct": null, "valuationsFrom": "2026-10-01", "valuationsTo": "2026-10-01",
      "reason": "Only 3 other valued properties in this group (minimum 5)" },
    { "level": "COMMUNITY", "status": "AVAILABLE", "groupKey": "CORAL GARDENS SUB", "basis": "ZENTRAVALUE_ESTIMATES",
      "sampleSize": 12, "medianValue": 480000.00, "medianPricePerSqft": 455.10, "divergenceUsd": 40000.00,
      "divergencePct": 8.33, "valuationsFrom": "2026-10-01", "valuationsTo": "2026-11-10", "reason": null },
    { "level": "MICRO_MARKET", "status": "AVAILABLE", "groupKey": "3200", "basis": "ZENTRAVALUE_ESTIMATES",
      "sampleSize": 47, "medianValue": 450000.00, "medianPricePerSqft": 430.00, "divergenceUsd": 70000.00,
      "divergencePct": 15.56, "valuationsFrom": "2026-10-01", "valuationsTo": "2026-11-10", "reason": null }
  ],
  "primaryBenchmark": "COMMUNITY",
  "drivers": {
    "status": "AVAILABLE", "source": "ZentraValue valuation factors", "comparedWith": "COMMUNITY", "sampleSize": 12,
    "factors": [
      { "code": "age_and_condition", "label": "Age and condition", "subjectUsd": 12000.00,
        "groupAverageUsd": -5000.00, "differenceUsd": 17000.00 },
      { "code": "living_area", "label": "Living area", "subjectUsd": 60000.00,
        "groupAverageUsd": 45000.00, "differenceUsd": 15000.00 },
      { "code": "location", "label": "Location", "subjectUsd": 40000.00,
        "groupAverageUsd": 38000.00, "differenceUsd": 2000.00 }
    ],
    "explainedUsd": 34000.00, "reason": null
  },
  "unexplainedResidual": { "status": "AVAILABLE", "benchmark": "COMMUNITY", "usd": 6000.00, "reason": null },
  "confidence": { "level": "MEDIUM",
                  "reason": "COMMUNITY benchmark from 12 valued properties; valuation confidence MEDIUM" },
  "askingPriceComparison": { "status": "UNAVAILABLE", "askingPrice": null, "differenceUsd": null,
                             "reason": "No asking-price data is recorded" },
  "note": "Benchmarks are the median ZentraValue estimates of other properties in the same group, ..."
}
```

A property without a stored estimate still returns 200: `subject.estimate` is `null`, the benchmarks show
`medianValue` with `divergenceUsd: null` and the reason "The property has no estimate to compare", and drivers,
residual and confidence are `UNAVAILABLE`.

Errors use the shared `ApiError` body:

```
{ "status": 404, "error": "Not Found", "message": "Property not found: 999999999", "timestamp": "..." }
```

## Manual checks

Postman collection: `docs/postman/SCRUM-60-price-divergence.postman_collection.json` (success, property without a
valuation, unknown property, invalid IDs). No token is needed.
