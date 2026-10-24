package com.geointelli.ai.property.service.entity;

/** Categories determine cash direction, so an expense cannot accidentally be counted as income. */
public enum FinancialRecordCategory {
    RENT(FinancialRecordType.INCOME),
    OTHER_INCOME(FinancialRecordType.INCOME),
    REPAIRS(FinancialRecordType.EXPENSE),
    MAINTENANCE(FinancialRecordType.EXPENSE),
    INSURANCE(FinancialRecordType.EXPENSE),
    PROPERTY_TAX(FinancialRecordType.EXPENSE),
    HOA(FinancialRecordType.EXPENSE),
    UTILITIES(FinancialRecordType.EXPENSE),
    MANAGEMENT_FEE(FinancialRecordType.EXPENSE),
    MORTGAGE_PAYMENT(FinancialRecordType.EXPENSE),
    OTHER_EXPENSE(FinancialRecordType.EXPENSE);

    private final FinancialRecordType type;

    FinancialRecordCategory(FinancialRecordType type) {
        this.type = type;
    }

    public FinancialRecordType getType() {
        return type;
    }
}
