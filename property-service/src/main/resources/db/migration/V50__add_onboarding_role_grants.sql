-- SCRUM-204: onboarding page at first login, and the account type kept as the user's Keycloak realm role.
--
-- * users.onboarding_completed_at: when the user answered the onboarding page (investor, lender, realtor or home
--   buyer); NULL until then. The account type is the professional realm role (investor, realtor or lender, one per
--   account), never the legacy users.persona column, which is no longer read. Users who already chose a professional
--   persona or submitted a realtor license count as onboarded (the time of this migration).
-- * role_requests: a user's request for the lender or investor realm role. It waits for a GeoZentra admin's approval,
--   unless the role is listed in onboarding.self-service-roles (empty by default): then it is granted at once. Realtors use the license submission of SCRUM-199/200 instead, so 'realtor' is not requestable.
-- * role_grant_events: append-only history of every realm role GeoZentra granted or revoked in Keycloak (who, which
--   role, why, who decided, when). The latest event of a user and role tells whether GeoZentra granted it; roles
--   assigned by hand in the Keycloak console have no event and are never revoked automatically.
--
-- Apply by hand in ONE transaction: psql -v ON_ERROR_STOP=1 -1 -f V50__add_onboarding_role_grants.sql

ALTER TABLE users ADD COLUMN onboarding_completed_at TIMESTAMP WITH TIME ZONE;

UPDATE users u
SET onboarding_completed_at = CURRENT_TIMESTAMP
WHERE (u.persona IS NOT NULL AND u.persona <> 'USER')
   OR EXISTS (SELECT 1 FROM realtor_license_verifications v WHERE v.user_id = u.id);

CREATE TABLE role_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    requested_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    organization_name VARCHAR(160),
    note VARCHAR(1000),
    decision_reason VARCHAR(500),
    decided_by_user_id BIGINT REFERENCES users(id),
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_requests_role CHECK (requested_role IN ('investor', 'lender')),
    CONSTRAINT ck_role_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REVOKED')),
    -- a rejection always tells the applicant why
    CONSTRAINT ck_role_requests_reject_reason CHECK (status <> 'REJECTED'
        OR (decision_reason IS NOT NULL AND btrim(decision_reason) <> '')),
    -- pending requests are undecided; every other status has a decision time
    CONSTRAINT ck_role_requests_decided CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

-- at most one pending request per user and role
CREATE UNIQUE INDEX ux_role_requests_one_pending ON role_requests (user_id, requested_role) WHERE status = 'PENDING';
CREATE INDEX idx_role_requests_status ON role_requests (status, id DESC);
CREATE INDEX idx_role_requests_user ON role_requests (user_id, id DESC);

CREATE TABLE role_grant_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL,
    action VARCHAR(10) NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_id BIGINT,
    reason VARCHAR(500),
    decided_by_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_role_grant_events_role CHECK (role IN ('investor', 'realtor', 'lender')),
    CONSTRAINT ck_role_grant_events_action CHECK (action IN ('GRANT', 'REVOKE')),
    CONSTRAINT ck_role_grant_events_source CHECK (source IN
        ('ROLE_REQUEST', 'SELF_SERVICE', 'REALTOR_LICENSE', 'ADMIN_REVOCATION', 'LICENSE_EXPIRY'))
);

CREATE INDEX idx_role_grant_events_user_role ON role_grant_events (user_id, role, id DESC);
