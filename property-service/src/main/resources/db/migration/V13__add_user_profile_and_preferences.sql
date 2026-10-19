ALTER TABLE users ADD COLUMN persona VARCHAR(40) DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT chk_user_persona CHECK (
    persona IN ('USER', 'RESIDENTIAL_INVESTOR', 'REALTOR', 'BANK_LENDER')
);

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    min_budget NUMERIC(15, 2),
    max_budget NUMERIC(15, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_budget_nonnegative CHECK (
        (min_budget IS NULL OR min_budget >= 0) AND
        (max_budget IS NULL OR max_budget >= 0)
    ),
    CONSTRAINT chk_user_budget_range CHECK (
        min_budget IS NULL OR max_budget IS NULL OR min_budget <= max_budget
    )
);

CREATE TABLE user_target_counties (
    user_id BIGINT NOT NULL REFERENCES user_preferences(user_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    county_id BIGINT NOT NULL REFERENCES counties(id),
    PRIMARY KEY (user_id, position)
);

CREATE TABLE user_investment_goals (
    user_id BIGINT NOT NULL REFERENCES user_preferences(user_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    goal VARCHAR(200) NOT NULL,
    PRIMARY KEY (user_id, position)
);
