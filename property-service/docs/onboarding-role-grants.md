# Onboarding and Keycloak role grants (SCRUM-204)

At first login the user answers one onboarding page: home buyer, investor, lender or realtor. **The account type is the
user's professional realm role in Keycloak** (`investor`, `realtor` or `lender`), **one per account**. The legacy
`users.persona` column is no longer read or written; a user without a professional role is a home buyer.

```
first login ──> GET /api/users/me/entitlements: onboardingCompleted=false ──> onboarding page
                                          │
  Home buyer: POST /api/users/me/onboarding/complete      (no role; done)
  Investor / Lender: POST /api/users/me/role-requests     ──> admin approves (/api/admin/role-requests)
  Realtor: POST /api/users/me/realtor-license             ──> admin approves (/api/admin/realtor-license-verifications)
                                          │
        each choice sets onboardingCompleted; on approval GeoZentra grants the realm role in Keycloak
                                          │
              frontend refreshes the access token, then GET /api/users/me/entitlements
```

- **Home buyer**: nothing to grant; every signed-in account has the baseline features. The user can still choose a
  professional role later.
- **Investor** and **lender**: a role request (product decision of 2026-11-25: a GeoZentra admin assigns every
  role). It is answered `201` with status `PENDING` and listed in `pendingRoles`; the admin approves it
  (`/api/admin/role-requests`), which grants the role in Keycloak. `organizationName` is optional; the lender then
  creates or joins the organization (`/api/lender/organizations`). Listing a role in `onboarding.self-service-roles`
  grants it at once instead (`201` `APPROVED`, no admin).
- **Realtor**: the license submission (SCRUM-199/200). The admin's approval grants the realtor role; until then
  `pendingRoles` contains `realtor`. Realtor is never self-service.
- **admin** is never granted or revoked by the application and is not an account type.

## One professional role per account

The user cannot change the account type: a request for another professional role is refused with `409` when the
account

- **holds** another one: in the token, unless GeoZentra revoked it after the token was issued; or granted by GeoZentra
  after the token was issued (so the rule holds before the token is refreshed);
- **waits** for another one: a pending investor or lender request (the user can cancel it), or a realtor license under
  review (for an investor or lender request).

This covers `POST /api/users/me/role-requests` and `POST /api/users/me/realtor-license`. To change an account type an
admin revokes the role (`POST /api/admin/users/{userId}/roles/{role}/revoke`); the user then chooses the other one.
Roles assigned by hand in the Keycloak console count as held; accounts that already had two roles keep them.

## Database (V50)

Apply by hand in one transaction: `psql -v ON_ERROR_STOP=1 -1 -f V50__add_onboarding_role_grants.sql`.

| Object | Purpose |
|---|---|
| `users.onboarding_completed_at` | when the onboarding page was answered; NULL until then. Existing users with a persona other than USER, or with a realtor license submission, get the migration time |
| `role_requests` | investor and lender requests: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `REVOKED`. At most one PENDING per user and role (partial unique index). `organization_name` is optional; a rejection needs `decision_reason`; every status except PENDING has `decided_at` |
| `role_grant_events` | append-only history of every grant and revocation done by GeoZentra: user, role, `GRANT`/`REVOKE`, source (`ROLE_REQUEST`, `SELF_SERVICE`, `REALTOR_LICENSE`, `ADMIN_REVOCATION`, `LICENSE_EXPIRY`), source record id, reason, deciding admin (null when automatic), time |

Roles assigned by hand in the Keycloak console have no event: the application never revokes them automatically.

## Keycloak setup

The application changes roles through the Keycloak Admin REST API with a **dedicated confidential client**, never a
person's login and never the Keycloak admin account:

1. Realm `geozentra` → Clients → Create: client id `geozentra-role-admin`, client authentication on, only "Service
   accounts roles" enabled (no standard or direct-access flow).
2. Service account roles → assign from client `realm-management`: `view-users`, `manage-users`, `view-realm`.
3. Credentials → copy the client secret into the environment (below). The realm roles `investor`, `realtor` and
   `lender` must exist (SCRUM-94).

