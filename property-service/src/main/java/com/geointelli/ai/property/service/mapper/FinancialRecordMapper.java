package com.geointelli.ai.property.service.mapper;

import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.dto.FinancialRecordDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancialRecord;

/** Copies actual records without changing holdings, official property facts or scenarios. */
@Component
public class FinancialRecordMapper {
    public FinancialRecordDTO toDTO(HoldingFinancialRecord record) {
        return new FinancialRecordDTO(record.getId(), record.getHolding().getPortfolio().getId(),
            record.getHolding().getId(), record.getCategory(), record.getCategory().getType(),
            record.getAmount(), record.getCurrency(), record.getTransactionDate(), record.getPeriodStart(),
            record.getPeriodEnd(), record.getDescription(), "USER_REPORTED_ACTUAL",
            record.getCreatedAt(), record.getUpdatedAt());
    }
}
