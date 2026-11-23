# Listing source and field mapping (SCRUM-118)

A **listing** is what a seller publishes about a home that is on the market: asking price, status
(for sale, pending, sold), dates and the MLS number. It is different from the county appraiser records we
already hold in `properties` and `sales`, which describe ownership, size and **completed** sales. This
document fixes, before any import exists, where listings come from, which markets are covered, which
provider field maps to which column of ours, and how the data is refreshed.

Subtask of SCRUM-50 "Acquire and normalize supported-market listing data". The import itself is SCRUM-119
and the matching to our properties is SCRUM-120; both build on this mapping.

The same information is stored in the tables `listing_sources` and `listing_source_fields` (migration V45)
and served by `GET /api/admin/listing-sources` so it can be checked with Postman. The tables are the
machine-readable copy of this page; when one changes, change the other.

## Source

| Item | Value |
|---|---|
| Code | `REALTOR_COM` |
| Name | Realtor.com listing search |
| Provider | Move, Inc. (Realtor.com), listings syndicated from the local MLS boards |
| Access method | Realtor.com's internal GraphQL search, read through the open-source HomeHarvest library (`scrape_property`, `return_type="raw"`), per ZIP code and listing type. No API key, no contract. A US IP is required; other countries get HTTP 403. |
| Authorization status | **PENDING**. Realtor.com has no public listing API; the team already uses this feed for the Florida photo backfill. The product owner confirms the source before SCRUM-119 loads listings into a shared environment. Record the decision date in `listing_sources.authorized_at`. |
| Licence note | Realtor.com Terms of Use; MLS data carries the listing broker's copyright. Store listing facts, not the description text or photos, unless the authorization covers them. |
| Markets covered | Florida: the 925 ZIP codes with homes in our database, all 67 counties (ZIP runs of 2026-11-18 to 2026-11-20). Arizona: Maricopa County ZIP codes (earlier run). Any ZIP in the US can be added by running the same collection. |
| Listing types collected | `sold`, `for_sale`, `for_rent`, `pending`, `off_market`. Rentals are collected but out of scope for sale listings. |
| Refresh method | Batch, pull. Re-run the ZIP collection and re-import; a listing is recognised by its source identifiers, so a re-import updates rather than duplicates. There is no push feed and no change log; the provider's `last_update_date` says when the record last changed. Proposed cadence: weekly per supported county. |
| Known limits | 10,000 results per query (the collector splits by year, then by month); search history starts around 2016; a record is a **snapshot** of the current status, not a status history; description text and photos are the broker's; rate limited, about one ZIP per minute. |
| Reference files | Collector `scrape_realtor_zips.py`, address lookup `address_search.py`, matcher `match_florida.py` (team data folder `counties/florida_realtor/`). |

The authorized-source rule of the parent story: **only this source may feed the listings table.** A second
provider gets its own row in `listing_sources` and its own mapping before any import.

## Identity and idempotency

The parent story forbids using a CSV row number as identity. The provider gives three identifiers:

| Provider field | Meaning | Use |
|---|---|---|
| `property_id` | Realtor's stable id of the **home** (the same across its listings over the years) | Our `source_property_id`. Key for matching to our properties in SCRUM-120 together with the address. |
| `listing_id` | Id of **one listing event** (one time the home was put on the market). Changes when the home is relisted. Null on records that were never listed on Realtor (`off_market` tax-record-only homes). | Our `source_listing_id`. |
| `source.listing_id` + `source.id` | The **MLS number** and the MLS board code | Our `mls_number`, `mls_set`. Realtor returns them, but the team's collector currently drops the `source` block (`DROP` list in `scrape_realtor_zips.py`). SCRUM-119 must keep it. |

Import identity (unique key proposed for the `listings` table): `(source_code, source_property_id,
source_listing_id)`, with `source_listing_id` replaced by the empty string when null. A re-import of the same
listing updates the row; a relisting of the same home creates a new row with the same `source_property_id`.

## Field mapping

