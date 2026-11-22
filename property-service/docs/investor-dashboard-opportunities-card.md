# Investor dashboard, opportunities card — `GET /api/dashboards/investor/opportunities`

Subtask SCRUM-106 of SCRUM-85 "Implement GET /api/dashboards/investor". Sibling cards: value-equity-debt
(SCRUM-103) and occupancy-exposure (SCRUM-105); the composed endpoint is SCRUM-107. Depends on SCRUM-76
(investment opportunities), whose shared `OpportunityBrowseService` this card calls.

## What the card is

A **bounded list** of the best stored opportunity scores, as the shared opportunity service ranks and explains
them, with one personal addition: whether the investor already holds the property. The card computes no score,
no formula and no ranking of its own.

Where the opportunities come from:

1. ZentraValue stores a valuation and forecasts per property (`property_value_predictions`, SCRUM-58).
2. The SCRUM-76 refresh scores every valued property with the documented rules `opportunity-v1` and stores the
   result in `property_opportunity_scores` (score, coverage, the seven factors, county, state, estimate, dates).
3. `OpportunityBrowseService.browse` pages through those stored scores, `SCORED` only, best first. It is what
   `GET /api/opportunities` uses, and what this card calls with page 0, `size = limit`, sort `SCORE_DESC` and the
   optional `countyId` / `stateId` filters.

Scores come from stored valuations, not from listings: a listed property is **not** known to be for sale. The
shared `notes` say so, and the card passes them through unchanged.

### Reused, not re-implemented

