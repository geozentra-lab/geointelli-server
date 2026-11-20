# Investor dashboard: income, expenses and returns card — `GET /api/dashboards/investor/income-expenses-returns`

Story SCRUM-104, part of the investor dashboard (SCRUM-85). Depends on the portfolio summaries (SCRUM-74) and the
investment calculations (SCRUM-64).

## What it answers

"Over these months, how much did my holdings really bring in and cost, and how much should their leases bring in?"
The card has two separate sections over the **same whole months**:

- `actual`: the recorded cash (financial records) of the period;
- `projected`: the monthly contract rent of the rented holdings for the same months, and the gross yield.

The two sections are never added together. The card is read-only.

## Request

A bearer token is required. Every parameter is optional.

| Parameter | Format | Meaning |
|---|---|---|
| `portfolioId` | number | one portfolio of the current user; all of them (up to 100) when omitted |
| `fromMonth` | `yyyy-MM` | first month, included |
| `toMonth` | `yyyy-MM` | last month, included |

Give both months or none. Without months, the period is the **last 12 complete months** (UTC): on 2026-11-20 it is
2025-11 to 2026-10.

Rules:

- only complete months: `toMonth` must be before the current month (UTC). In the current month the recorded cash is
  not complete yet, so it would not be comparable with a full month of contract rent;
- `fromMonth` not after `toMonth`, not before `1900-01`, and at most 120 months.

## Period

`period` gives the months and the matching dates: `fromDate` is the first day of `fromMonth`, `toDate` the last day of
`toMonth`, and `months` the number of months. The same period is used by both sections.

## Actual (`actual`)

The SCRUM-74 financial summary of each included portfolio (`PortfolioFinancialSummaryService`), added up:

- records are counted by `transactionDate` between `fromDate` and `toDate`, both included;
- every amount is the full-property amount × the **current** ownership share, rounded to cents;
- `income`, `expenses`, `cashFlow` = income − expenses (recorded cash, not profit);
- `categories`: the amount and number of records per category, in category order;
- `expenseCategoriesWithoutRecords`: expense categories with no record in the period. They are **missing costs**,
  not costs of 0 (for example no insurance recorded);
- `holdingsWithRecords` / `holdingsWithoutRecords`: holdings with and without any record in the period;
- `status`: `RECORDED`, or `NO_RECORDS` when the period has no record.

## Projected (`projected`)

The contract rent of the holdings reported as `RENTED` with a monthly rent, run through the SCRUM-64 investment
calculation (`InvestmentAnalysisService`). No other formula is used.

- `monthlyGrossRent`: sum of the monthly rents × current ownership share;
- `annualGrossRent`: from the calculation (monthly × 12);
- `periodGrossRent` = `monthlyGrossRent` × `period.months`. This is the figure to compare with `actual.income`;
- `grossYieldPercent` = annual gross rent ÷ purchase price × 100 (SCRUM-64). It is `null` when a rented holding has no
  purchase price;
- `netOperatingIncome` is always `null`: holdings store no vacancy rate, tax, insurance or other operating cost, so a
  projected NOI would only repeat the rent. Recorded costs are not used, to keep actual and projected apart;
- `missingInputs`: every input the projection lacks: rented holdings without rent or purchase price, and the notes
  of the SCRUM-64 calculation (vacancy, maintenance, management, tax, insurance, HOA, other costs);
- `status`: `AVAILABLE`, or `UNAVAILABLE` with a `reason` when no rented holding has a rent ("No holdings" or
  "No rented holding has a monthly contract rent").

The projection uses the holdings as they are today; lease dates do not change it.

## Response

```
{
  "scope": "PORTFOLIO",
  "portfolioId": 5,
  "portfolioCount": 1,
  "status": "OK",
  "error": null,
  "generatedAt": "2026-11-20T10:00:00Z",
  "currency": "USD",
  "period": { "fromMonth": "2025-11", "toMonth": "2026-10", "fromDate": "2025-11-01",
              "toDate": "2026-10-31", "months": 12, "timeZone": "UTC" },
  "totalHoldings": 2,
  "actual": {
    "basis": "ACTUAL_RECORDED_CASH",
    "status": "RECORDED",
    "income": 40500.00,
    "expenses": 14000.00,
    "cashFlow": 26500.00,
    "recordCount": 38,
    "holdingsWithRecords": 2,
    "holdingsWithoutRecords": 0,
    "categories": [
      { "category": "RENT", "type": "INCOME", "amount": 40500.00, "recordCount": 23 },
      { "category": "REPAIRS", "type": "EXPENSE", "amount": 2000.00, "recordCount": 3 },
      { "category": "MORTGAGE_PAYMENT", "type": "EXPENSE", "amount": 12000.00, "recordCount": 12 }
    ],
    "expenseCategoriesWithoutRecords": [ "MAINTENANCE", "INSURANCE", "PROPERTY_TAX", "HOA", "UTILITIES",
                                         "MANAGEMENT_FEE", "OTHER_EXPENSE" ],
    "recordsUpdatedAt": "2026-10-30T14:12:00"
  },
  "projected": {
    "basis": "CONTRACT_RENT_INVESTMENT_CALCULATION",
    "status": "AVAILABLE",
    "rentedHoldings": 2,
    "holdingsWithRent": 2,
    "monthlyGrossRent": 3500.00,
    "annualGrossRent": 42000.00,
    "periodGrossRent": 42000.00,
    "grossYieldPercent": 8.40,
    "netOperatingIncome": null,
    "missingInputs": [
      "Holdings store no vacancy rate or operating costs: projected net operating income is not calculated.",
      "Vacancy rate not provided: no vacancy deducted, income may be overstated.",
      "Property tax not provided: not included in expenses, NOI may be overstated.",
      "..."
    ],
    "reason": null
  },
  "note": "actual = recorded cash of the period (SCRUM-74 financial summaries), ..."
}
```

## Empty and incomplete data

| Situation | Response |
|---|---|
| no portfolio or no holding | 200, `status: EMPTY`, actual amounts 0 with `NO_RECORDS`, projected `UNAVAILABLE` ("No holdings") |
| holdings but no record in the period | 200, `actual.status: NO_RECORDS`, amounts 0, every expense category listed as missing |
| no rented holding with a rent | 200, `projected.status: UNAVAILABLE` ("No rented holding has a monthly contract rent") |
| a rented holding without purchase price | 200, `grossYieldPercent: null`, reason in `missingInputs` |

`status` is `ERROR` only in the composed dashboard (SCRUM-107), when this card could not be loaded.

## Access and errors

Only the current user's portfolios are read. A portfolio of another user returns the same 404 as a missing one.

| Response | When |
|---|---|
| 200 | the card (`OK` or `EMPTY`) |
| 400 | `portfolioId` not a number or not positive; a month not `yyyy-MM`; only one month given; `fromMonth` after `toMonth`; `toMonth` not before the current month; `fromMonth` before 1900-01; more than 120 months |
| 401 | no bearer token |
| 404 | the portfolio does not exist or belongs to another user |

Errors use the shared `ApiError` body:

```
{ "status": 400, "error": "Bad Request", "message": "fromMonth and toMonth must be given together", "timestamp": "..." }
```

## Manual checks

Postman collection: `docs/postman/SCRUM-104-investor-income-expenses-returns.postman_collection.json`. Run the
folders in order with the Collection Runner. It needs a running service, a Keycloak realm, two users (A owns the
data, B is another investor) and two existing property IDs. Each run creates its own portfolio and deletes it at the
end, so it can be repeated.