Field names are the raw GraphQL names as saved by the collector (one JSON object per home). "Filled" is the
share of non-empty values in a sample of 2,041 Florida records returned by the address lookup
(mostly `off_market` and `sold` homes; for-sale fields are filled on for-sale records only). Target
columns are the proposed columns of the `listings` table that SCRUM-119 creates.

Availability: **AVAILABLE** always or nearly always filled; **PARTIAL** filled only on some records;
**UNAVAILABLE** the provider does not give it; **EXCLUDED** given by the provider but not imported, by
decision.

### Identifiers

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `property_id` | `source_property_id` | text | AVAILABLE | Realtor home id, stable, 100% filled. |
| `listing_id` | `source_listing_id` | text | PARTIAL | One per listing event; null when the home was never listed (tax-record-only `off_market` homes). |
| `source.listing_id` | `mls_number` | text | PARTIAL | MLS number. Dropped by the current collector; keep it in SCRUM-119. |
| `source.id` | `mls_set` | text | PARTIAL | MLS board code. Same remark. |
| `permalink` | `source_permalink` | text | AVAILABLE | Path of the Realtor page, used for provenance; `href` is the full URL and is derived. |
| `tax_record.public_record_id`, `tax_record.cl_id` | `source_public_record_id` | text | AVAILABLE | Provider's public-record key; `cl_id` is the same value. |
| `tax_record.tax_parcel_id` | `source_tax_parcel_id` | text | AVAILABLE | Provider's parcel key (UUID-like, not the county folio). |
| `tax_record.apn` | (none) | text | UNAVAILABLE | Assessor parcel number. Empty in every Florida record seen, so not mapped (would be `source_apn`); when present it is folio-like, but a folio alone is not unique across counties (parent story), so it is never used to attach a listing on its own. |

### Timestamps

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `list_date` | `listed_at` | timestamp (UTC) | PARTIAL | When the listing entered the provider's system. Filled on listed records only. |
| `pending_date` | `pending_at` | timestamp (UTC) | PARTIAL | When the listing went pending. |
| `last_status_change_date` | `status_changed_at` | timestamp (UTC) | PARTIAL | Last status change. |
| `last_update_date` | `source_updated_at` | timestamp (UTC) | AVAILABLE | Last change of the record at the provider; this is the freshness field. |
| `last_sold_date` | `last_sold_at` | date | PARTIAL | Completed-sale side, see "Sale price". |
| `tax_record.last_update_date` | `tax_record_updated_at` | date | AVAILABLE | When the provider refreshed the public record. |
| collection time | `collected_at` | timestamp (UTC) | AVAILABLE | Not a provider field: the time the collector fetched the record. Written by the import from the file date. |

### Status

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `status` | `listing_status` | enum | AVAILABLE | Provider values `for_sale`, `pending`, `sold`, `off_market`, `for_rent`, `other`. Stored as our `FOR_SALE`, `PENDING`, `SOLD`, `OFF_MARKET`, `FOR_RENT`, `OTHER`. `off_market` also covers homes that were never listed. |
| `mls_status` | `mls_status` | text | PARTIAL | MLS wording (`Active`, `Sold`, `Expired`, `Pending`, ...). Kept as given; not normalised. |
| `flags.is_pending` | `flag_pending` | boolean | PARTIAL | Null means unknown, not false. |
| `flags.is_contingent` | `flag_contingent` | boolean | PARTIAL | Same. |
| `flags.is_new_construction` | `flag_new_construction` | boolean | PARTIAL | Same. |
| `tags` | `tags` | text array | PARTIAL | Provider feature tags (`garage_2_or_more`, `swimming_pool`, ...). Informational. Any financing-related tag is dropped at import. |

### Asking price (never mixed with sale price)

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `list_price` | `asking_price` | numeric(14,2) | PARTIAL | Current asking price in USD. Filled on listed records only. |
| `list_price_min`, `list_price_max` | `asking_price_min`, `asking_price_max` | numeric(14,2) | PARTIAL | Range, used for new-construction communities and rentals. Empty in every Florida sale record seen. |
| `price_per_sqft` | `asking_price_per_sqft` | numeric(10,2) | PARTIAL | Provider's price per square foot. On `sold` records it is derived from the sold price; the import stores it only when `status` is `for_sale` or `pending`. |
| `hoa.fee` | `hoa_fee_monthly` | numeric(10,2) | PARTIAL | Association fee per month; `0` means none. A fee, not a financing figure. |

