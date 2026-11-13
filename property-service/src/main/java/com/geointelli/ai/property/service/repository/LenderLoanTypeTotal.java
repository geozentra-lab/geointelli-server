package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.LenderLoanType;

import java.math.BigDecimal;

/** Number of applications and summed amount for one loan type, computed by the database. */
public interface LenderLoanTypeTotal {
    LenderLoanType getLoanType();
    long getCount();
    BigDecimal getTotal();
}
