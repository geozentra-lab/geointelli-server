# Collateral analysis — `GET /api/lender/applications/{id}/collateral-analysis` (SCRUM-180 / SCRUM-181)

Part of the lender workflow (parent SCRUM-83). For one application of an organization the user belongs to, the
endpoint returns the loan amount, the stored model valuation of the linked property, the loan-to-value (SCRUM-180)
and the geographic risk context of the property (SCRUM-181). It changes nothing, computes no risk score and makes no
credit decision: every response carries the disclaimer `Informational only; not a credit decision or approval.`

```
GET /api/lender/applications/{id}/collateral-analysis?organizationId={organizationId}
    bearer token with the lender or admin realm role (SCRUM-94)
```

## Access and ownership

| Case | HTTP |
|---|---|
| no token, or an invalid one | 401 |
| signed in without the lender or admin role | 403 |
| `organizationId` missing, not positive or not a number; `id` not positive or not a number | 400 |
| unknown organization, user not a member, or application of another organization | 404 (same body, so a non-member cannot tell which) |

Membership is checked first (`LenderOrganizationService.requireAccess`), before the application, the valuation or
any risk data is read.

## Response

| Field | Meaning |
|---|---|
| `applicationId`, `organizationId`, `propertyId`, `currency` | the application; `propertyId` null when none is linked |
| `loanAmount`, `loanAmountBasis` | funded amount, else approved, else requested (`FUNDED`, `APPROVED`, `REQUESTED`) |
| `collateral` | stored model valuation of the property (SCRUM-58), unchanged: status, value, ranges, confidence, date, model version, reasons; null when no property is linked or found |
| `ltv` | `status` `AVAILABLE`, `LIMITED` or `UNAVAILABLE`; `percent` = loanAmount / value x 100, two decimals, null when unavailable; `reasons` |
| `geography` | location and risk sources of the property (SCRUM-181), see below |
| `disclaimer` | not a credit decision |

### `geography`

| Field | Meaning |
|---|---|
| `status` | `AVAILABLE` every source has evidence; `PARTIAL` some sources missing; `NO_COVERAGE` no source covers the location; `UNAVAILABLE` see `reasons` |
| `reasons` | `NO_COLLATERAL_PROPERTY`, `COLLATERAL_PROPERTY_NOT_FOUND`, `NO_PARCEL_POLYGON` or `RISK_CONTEXT_UNAVAILABLE`; empty when `AVAILABLE` or `PARTIAL` |
| `location` | `folio`, `address`, `city`, `zip`, `county`, `state` as stored; null when no property is found |
| `sources[]` | one per section, in the order `FLOOD` (FEMA NFHL), `HAZARDS` (FEMA National Risk Index), `STORMS` (NOAA Storm Events): `section`, `source`, `status`, `dataAsOf`, `note` |
| `missingCoverage` | sections whose source status is `NO_COVERAGE` (not loaded for the location) or `UNAVAILABLE` (could not be read) |
| `riskContext` | the risk section of SCRUM-127 (`docs/location-intelligence-risk.md`), unchanged; null when it could not be read |

Rules:

- **Reuse, no new risk data.** The risk context is the one of
  `GET /api/properties/id/{id}/location-intelligence/risk` (`PropertyRiskContextService`); the values are FEMA's and
  NOAA's own. No GeoZentra score or rating is added.
- **Missing evidence is shown, never read as low risk.** A source with status `NO_DATA` (loaded, no record at the
  point) has evidence: it is not in `missingCoverage`, and its `note` says why it is not a safe rating. A source
  `NO_COVERAGE` or `UNAVAILABLE` is listed in `missingCoverage`.
- **Sections stay separate.** A missing or failing risk source never hides the valuation or the LTV; an error while
  reading the risk context makes only the geography `UNAVAILABLE` (`RISK_CONTEXT_UNAVAILABLE`, cause in the service
  log).

| Case | `geography.status` | `reasons` | `missingCoverage` |
|---|---|---|---|
| every source has evidence (`AVAILABLE` or `NO_DATA`) | `AVAILABLE` | `[]` | `[]` |
| one or two sources `NO_COVERAGE` / `UNAVAILABLE` | `PARTIAL` | `[]` | those sections |
| the three sources `NO_COVERAGE` / `UNAVAILABLE` | `NO_COVERAGE` | `[]` | `FLOOD`, `HAZARDS`, `STORMS` |
| property without usable parcel polygon | `NO_COVERAGE` | `NO_PARCEL_POLYGON` | `FLOOD`, `HAZARDS`, `STORMS`; `sources` empty |
| risk context unreadable | `UNAVAILABLE` | `RISK_CONTEXT_UNAVAILABLE` | `[]`; `sources` empty |
| application without property | `UNAVAILABLE` | `NO_COLLATERAL_PROPERTY` | `[]`; no location |
| linked property not found | `UNAVAILABLE` | `COLLATERAL_PROPERTY_NOT_FOUND` | `[]`; no location |

