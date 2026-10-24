package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.FinancialRecordDTO;
import com.geointelli.ai.property.service.dto.FinancialRecordQuery;
import com.geointelli.ai.property.service.dto.FinancialRecordRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;

/** Manages actual cash transactions through an owned portfolio and its holding. */
public interface HoldingFinancialRecordService {
    /** Adds a positive actual amount; the category determines income or expense. */
    FinancialRecordDTO create(Long portfolioId, Long holdingId, FinancialRecordRequest request);
    /** Lists records by transaction date descending, then ID descending; filters are inclusive. */
    PortfolioPageDTO<FinancialRecordDTO> list(Long portfolioId, Long holdingId, FinancialRecordQuery query);
    /** Returns one record after checking portfolio ownership and holding membership; otherwise 404. */
    FinancialRecordDTO get(Long portfolioId, Long holdingId, Long recordId);
    /** Corrects supplied facts in place, preserving omitted fields and original creation time. */
    FinancialRecordDTO update(Long portfolioId, Long holdingId, Long recordId, FinancialRecordRequest request);
    /** Permanently deletes only the actual record; portfolio, holding and property are preserved. */
    void delete(Long portfolioId, Long holdingId, Long recordId);
}