### Sale price (completed sales, kept apart)

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `last_sold_price` | `last_sold_price` | numeric(14,2) | PARTIAL | Price of the last completed sale as the provider knows it. Goes to its own column, never to `asking_price`. Our authoritative sale history stays the county `sales` table; this value is a cross-check only. |
| `last_sold_date` | `last_sold_at` | date | PARTIAL | Date of that sale. |

### Address and location (for matching in SCRUM-120)

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `location.address.line` | `address_line` | text | AVAILABLE | Street line as the provider formats it. |
| `location.address.street_number`, `street_direction`, `street_name`, `street_suffix` | `street_number`, `street_direction`, `street_name`, `street_suffix` | text | AVAILABLE | Parsed parts; direction and suffix can be null. |
| `location.address.unit` | `unit` | text | PARTIAL | Condo or apartment unit. Null on houses. |
| `location.address.city` | `city` | text | AVAILABLE | |
| `location.address.state_code` | `state_code` | text | AVAILABLE | Two letters. |
| `location.address.postal_code` | `zip_code` | text | AVAILABLE | Five digits. |
| `location.address.coordinate.lat`, `lon` | `latitude`, `longitude` | numeric | AVAILABLE | WGS84. |
| `location.county.name`, `location.county.fips_code` | `county_name`, `county_fips` | text | AVAILABLE | Five-digit FIPS, matches the SCRUM-131 `COUNTY` geography code. |
| `location.neighborhoods[].name` | `neighborhood_name` | text | PARTIAL | Rare. |

### Home description (secondary; the appraiser record stays authoritative)

| Provider field | Target column | Type | Availability | Note |
|---|---|---|---|---|
| `description.type` | `property_type` | text | AVAILABLE | `single_family`, `condos`, `townhomes`, `mobile`, `land`, ... |
| `description.beds` | `bedrooms` | integer | PARTIAL | Filled when the listing says so; this is the field the photo backfill uses for bedrooms. |
| `description.baths_full`, `baths_half` | `bathrooms_full`, `bathrooms_half` | integer | PARTIAL | |
| `description.sqft`, `lot_sqft` | `living_sqft`, `lot_sqft` | integer | PARTIAL | |
| `description.year_built`, `stories`, `garage` | `year_built`, `stories`, `garage_spaces` | integer | PARTIAL | |
| `description.text` | (none) | text | EXCLUDED | Broker's marketing text, copyright; not stored. |
| `primary_photo`, `photos[]` | (none) | url | EXCLUDED | Photos are handled by the image pipeline (`property_images`), not by the listings table. |

### Not mapped

| Provider field | Availability | Reason |
|---|---|---|
| `estimates.currentValues[]`, `current_estimates[]` | EXCLUDED | Third-party automated valuations (Quantarium, CoreLogic, Collateral). Estimates, not observations; our valuation module has its own sources. |
| `taxHistory[]`, `nearbySchools` | EXCLUDED | We hold assessments and taxes from the county, and schools from SCRUM-125. |
| `advertisers` (agent, broker, office, phones, emails) | EXCLUDED | Personal contact data; the collector already drops it. |
| `open_houses`, `pet_policy`, `units`, `monthly_fees`, `one_time_fees`, `parking`, `popularity`, `terms` | EXCLUDED | Rental or marketing details; the collector drops most of them. |
| Any mortgage, financing, monthly-payment or rate field, and the `is_foreclosure` flag | EXCLUDED | Not requested by the collector's query and not imported by policy: the platform stores no financing data. |
| Owner names, deed book and page, instrument numbers | UNAVAILABLE | The provider does not publish them; they stay with the county `sales` and `owners` tables. |
| Listing history (previous prices, previous statuses) | UNAVAILABLE | Each record is the current snapshot; history accumulates only through repeated imports. |

