-- Loan type of a lender application (SCRUM-114), used by the lender dashboard loan mix.
-- Existing applications have no recorded type, so they become UNKNOWN.
-- Written without PostgreSQL-only syntax so the same script also runs on the H2 test database.
ALTER TABLE lender_applications ADD COLUMN loan_type VARCHAR(20) DEFAULT 'UNKNOWN' NOT NULL;
ALTER TABLE lender_applications ADD CONSTRAINT ck_lender_loan_type
    CHECK (loan_type IN ('CONVENTIONAL', 'FHA', 'VA', 'USDA', 'UNKNOWN'));
