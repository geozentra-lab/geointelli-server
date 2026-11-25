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
  `divergencePct` = `divergenceUsd` × 100 ÷ `medianValue`, 2 decimals. The median is the headline comparison: it
  is the typical neighbour and a few very high or low estimates do not move it.
- `averageValue`: average estimate of the sample. Shown so the skew of the group is visible: when it is far from
  `medianValue`, a few members are much higher (or lower) than the rest.
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

Because the drivers compare with the group **average**, they explain the gap from the average estimate, not from
the median:

- `groupAverageValue`: average estimate of the same members whose factors are compared (usually the whole sample,
  so it equals the primary benchmark's `averageValue`);
- `divergenceFromAverageUsd` = property estimate − `groupAverageValue`;
- `explainedUsd`: the sum of the differences;
- `unexplainedResidual.usd` = `divergenceFromAverageUsd` − `explainedUsd`.

So `divergenceFromAverageUsd` = `explainedUsd` + `unexplainedResidual.usd`, exactly.

**What the residual means.** The model combines its factors by multiplying them (estimate ≈ baseline ×
(1 + pct₁) × (1 + pct₂) × …), so the dollar amount of each factor is an approximation and the amounts do not add up
exactly to the estimate. Factors that reinforce each other — for example a large living area in an expensive
location — add more together than their separate amounts. The residual is that combined effect: the part of the
gap from the group average that no single factor accounts for. A small residual compared with
`divergenceFromAverageUsd` means the listed drivers explain the gap well; a large one means their amounts should be
read as indications only. It never contains the median-to-average gap, which is shown by `medianValue` and
`averageValue`.

Sample members whose factors cannot be read are skipped, from both averages. The drivers need factors for the
property and for at least 5 sample members; otherwise drivers and residual are `UNAVAILABLE`.

The model's own `pct` values are relative to a county-wide baseline, not to the benchmark group, so they are not
shown here.

## Confidence

`confidence.level` is the confidence stored with the property's estimate (for example `MEDIUM`), and `reason` names
the benchmark and its sample size. It is `UNAVAILABLE` without a primary benchmark or without an estimate.

## Asking price

`askingPriceComparison` is kept separate from the valuation benchmarks. No asking price is recorded in GeoZentra, so
it is always `UNAVAILABLE` with the reason "No asking-price data is recorded".

## Access

Any signed-in user, like `GET /api/properties/id/{id}/valuation`: a bearer token is required (SCRUM-94 baseline
property reads, no realm role needed; 401 without a token). No ownership applies: property data is public county
data.

| Response | When |
|---|---|
| 200 | the property exists; missing evidence is reported inside the response |
| 400 | `id` is 0, negative or not a number |
| 404 | no property has this ID |

## Example

Property 1 of the local database (factors shortened to the three largest differences):

```
{
  "propertyId": 1,
  "folio": "3049340031180",
  "observedAt": "2026-11-21T17:31:56Z",
  "subject": { "valuationStatus": "limited", "estimate": 621063.48, "livingAreaSqft": 1075.00,
               "pricePerSqft": 577.73, "valuationDate": "2026-11-08", "valuationConfidence": "medium" },
  "benchmarks": [
    { "level": "BUILDING", "status": "UNAVAILABLE", "groupKey": null, "basis": "ZENTRAVALUE_ESTIMATES",
      "sampleSize": 0, "medianValue": null, "averageValue": null, "medianPricePerSqft": null, "divergenceUsd": null,
      "divergencePct": null, "valuationsFrom": null, "valuationsTo": null,
      "reason": "The property is not a unit of a building" },
    { "level": "COMMUNITY", "status": "AVAILABLE", "groupKey": "304934003", "basis": "ZENTRAVALUE_ESTIMATES",
      "sampleSize": 128, "medianValue": 766936.52, "averageValue": 774572.10, "medianPricePerSqft": 441.45,
      "divergenceUsd": -145873.04, "divergencePct": -19.02, "valuationsFrom": "2026-11-08",
      "valuationsTo": "2026-11-08", "reason": null },
    { "level": "MICRO_MARKET", "status": "UNAVAILABLE", "groupKey": null, "basis": "ZENTRAVALUE_ESTIMATES",
      "sampleSize": 0, "medianValue": null, "averageValue": null, "medianPricePerSqft": null, "divergenceUsd": null,
      "divergencePct": null, "valuationsFrom": null, "valuationsTo": null,
      "reason": "No neighborhood is recorded for the property" }
  ],
  "primaryBenchmark": "COMMUNITY",
  "drivers": {
    "status": "AVAILABLE", "source": "ZentraValue valuation factors", "comparedWith": "COMMUNITY", "sampleSize": 128,
    "groupAverageValue": 774572.10, "divergenceFromAverageUsd": -153508.62,
    "factors": [
      { "code": "county_assessment", "label": "County assessment", "subjectUsd": -61262.00,
        "groupAverageUsd": -3344.98, "differenceUsd": -57917.02 },
      { "code": "living_area", "label": "Living area", "subjectUsd": -47842.00,
        "groupAverageUsd": -7197.13, "differenceUsd": -40644.87 },
      { "code": "market_timing", "label": "Market timing", "subjectUsd": -44594.00,
        "groupAverageUsd": -11948.79, "differenceUsd": -32645.21 },
      ...
    ],
    "explainedUsd": -154934.99, "reason": null
  },
  "unexplainedResidual": { "status": "AVAILABLE", "benchmark": "COMMUNITY", "usd": 1426.37, "reason": null },
  "confidence": { "level": "MEDIUM",
                  "reason": "COMMUNITY benchmark from 128 valued properties; valuation confidence MEDIUM" },
  "askingPriceComparison": { "status": "UNAVAILABLE", "askingPrice": null, "differenceUsd": null,
                             "reason": "No asking-price data is recorded" },
  "note": "Benchmarks are the median ZentraValue estimates of other properties in the same group, ..."
}
```

Reading it: the property is $145,873 (19.02%) below the typical (median) home of its subdivision. A few higher
estimates put the subdivision's average at $774,572, $7,636 above its median. Against that average the property is
$153,509 below; the factor differences account for −$154,935 of it, mostly a lower county assessment, a smaller
living area and market timing. The remaining $1,426 is the factors' combined effect, so the drivers explain this gap
well.

A property without a stored estimate still returns 200: `subject.estimate` is `null`, the benchmarks show
`medianValue` with `divergenceUsd: null` and the reason "The property has no estimate to compare", and drivers,
residual and confidence are `UNAVAILABLE`.

Errors use the shared `ApiError` body:

```
{ "status": 404, "error": "Not Found", "message": "Property not found: 999999999", "timestamp": "..." }
```

## Manual checks

Postman collection: `docs/postman/SCRUM-60-price-divergence.postman_collection.json` (success, property without a
valuation, unknown property, invalid IDs, no token). Set `access_token` to any user's token.