## Coverage summary

| Market | Geography unit | Collected | Status |
|---|---|---|---|
| Florida, 67 counties | ZIP code (925 ZIPs) | 2026-11-18 to 2026-11-20 | collected to files, not imported; authorization pending |
| Arizona, Maricopa County | ZIP code | 2026-11 | collected to files, not imported |

Collection counts and per-county coverage of the photo backfill are in
`counties/florida_realtor/florida_coverage_by_county.csv`.

## Endpoints

All under `/api/admin/**`, so the Keycloak realm role `admin` is required. That is the ownership rule of
this ticket: there is no per-user data here.

| Endpoint | Returns |
|---|---|
| `GET /api/admin/listing-sources` | All sources, newest first. |
| `GET /api/admin/listing-sources/{code}` | One source with its field mapping. `404` for an unknown code. |
| `GET /api/admin/listing-sources/{code}/fields?group=&availability=` | Only the field rows, optionally filtered by `group` (`IDENTIFIER`, `TIMESTAMP`, `STATUS`, `ASKING_PRICE`, `SALE_PRICE`, `LOCATION`, `DESCRIPTION`, `NOT_MAPPED`) and `availability` (`AVAILABLE`, `PARTIAL`, `UNAVAILABLE`, `EXCLUDED`). `400` for an unknown value. |

Source item:

```json
{
  "code": "REALTOR_COM", "name": "Realtor.com listing search", "provider": "Move, Inc. (Realtor.com)",
  "accessMethod": "HomeHarvest scrape_property(raw) per ZIP and listing type; US IP required",
  "authorizationStatus": "PENDING", "authorizedAt": null,
  "licenceNote": "Realtor.com Terms of Use; MLS data is the listing broker's. Facts only, no text or photos.",
  "coverage": "FL all 67 counties by ZIP (925 ZIPs, 2026-11-18..20); AZ Maricopa by ZIP",
  "refreshMethod": "BATCH_PULL", "refreshCadence": "weekly per supported county",
  "identityFields": "property_id + listing_id (MLS number from source.listing_id)",
  "knownLimits": "10,000 results per query; history from ~2016; snapshot, no status history",
  "fieldCount": 59,
  "createdAt": "2026-11-23T10:00:00Z", "updatedAt": "2026-11-23T10:00:00Z"
}
```

Field item:

```json
{
  "id": 1, "sourceCode": "REALTOR_COM", "fieldGroup": "ASKING_PRICE",
  "sourceField": "list_price", "targetColumn": "asking_price", "dataType": "numeric(14,2)",
  "availability": "PARTIAL", "note": "Current asking price in USD. Filled on listed records only."
}
```

Errors: `401` without a token, `403` without the `admin` role, `404` unknown source code, `400` unknown
`group` or `availability`. A source with no field rows returns `200` with an empty `fields` list.

## Manual verification

Collection `docs/postman/SCRUM-118-listing-source.postman_collection.json`.

| # | Case | Expected |
|---|---|---|
| 03 | List sources as admin | `200`, one item `REALTOR_COM`, `authorizationStatus = PENDING` |
| 04 | Get `REALTOR_COM` | `200`, `fields` non-empty, every `ASKING_PRICE` row targets an `asking_*` or `hoa_*` column, every `SALE_PRICE` row targets `last_sold_*` |
| 05 | Fields filtered `group=IDENTIFIER` | `200`, contains `property_id` and `listing_id` |
| 06 | Fields filtered `availability=EXCLUDED` | `200`, contains the financing row and `description.text` |
| 07 | Fields filtered `group=TIMESTAMP&availability=AVAILABLE` | `200`, contains `last_update_date` |
| 08 | Lower-case code `realtor_com` | `200` (code is case-insensitive) |
| 09 | Unknown code | `404` |
| 10 | `group=PRICE` | `400` naming the allowed values |
| 11 | `availability=MAYBE` | `400` |
| 12 | Non-admin user | `403` |
| 13 | No token | `401` |