| Setting | Environment variable | Default |
|---|---|---|
| `keycloak.role-admin.enabled` | `KEYCLOAK_ROLE_ADMIN_ENABLED` | `false`: every grant or revocation answers 503 and nothing changes |
| `keycloak.role-admin.client-id` / `client-secret` | `KEYCLOAK_ROLE_ADMIN_CLIENT_ID` / `KEYCLOAK_ROLE_ADMIN_CLIENT_SECRET` | none |
| `keycloak.role-admin.server-url` / `realm` | `KEYCLOAK_ROLE_ADMIN_SERVER_URL` / `KEYCLOAK_ROLE_ADMIN_REALM` | taken from `KEYCLOAK_ISSUER_URI` (`http://host:port/realms/geozentra`) |
| `keycloak.role-admin.timeout-seconds` | `KEYCLOAK_ROLE_ADMIN_TIMEOUT_SECONDS` | 5 |
| `onboarding.self-service-roles` | `ONBOARDING_SELF_SERVICE_ROLES` | empty: every investor and lender request waits for an admin. `investor`, `lender` or `investor,lender` grants those at once; startup fails on realtor, admin or an unknown role |
| `role-grants.realtor-license-expiry.enabled` / `cron` | `ROLE_GRANTS_REALTOR_EXPIRY_ENABLED` / `ROLE_GRANTS_REALTOR_EXPIRY_CRON` | `false` / `0 15 3 * * *` (UTC) |

The calls are the ones the official admin client wraps, made with Spring's RestClient (no Keycloak or RESTEasy
library): a client-credentials token (cached until 30 s before expiry, renewed once on 401),
`GET /admin/realms/{realm}/roles/{role}`, then `POST` or `DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm`.
Both mapping calls are idempotent. Secrets and tokens are never logged or returned.

## Consistency rules

- **Keycloak first, database second.** A decision calls Keycloak before writing anything. If Keycloak is off, refuses
  or cannot be reached, the answer is `503` and nothing changes: the request stays `PENDING`, the license stays
  `PENDING_REVIEW`. If the database write fails after Keycloak accepted, retrying the decision is safe (idempotent).
- **Self-service** (only for roles listed in `onboarding.self-service-roles`) **with Keycloak down** (or role
  management off): the request is kept `PENDING` (201) so an admin can approve it later.
- **Tokens**: a new or removed role reaches the user with their next access token (5 minutes at most). The frontend
  refreshes the token after a grant, then reads the entitlements again.
- **Realtor expiry**: the realtor role is held while the user has a VERIFIED license valid today (no expiration date,
  or expiring today or later, UTC). The daily run (or `POST /api/admin/role-grants/realtor-license-expiry`) revokes
  it otherwise; an expired verified license no longer blocks a new submission, so the renewed one can be sent.
  A rejected license never removes a role: a pending submission never granted one.

## Realtor directory

`GET /api/contact-requests/professionals` and contact requests (`POST /api/contact-requests`) now use the realtor
role, not the persona: a user is listed and contactable while they have a VERIFIED license valid today and
GeoZentra has not revoked their realtor role since. A pending, rejected or expired license, or an admin revocation,
removes them.

## User endpoints (any signed-in account; 401 without a token)

### `GET /api/users/me/entitlements` (SCRUM-94, changed)

| Field | Meaning |
|---|---|
| `onboardingCompleted` | `false` until the onboarding page was answered: show the page while false |
| `onboardingCompletedAt` | when it was answered (the first answer is kept) |
| `roles` | realm roles of the token; the professional one is the account type |
| `pendingRoles` | roles waiting for a decision: pending role requests, plus `realtor` while the latest license is PENDING_REVIEW. Show "pending approval"; the features stay off until the role is in the token |

`persona` was removed from this response and from `GET /api/users/me`. `PATCH /api/users/me` only changes the names;
a `persona` field in the body is ignored.

### `POST /api/users/me/onboarding/complete`

The home buyer choice: marks the page as answered without a role and returns the entitlements (200). Calling it again
keeps the first time.

### `POST /api/users/me/role-requests`

```json
{ "role": "lender", "organizationName": "Acme Mortgage", "note": "NMLS 123456" }
```

Only `role` is required. 201 with `Location`:

```json
{ "id": 12, "role": "lender", "status": "PENDING", "organizationName": "Acme Mortgage", "note": "NMLS 123456",
  "decisionReason": null, "createdAt": "2026-11-25T10:00:00Z", "decidedAt": null }
```