## Example (application on a property in a county whose flood map is not loaded)

```json
{
  "applicationId": 15, "organizationId": 42, "propertyId": 7, "currency": "USD",
  "loanAmount": 300000.00, "loanAmountBasis": "REQUESTED",
  "collateral": { "valuationStatus": "supported", "value": 400000.00, "confidence": "high", "valuationDate": "2026-11-01", "...": "..." },
  "ltv": { "status": "AVAILABLE", "percent": 75.00, "reasons": [] },
  "geography": {
    "status": "PARTIAL", "reasons": [],
    "location": { "folio": "...", "address": "...", "city": "...", "zip": "...", "county": "...", "state": "FL" },
    "sources": [
      { "section": "FLOOD", "source": "FEMA National Flood Hazard Layer (NFHL), flood hazard zones", "status": "NO_COVERAGE", "dataAsOf": null, "note": "..." },
      { "section": "HAZARDS", "source": "FEMA National Risk Index (NRI), census tract", "status": "AVAILABLE", "dataAsOf": "2026-11-11", "note": null },
      { "section": "STORMS", "source": "NOAA NCEI Storm Events Database", "status": "AVAILABLE", "dataAsOf": "...", "note": "..." }
    ],
    "missingCoverage": ["FLOOD"],
    "riskContext": { "propertyId": 7, "status": "AVAILABLE", "...": "..." }
  },
  "disclaimer": "Informational only; not a credit decision or approval."
}
```

## Implementation

| Layer | File |
|---|---|
| Controller | `controller/LenderApplicationController.java` (`collateralAnalysis`) |
| Service | `service/LenderCollateralAnalysisService.java`, `service/impl/LenderCollateralAnalysisServiceImpl.java` (ownership, loan amount, valuation, LTV, then the geography) |
| Service | `service/LenderCollateralGeographyService.java`, `service/impl/LenderCollateralGeographyServiceImpl.java` (location, risk sources, missing coverage, status) |
| Repository | `repository/PropertyRepository.java`: `findCollateralLocation` (folio, address, county and state of one property) |
| Reused | `PropertyRiskContextService` (SCRUM-127), `ValuationService.getValuationWithoutComparables` (SCRUM-58) |
| DTO | `dto/LenderCollateralAnalysisDTO.java` with nested `Collateral`, `Ltv`, `Geography`, `Location`, `RiskSource` |

No migration. No new automated tests (ticket); the SCRUM-180 service test only receives the new dependency.

## Manual checks (Postman)

`docs/postman/SCRUM-181-collateral-geographic-risk.postman_collection.json`. Run the folders in order. Variables:
`baseUrl`, `keycloakUrl`, `clientIdKc`, users A and B with the lender role (A owns a new organization, B is not a
member), user C without the lender role, and the property ids of the SCRUM-127 checks: `idFull` (every source
loaded), `idPartial` (county flood map not loaded), `idNoParcel` (no parcel). Passwords stay empty in the file.

| # | Case | Expected |
|---|---|---|
| 1 | setup: new organization, one application per case | 201 each |
| 2a | every source covers the property | 200, `AVAILABLE`, three sources with `source` and `dataAsOf`, `missingCoverage` empty |
| 2b | flood map not loaded | 200, `PARTIAL`, `missingCoverage` contains `FLOOD`, `ltv` still returned |
| 3a | property without parcel | 200, `NO_COVERAGE`, `NO_PARCEL_POLYGON`, the three sections missing |
| 3b | application without property | 200, geography and LTV `UNAVAILABLE`, `NO_COLLATERAL_PROPERTY` |
| 3c | linked property not found | 200, geography `UNAVAILABLE`, `COLLATERAL_PROPERTY_NOT_FOUND` |
| 4 | `organizationId` missing or 0, `id` 0 or `abc` | 400 |
| 5a | no token | 401 |
| 5b | user without the lender role | 403 |
| 5c | user not a member | 404, no geography in the body |
| 5d | unknown organization, unknown application | 404 |