| Acceptance point | How |
|---|---|
| ranking explanations | each item carries the shared service's seven `factors` (evidence, subscore, points, explanation, or why unavailable) exactly as `GET /api/opportunities` returns them |
| evidence coverage | `coveragePercent` of each item is the shared value (sum of the weights of the available factors; at most 50 with today's data) |
| empty candidate set | when the shared page is empty the card is `EMPTY`, `items` is `[]`, `totalCandidates` is 0 and `candidateStatus` carries the shared service's own reason; no placeholder item or number is ever produced |
| ownership | the bearer token identifies the investor; `portfolioId` must be one of their portfolios (foreign or missing: 404); the scores themselves are public property data, no ownership applies to them |

## Access and ownership

`/api/dashboards/**` requires a bearer token (401 without). The user behind the token is the investor:
`alreadyHeld` is computed only against that user's portfolios. With `portfolioId`, only that portfolio's
holdings are flagged, and a portfolio of another user answers 404 exactly like a missing one, so IDs cannot be
probed. A user without portfolios gets the same public list with `portfolioCount` 0 and every flag false.

## Request

| Parameter | Type | Default | Rule |
|---|---|---|---|
| `portfolioId` | integer | all my portfolios | positive; one of the current user's portfolios, else 404 |
| `countyId` | integer | not applied | positive; passed to the shared service |
| `stateId` | integer | not applied | positive; passed to the shared service |
| `limit` | integer | 5 | 1 to 20 |

## Response

| Field | Meaning |
|---|---|
| `scope`, `portfolioId`, `portfolioCount` | `ALL_PORTFOLIOS` or `PORTFOLIO`; portfolios whose holdings are flagged |
| `status` | `OK` with at least one item; `EMPTY` without; `ERROR` only inside the composed dashboard (SCRUM-107) |
| `candidateStatus` | shared service status: `OK`, `NO_MATCHES` (filters exclude every scored property), `NO_ELIGIBLE_OPPORTUNITIES` (scores stored but none has the essential evidence), `NOT_SCORED` (refresh never ran for this version) |
| `scoreVersion` | version of the listed scores |
| `countyId`, `stateId`, `limit` | applied filters and bound |
| `totalCandidates` | scored properties matching the filters, before the bound |
| `items[]` | best first (score descending, ties by property ID): property details, `score`, `maxScore`, `coveragePercent`, `estimatedValue`, `forecastGrowth1yPct`, `factors[]`, `valuationDataThrough`, `scoredAt`, `alreadyHeld` |
| `notes` | the shared service's reading rules |
| `note` | how the card is built |

| Status | When |
|---|---|
| 200 | the card, `OK` or `EMPTY` |
| 400 | `portfolioId`, `countyId` or `stateId` not positive or not a number; `limit` outside 1–20 or not a number |
| 401 | no bearer token |
| 404 | `portfolioId` missing or owned by another user |

Response of `?limit=2` on the local seed (user A holds property 1), factors abridged:

```
{
  "scope": "ALL_PORTFOLIOS", "portfolioId": null, "portfolioCount": 2,
  "status": "OK", "error": null, "generatedAt": "2026-11-19T12:16:29.699812300Z",
  "scoreVersion": "opportunity-v1", "candidateStatus": "OK",
  "countyId": null, "stateId": null, "limit": 2, "totalCandidates": 5,
  "items": [
    { "propertyId": 1, "folio": "TEST-FOLIO-1", "address": null, "city": null, "zip": null,
      "countyName": "Miami-Dade", "stateCode": "FL",
      "score": 44.49, "maxScore": 100, "coveragePercent": 50.00,
      "estimatedValue": 525636.50, "forecastGrowth1yPct": 10.32,
      "factors": [
        { "code": "FORECAST_GROWTH_1Y", "label": "One-year forecast growth", "category": "MARKET", "weight": 20,
          "status": "AVAILABLE", "signal": 10.32, "signalUnit": "PERCENT", "subscore": 100.00, "points": 20.00,
          "explanation": "One-year forecast 10.32% against the current estimate. Scale: -10% scores 0, +10% scores 100." },
        ...,
        { "code": "RENTAL_YIELD", "label": "Rental yield", "category": "RENT", "weight": 20, "status": "UNAVAILABLE",
          "signal": null, "signalUnit": null, "subscore": null, "points": null,
          "explanation": "Unavailable: No rent estimate is stored for this property, so rental yield cannot be measured. It adds no points." },
        ...
      ],
      "valuationDataThrough": "2026-07-26", "scoredAt": "2026-11-19T12:13:22.781863Z", "alreadyHeld": true },
    { "propertyId": 2, "folio": "TEST-FOLIO-2", ..., "score": 38.10, "coveragePercent": 50.00, ..., "alreadyHeld": false }
  ],
  "notes": [ "Version opportunity-v1 scores the stored ZentraValue valuation and market forecasts; ...",
             "Unavailable factors add no points and are never treated as zero costs: ...",
             "Forecasts are model trend scenarios, not guarantees, and no listing price is known: ..." ],
  "note": "First page of the shared opportunity browse (GET /api/opportunities, SCRUM-76) in score order, bounded by limit. ..."
}
```

Empty candidate set, before any refresh has run (same shape, nothing invented):

```
{ "scope": "ALL_PORTFOLIOS", "portfolioId": null, "portfolioCount": 2, "status": "EMPTY", "error": null,
  "generatedAt": "2026-11-19T12:16:31.589094200Z", "scoreVersion": "opportunity-v1", "candidateStatus": "NOT_SCORED",
  "countyId": null, "stateId": null, "limit": 5, "totalCandidates": 0, "items": [], "notes": [ ... ], "note": "..." }
```

## Errors

Shared `GlobalExceptionHandler` shape `{status, error, message, timestamp}`:

| Case | Status | Message |
|---|---|---|
| `limit` outside 1–20 | 400 | `limit must be between 1 and 20` |
| `portfolioId` / `countyId` / `stateId` ≤ 0 | 400 | `<name> must be a positive number` |
| a parameter that is not a number | 400 | `<name>: invalid value` |
| no token | 401 | |
| portfolio missing or foreign | 404 | `Portfolio not found` |

## Implementation

| File | Role |
|---|---|
| `dto/InvestorOpportunitiesCardDTO.java` | the card and its `Item` record |
| `service/InvestorOpportunitiesService.java` | interface; `DEFAULT_LIMIT` 5, `MAX_LIMIT` 20 |
| `service/impl/InvestorOpportunitiesServiceImpl.java` | validation, ownership (same as the sibling cards), one `browse` call, one holdings query, mapping |
| `repository/PortfolioHoldingRepository.java` | `findHeldPropertyIds(portfolioIds, propertyIds)` added, one query per card |
| `controller/InvestorDashboardController.java` | `GET /opportunities` added next to the two existing cards |
| `docs/postman/SCRUM-106-investor-opportunities.postman_collection.json` | manual verification |

No migration: the card reads tables that already exist (`property_opportunity_scores` from V40, portfolios,
holdings). Nothing is written. No automated test was added (ticket); the existing
`InvestorDashboardControllerTest` only gained a mock of the new service so the controller still builds.

For SCRUM-107: call `InvestorOpportunitiesService.getCard(null, null, null, null)` for the default card; the
`ERROR` status and `error` field are reserved for the composed dashboard to report a failed card without hiding
the others.

## Manual verification (Postman)

Collection `docs/postman/SCRUM-106-investor-opportunities.postman_collection.json`. Variables: `baseUrl`,
`user_a_token` (an investor with portfolios), `admin_token` (any user without portfolios), `portfolioId` (one of
user A's), `heldPropertyId` (a scored property held in that portfolio), `otherUsersPortfolioId`, `countyId` and
`countyName` (a county with scored properties). The database must hold at least one `SCORED` row of the current
version; locally, run the SCRUM-76 refresh or seed a few rows.

| # | Request | Expect |
|---|---|---|
| 01 | without token | 401 |
| 02 | default card | 200 OK, ≤ 5 items best first, every item with 7 factors and `coveragePercent`, notes present |
| 03 | default card | `alreadyHeld` true for the held property, false elsewhere |
| 04 | `portfolioId` = mine | 200, scope `PORTFOLIO`, `portfolioCount` 1, only that portfolio's holding flagged |
| 05 | `portfolioId` of another user | 404 |
| 06 | `portfolioId` 999999 | 404 |
| 07 | `limit=2` | 200, exactly 2 items, `totalCandidates` unchanged, same first item |
| 08–10 | `limit` 0, 21, `abc` | 400 |
| 11 | `countyId=0` | 400 |
| 12 | `portfolioId=-1` | 400 |
| 13 | `countyId` with scores | 200, every item in that county, filter echoed |
| 14 | `countyId=999999` | 200, `EMPTY`, `candidateStatus` `NO_MATCHES`, `items` `[]`, `totalCandidates` 0 |
| 15 | user without portfolios | 200 OK, `portfolioCount` 0, every `alreadyHeld` false |

Run on 2026-11-19 against the local PostgreSQL and Keycloak with a seed of five `SCORED` and one `UNAVAILABLE`
score: 15 of 15, plus extra checks (second investor sees the same list with only their holding flagged, `stateId`
filter, `limit=20`, `portfolioId` + `countyId` together, non-numeric `portfolioId`). The `UNAVAILABLE` property
was never listed. The two other empty reasons were produced by flipping the seed rows: all rows `UNAVAILABLE`
gives `EMPTY` / `NO_ELIGIBLE_OPPORTUNITIES`, rows of another `scoreVersion` give `EMPTY` / `NOT_SCORED`, both
with `items: []` and `totalCandidates: 0`.