The request answers the onboarding page and waits for an admin: show "pending approval" while `pendingRoles` contains
the role. Once approved (`GET /api/users/me/role-requests/{id}` shows `APPROVED`), refresh the access token: the new
role is only in the next token.

| Response | When |
|---|---|
| 201 | `PENDING` (the default); `APPROVED` at once when the role is listed in `onboarding.self-service-roles` and Keycloak accepted |
| 400 | `role` missing or not investor/lender (`realtor` points to the license submission); field too long |
| 409 | the account already has the role (admin included), a request for that role is pending, or the account has or waits for another professional role |

### `POST /api/users/me/realtor-license` (SCRUM-199/200, changed)

Also answers the onboarding page, and is refused with `409` when the account has, or waits for, the investor or
lender role.

### `GET /api/users/me/role-requests`, `GET /api/users/me/role-requests/{id}`, `POST /api/users/me/role-requests/{id}/cancel`

The current user's requests, newest first; one of them (404 when missing or another user's); withdraw a pending one
(`CANCELLED`; 404, or 409 when already decided).

## Admin endpoints (realm role admin; 401 / 403)

| Endpoint | Effect | Errors |
|---|---|---|
| `GET /api/admin/role-requests?status=&role=&page=0&size=20` | review queue, newest first, with applicant id and e-mail | 400 bad page, size (1-100) or role |
| `GET /api/admin/role-requests/{id}` | one request | 404 |
| `POST /api/admin/role-requests/{id}/approve` body optional `{"reason": "..."}` | Keycloak grant, history row, request `APPROVED` with the admin | 404, 409 decided, 503 Keycloak (stays PENDING) |
| `POST /api/admin/role-requests/{id}/reject` `{"reason": "..."}` | request `REJECTED`; Keycloak not called | 400 no reason, 404, 409 decided |
| `POST /api/admin/realtor-license-verifications/{id}/approve` (existing) | now also grants `realtor` first | adds 503 Keycloak (stays PENDING_REVIEW) |
| `GET /api/admin/users?email=` | finds the account by exact email (case ignored): `id`, `keycloakUserId`, `username`, `email`, names, `realtorLicenseStatus`; the `id` goes into the two routes below. When old rows share the email (a Keycloak account deleted and signed up again), the newest one is returned | 400 missing or malformed email, 404 no account uses it |
| `GET /api/admin/users/search?q=&size=10` | suggestions while typing: accounts whose email, full name ("first last") or last name starts with `q`, case ignored, as `[{"id", "email", "firstName", "lastName"}]` by email (no email last), at most `size` (default 10, max 25), `[]` when none; newest row of each email only. See [Admin user search](#admin-user-search) | 400 `q` missing, under 2 or over 254 characters, `size` outside 1-25 |
| `GET /api/admin/users/{userId}/role-grants` | grant history of a user, newest first | 404 unknown user |
| `POST /api/admin/users/{userId}/roles/{role}/revoke` `{"reason": "..."}` | Keycloak revocation, history row, the user's approved request for that role `REVOKED`; the way to change an account type | 400 role not investor/realtor/lender or no reason, 404, 503 |
| `POST /api/admin/role-grants/realtor-license-expiry` | runs the expiry check now: `{"checkedOn", "candidates", "revoked", "failed"}` | failed revocations are retried by the next run |

All routes are under `/api/users/me/**` and `/api/admin/**`, so the SCRUM-94 route rules cover them unchanged.

## Adding a lender organization member by email

The owner of an organization finds the colleague first, then adds them by id:

1. `GET /api/lender/organizations/{organizationId}/members/lookup?email=` (owner only) answers
   `{"userId", "email", "firstName", "lastName", "member"}`; `member` is true when the account is already the owner or
   a member. 400 missing or malformed email, 403 a member who is not the owner, 404 an outsider or no account uses the
   email.
2. `POST /api/lender/organizations/{organizationId}/members` `{"userId": ...}` (unchanged, 204).

Both lookups match the exact email, case ignored, and find an account only once its owner has signed in to GeoZentra
at least once (the account row is created on the first call). The lender lookup is under `/api/lender/**` (lender or
admin role).

The lender lookup deliberately has no suggestions: with "starts with" matching, any organization owner could list
other users' names and emails a few letters at a time. Suggestions are for admins only.

## Admin user search

`GET /api/admin/users/search?q=dan&size=10` (admin) feeds a type-ahead on the admin screens:

- `q` is trimmed, runs of spaces count as one, and case is ignored. `_`, `%` and `\` match themselves.
- It matches the start of the email (`dan` finds `dana@bank.example`), of the full name "first last" (`jane do`
  finds Jane Doe) or of the last name (`doe` finds Jane Doe). It does not match the middle of a word (`ane`).
- At least 2 characters. The frontend should wait for 2 characters and about 250 ms without typing before calling,
  and ignore answers to older requests.
- When several rows share an email, only the newest is listed, as in the lookup by email; an old row whose name
  matches while the newest row's does not is not listed.
- Picking a suggestion gives the `id` for the role grant history and revoke routes;
  `GET /api/admin/users?email=` gives the full account with its realtor license status.

Migration `V53__add_user_search_columns.sql` adds three columns that Postgres computes on every insert and update,
`email_search` (`lower(email)`), `full_name_search` (lowercased "first last") and `last_name_search`, in collation
"C", each with an index. In code point order the values that start with a prefix are one range, so
`UserRepository.SEARCH` asks `prefix <= column < UserSearch.upperBound(prefix)` instead of `LIKE`. A range keeps using
the index when Postgres switches the prepared statement to a generic plan, which LIKE with a bound parameter does not.
The matches are sorted first, and the newest-row check runs only on the rows the limit takes.

Apply V53 by hand like the other migrations; it can run again safely. Adding stored columns rewrites `users` once
under a lock that also blocks reads (`users` is small). `UserSearchDatabaseTest` runs V53 and the query on a real
PostgreSQL when `ENVIRONMENTAL_TEST_DB_URL` points to a local `*_test` database, like the other database tests.

## Files

| Layer | Files |
|---|---|
| Migration | `db/migration/V50__add_onboarding_role_grants.sql` |
| Entities | `RoleRequest`, `RoleRequestStatus`, `RoleGrantEvent`, `RoleGrantAction`, `RoleGrantSource`; `User.onboardingCompletedAt` (`User.persona` kept, unused) |
| Repositories | `RoleRequestRepository`, `RoleGrantEventRepository`; `RealtorLicenseVerificationRepository.existsBlockingSubmission`; `UserRepository.findActiveRealtors` / `isActiveRealtor` (replace `findByPersona`) |
| Services | `KeycloakRoleService(Impl)`, `RoleGrantService(Impl)`, `RoleRequestService(Impl)`, `OnboardingService(Impl)` (one role per account, onboarding state); changes in `RealtorLicenseReviewServiceImpl`, `RealtorLicenseVerificationServiceImpl`, `ContactRequestServiceImpl`, `UserProfileServiceImpl`, `UserEntitlementServiceImpl` |
| Controllers | `UserOnboardingController`, `UserRoleRequestController`, `AdminRoleRequestController`, `AdminRoleGrantController` |
| DTOs | `UserEntitlementsResponse` (onboarding fields, no persona), `UserDTO` and `UserProfileUpdateRequest` (no persona) |
| Config | `KeycloakRoleAdminProperties`, `OnboardingProperties`, `RealtorLicenseExpiryScheduler`; `application.yml`, `.env.example` |
| Tests | `KeycloakRoleServiceImplTest` (mocked Keycloak), `OnboardingServiceImplTest`, `RoleRequestServiceImplTest`, `RealtorLicenseVerificationServiceImplTest`, `RoleGrantServiceImplTest`, `RealtorLicenseReviewServiceImplTest`, `UserEntitlementServiceImplTest`, `ContactRequestServiceImplTest` (PostgreSQL), `SecurityPolicyTest` |
| Postman | `docs/postman/SCRUM-204-onboarding-working-pipeline.postman_collection.json`: success path only, home buyer, investor, lender, realtor and an admin account change, with its own Keycloak logins (an admin and four fresh accounts per run); `docs/postman/SCRUM-204-onboarding-role-grants.postman_collection.json`: full checks with the error cases, one account |

## Verification log

### 2026-11-25

- **Automated:** full suite 1,746 tests, 0 failures. New: `KeycloakRoleServiceImplTest` (12, mocked Keycloak:
  token, role lookup, POST and DELETE mappings, token reuse and renewal on 401, disabled, missing secret, missing user
  409, missing role, 403, unreachable, wrong credentials), `RoleRequestServiceImplTest` (13),
  `RoleGrantServiceImplTest` (5), plus `RealtorLicenseReviewServiceImplTest` (grant before the decision, Keycloak
  failure keeps PENDING_REVIEW, rejection never calls Keycloak), `UserEntitlementServiceImplTest` and the new routes in
  `SecurityPolicyTest`.
- **Live, local scratch database with V50 and the real Keycloak token of an admin account, role management off:**
  38 of 38 checks. The application starts with `ddl-auto: validate`. `personaSelected` false for a new user, true
  with `personaSelectedAt` after `PATCH /api/users/me`. Role requests: admin 409 (already has the features), realtor
  400 pointing to the license, admin role 400, lender without organization 400, no token 401, another user's request
  404. Review queue with applicant e-mail and organization, filters and paging errors 400. Approval of a lender request
  and of a realtor license: 503 "not enabled ... nothing was changed", the request stays PENDING and the license
  PENDING_REVIEW, no history row. Rejection 200 with reason and admin, again 409. Revocation 503 / 400 / 404. Expiry
  run: the one user with a GeoZentra realtor grant and an expired license is the only candidate (failed while
  Keycloak is off). The test data and V50 were removed afterwards.
- **Live against the real Keycloak (`100.26.199.78:8090`, realm `geozentra`), role management on:** 35 of 35
  checks. Setup through the Keycloak admin API: the realm roles `investor`, `realtor` and `lender` (missing until
  then) and the client `geozentra-role-admin` (service account with view-users, manage-users, view-realm) were
  created and kept; a temporary test user was created and deleted at the end. The realm uses the e-mail as
  username. Results: persona alone grants nothing; lender request PENDING and listed in `pendingRoles`; admin
  approval maps `lender` in Keycloak, the old token keeps working without it, a new token has it, entitlements show
  `lender: true` and `/api/lender/organizations` answers 200; a second request and a second approval are 409.
  Admin revocation removes the mapping, the request becomes REVOKED and the lender route is 403 again. Realtor
  license approval maps `realtor` (CRM route 200) and blocks a new submission while valid; after setting the
  expiration to yesterday the expiry run revokes it (1 candidate, 1 revoked), the CRM route is 403, a second run
  finds nothing, and a new submission is accepted. History: lender GRANT/ROLE_REQUEST, lender
  REVOKE/ADMIN_REVOCATION, realtor GRANT/REALTOR_LICENSE, realtor REVOKE/LICENSE_EXPIRY. Investor request stays
  PENDING with self-service off and is cancelled; the tester's own roles are unchanged. The client secret used for
  the run existed only in memory: regenerate it in the Keycloak console (Credentials) before enabling the feature.

### 2026-11-25: self-service investor and lender, account type lock (superseded: roles only, below)

- **Automated:** full suite 1,760 tests, 0 failures. New `PersonaServiceImplTest` (7: persona to role, lender locked, grant newer than the token locks,
  pending realtor unlocked, USER never locked, revocation unlocks, admin change 400/404/200), `UserProfileServiceImplTest`
  (4: first selection, same persona keeps the time, a locked persona rejects the whole update, name-only update),
  `RoleRequestServiceImplTest` rewritten for self-service (lender without organization approved at once, investor,
  Keycloak down keeps PENDING, a role removed from the list waits, the list refuses realtor/admin/unknown),
  `UserEntitlementServiceImplTest` (`personaLocked`), and `PATCH /api/admin/users/{id}/persona` in `SecurityPolicyTest`.
- **Live against the real Keycloak, role management on, default `onboarding.self-service-roles`:** 37 of 37 checks.
  The existing `geozentra-role-admin` secret was read, not rotated. Lender request without organization: 201
  APPROVED with the self-service reason, `lender` mapped in Keycloak; with the old token entitlements already show
  `personaLocked: true` and nothing pending, and the new token gives `lender: true` and the lender route 200.
  Lock: BANK_LENDER → REALTOR 409 with the old token, → USER with a name 409 with the new token, name and persona
  unchanged, same persona 200 with the selection time kept. Investor request on the lender account: 201 APPROVED,
  persona stays BANK_LENDER. Admin persona change: ordinary account 403, no reason 400, unknown user 404, change 200
  with Keycloak roles unchanged, logged with the admin id and reason. Revoking investor and lender unlocks the
  persona. Realtor: the license waits (`pendingRoles: ["realtor"]`, not locked, CRM off), the choice can be corrected
  while pending, approval maps `realtor`, and the persona is locked at once (409). History: lender and investor
  GRANT/SELF_SERVICE, investor and lender REVOKE/ADMIN_REVOCATION, realtor GRANT/REALTOR_LICENSE. The tester's own
  roles are unchanged; the Keycloak test user, the test rows and V50 were removed from the scratch database.

### 2026-11-25: roles only (one professional realm role per account, persona removed; instant investor and lender superseded below)

- **Automated:** full suite 1,757 tests, 0 failures. `OnboardingServiceImplTest` (held roles: token, grant or revocation newer than the token, a token
  newer than a revocation; another held role, a pending request for another role and a pending license give 409; the
  first onboarding time is kept), `RoleRequestServiceImplTest` (one role per account through `create`, a revoked role
  frees the account, the request answers onboarding), `RealtorLicenseVerificationServiceImplTest` (a lender account
  gets 409 and nothing is saved; a submission answers onboarding), `UserProfileUpdateRequestTest` (`persona` from an
  older client ignored, other unknown fields still rejected), `UserEntitlementServiceImplTest`, `SecurityPolicyTest`
  (`/api/users/me/onboarding/complete`). `ContactRequestServiceImplTest` (15) run against a temporary PostgreSQL:
  only an active realtor is listed and contactable (revoked, expired or pending licenses and the legacy REALTOR
  persona are not), paging and counts of the native query.
- **Live against the real Keycloak, role management on:** 31 of 31 checks with two temporary test users (deleted
  afterwards); the existing client secret was read, not rotated. No persona fields in entitlements or the profile;
  `PATCH /api/users/me` with `persona` answers 200 and changes nothing. Lender request 201 APPROVED, `lender` mapped,
  onboarding answered. One role: investor request and realtor license 409 ("already a lender account") with the old
  and the new token; Keycloak keeps only `lender`. Admin revokes lender, the same stale token can then submit a
  realtor license (201); while it is pending an investor request is 409 ("realtor license is under review") and the
  user is not in the realtor directory. Approval maps only `realtor` and lists the user; a lender request is 409
  ("already a realtor account"); an admin revocation removes the user from the directory although the license is
  still valid. Home buyer: `POST /api/users/me/onboarding/complete` 200 with `roles: ["user"]`, the first time kept on
  a second call, and an investor request afterwards is 201 APPROVED. Anonymous onboarding 401. History: lender
  GRANT/SELF_SERVICE, lender REVOKE/ADMIN_REVOCATION, realtor GRANT/REALTOR_LICENSE, realtor REVOKE/ADMIN_REVOCATION.
  The tester's roles are unchanged; the test rows and V50 were removed from the scratch database.

### 2026-11-25: the admin approves every role (default)

- **Change:** `onboarding.self-service-roles` is empty by default (`application.yml`, `.env.example`,
  `OnboardingProperties`): investor and lender requests are `PENDING` until an admin approves them, like realtor
  licenses. Listing a role grants it at once, as before.
- **Unit:** `RoleRequestServiceImplTest` (18; new: by default every request is PENDING and Keycloak is not called; the
  self-service cases set the list), new `OnboardingPropertiesTest` (2: unset and empty bind to no self-service role, a
  list binds normalised). Full suite 1,760 tests, 0 failures.
- **Live against the real Keycloak, role management on, default settings:** five temporary accounts (deleted
  afterwards) and the tester as admin. Working-pipeline collection 46 requests, 107 checks; error-case collection 29
  requests, 50 checks; 0 failures. Investor and lender requests answer 201 `PENDING` and appear in `pendingRoles` and
  the admin queue; the admin's approval answers 200 `APPROVED` with the admin as decider and the next token carries
  the role; the history source is `ROLE_REQUEST`. Account change investor to lender: revoke, request, approval. The
  tester's roles are unchanged; the test rows and V50 were removed from the scratch database.
