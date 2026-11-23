# Investor dashboard — `GET /api/dashboards/investor`

Story SCRUM-107, part of the investor dashboard (SCRUM-85). It returns the four investor cards in one response:

| Field | Card | Story | Own endpoint |
|---|---|---|---|
| `valueEquityDebt` | value, equity and debt | SCRUM-103 | `/api/dashboards/investor/value-equity-debt` |
| `incomeExpensesReturns` | income, expenses and returns | SCRUM-104 | `/api/dashboards/investor/income-expenses-returns` |
| `occupancyExposure` | occupancy and exposure | SCRUM-105 | `/api/dashboards/investor/occupancy-exposure` |
| `opportunities` | opportunities | SCRUM-106 | `/api/dashboards/investor/opportunities` |

Nothing is recalculated: each card is the response of its own service, unchanged. The dashboard is read-only. See
each card's doc for its fields.

## Request

A bearer token is required. Every parameter is optional.

| Parameter | Format | Meaning |
|---|---|---|
| `portfolioId` | number | one portfolio of the current user; all of them (up to 100) when omitted |
| `fromMonth` | `yyyy-MM` | first month of the income card, included |
| `toMonth` | `yyyy-MM` | last month of the income card, included |

- **Scope:** the same `portfolioId` is passed to the four cards, so they all cover the same portfolios.
- **Period:** only the income, expenses and returns card uses a period. The rules are the same as for that card:
  give both months or none (the last 12 complete months, UTC), `toMonth` before the current month, `fromMonth` not
  after `toMonth` and not before `1900-01`, at most 120 months. The other cards show the current state (`asOfDate`).
- **Opportunities:** the card uses its default limit (5) and no county or state filter. Call
  `/api/dashboards/investor/opportunities` to filter.

## Order of the checks

1. `portfolioId` and the period are checked first. An invalid value fails the **whole** request with 400; no card
   is loaded.
2. The portfolio must belong to the current user. Otherwise the whole request fails with 404; no card is loaded.
3. Then each card is loaded on its own (see below).

## Partial failures

When one card cannot be loaded (for example a database error in the opportunity scores), the other cards are kept:

- the failed card keeps its place with `status: ERROR`, a generic `error` message, the scope and `generatedAt`; its
  other fields are null. The technical cause is written to the service log, not sent to the client;
- its name is added to `failedCards`;
- the dashboard `status` is `PARTIAL`.

The dashboard service is not transactional on purpose: each card runs in its own transaction, so one failing card
cannot roll back the others.

## Response

```
{
  "scope": "PORTFOLIO",
  "portfolioId": 5,
  "portfolioCount": 1,
  "status": "PARTIAL",
  "failedCards": [ "opportunities" ],
  "generatedAt": "2026-11-23T10:00:00Z",
  "period": { "fromMonth": "2026-01", "toMonth": "2026-03", "fromDate": "2026-01-01",
              "toDate": "2026-03-31", "months": 3, "timeZone": "UTC" },
  "valueEquityDebt":       { "status": "OK", "totalHoldings": 2, ... },
  "incomeExpensesReturns": { "status": "OK", "period": { ... same period ... }, "actual": { ... }, "projected": { ... } },
  "occupancyExposure":     { "status": "OK", "totalHoldings": 2, ... },
  "opportunities":         { "scope": "PORTFOLIO", "portfolioId": 5, "portfolioCount": 1, "status": "ERROR",
                             "error": "This card could not be loaded; the other cards are not affected.",
                             "generatedAt": "2026-11-23T10:00:00Z", "items": null, ... },
  "note": "The four investor cards for the same portfolio scope. ..."
}
```

## Status

| `status` | When |
|---|---|
| `OK` | every card loaded, at least one is not empty |
| `EMPTY` | every card loaded and every card is `EMPTY` |
| `PARTIAL` | one to three cards failed (`failedCards`); the others are kept |
| `ERROR` | the four cards failed; the response is still 200 with the four ERROR cards |

## Empty and incomplete data

| Situation | Response |
|---|---|
| no portfolio, or no holding | 200; value, income and occupancy cards `EMPTY`; dashboard `EMPTY`, or `OK` when stored opportunity scores exist |
| holdings but no record in the period | 200; income card `actual.status: NO_RECORDS`, every expense category listed as missing |
| unknown value, debt, occupancy or state | 200; the coverage percentages of the cards show what is missing |
| no opportunity score | 200; opportunities card `EMPTY` with its `candidateStatus` |
| a card fails | 200; that card `ERROR`, dashboard `PARTIAL` |

Missing data is never shown as 0: each card keeps its own rules (missing costs, coverage, `null` totals).

## Access and errors

Only the current user's portfolios are read. A portfolio of another user returns the same 404 as a missing one.

| Response | When |
|---|---|
| 200 | the dashboard (`OK`, `EMPTY`, `PARTIAL` or `ERROR`) |
| 400 | `portfolioId` not a number or not positive; a month not `yyyy-MM`; only one month given; `fromMonth` after `toMonth`; `toMonth` not before the current month; `fromMonth` before 1900-01; more than 120 months |
| 401 | no bearer token |
| 404 | the portfolio does not exist or belongs to another user |

Errors use the shared `ApiError` body:

```
{ "status": 404, "error": "Not Found", "message": "Portfolio not found", "timestamp": "..." }
```

## Manual checks

Postman collection: `docs/postman/SCRUM-107-investor-dashboard.postman_collection.json`. Run the folders in order
with the Collection Runner. It needs a running service, a Keycloak realm, two users (A owns the data, B is another
investor) and two existing property IDs. Each run creates its own portfolio and deletes it at the end, so it can be
repeated.

A card failure cannot be caused from Postman without breaking the database. To see `PARTIAL` locally, run the
service in the debugger with a breakpoint in one card service (for example `InvestorOpportunitiesServiceImpl.getCard`),
throw an exception from the breakpoint (IntelliJ: right click the frame, "Throw Exception",
`new RuntimeException("test")`) and let the request finish: the other cards are returned, the failed card has
status `ERROR` and is listed in `failedCards`.
