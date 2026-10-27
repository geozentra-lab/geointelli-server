package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.PortfolioFinancialCategoryDTO;
import com.geointelli.ai.property.service.dto.PortfolioFinancialSummaryDTO;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.FinancialRecordType;
import com.geointelli.ai.property.service.repository.PortfolioFinancialAggregate;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
import com.geointelli.ai.property.service.service.PortfolioFinancialSummaryService;

/** Actual cash activity uses category direction and ownership share exactly once, with no projected monthly amounts. */
@Service
public class PortfolioFinancialSummaryServiceImpl implements PortfolioFinancialSummaryService {
    private final PortfolioSummaryRepository repository;
    public PortfolioFinancialSummaryServiceImpl(PortfolioSummaryRepository repository) { this.repository=repository; }
    @Override
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW, isolation=Isolation.REPEATABLE_READ)
    public PortfolioFinancialSummaryDTO summarize(Long portfolioId, Long userId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate==null || toDate==null || fromDate.isAfter(toDate)) throw new IllegalArgumentException("Invalid reporting period");
        long total=repository.holdingCount(portfolioId,userId);
        List<PortfolioFinancialAggregate> groups=repository.financials(portfolioId,userId,fromDate,toDate);
        Map<FinancialRecordCategory,BigDecimal> amounts=new EnumMap<>(FinancialRecordCategory.class);
        Map<FinancialRecordCategory,Long> counts=new EnumMap<>(FinancialRecordCategory.class);
        Set<Long> covered=new HashSet<>();
        BigDecimal income=BigDecimal.ZERO, expenses=BigDecimal.ZERO;
        long recordCount=0;
        LocalDateTime updated=null;
        for (PortfolioFinancialAggregate group:groups) {
            if (!"USD".equals(group.currency())) throw new IllegalStateException("Unsupported financial-record currency");
            BigDecimal amount=money(group.amount().multiply(group.share().movePointLeft(2)));
            amounts.merge(group.category(),amount,BigDecimal::add);
            counts.merge(group.category(),group.recordCount(),Long::sum);
            if (group.category().getType()==FinancialRecordType.INCOME) income=income.add(amount);
            else expenses=expenses.add(amount);
            covered.add(group.holdingId()); recordCount+=group.recordCount();
            if (group.updatedAt()!=null && (updated==null || group.updatedAt().isAfter(updated))) updated=group.updatedAt();
        }
        List<PortfolioFinancialCategoryDTO> categories=new ArrayList<>();
        for (FinancialRecordCategory category:amounts.keySet()) categories.add(new PortfolioFinancialCategoryDTO(
            category,category.getType(),money(amounts.get(category)),counts.get(category)));
        return new PortfolioFinancialSummaryDTO(recordCount==0 ? "EMPTY" : "RECORDED","ACTUAL_RECORDED_CASH","USD",fromDate,toDate,
            money(income),money(expenses),money(income.subtract(expenses)),recordCount,covered.size(),total,updated,
            List.copyOf(categories),"Inclusive transactionDate range; coverage periods do not prorate payments. "
            + "Amounts use current ownership share and round HALF_UP to cents per holding/category aggregate. Zero means no recorded cash, not proof of no costs. "
            + "Mortgage payments include recorded principal/interest together; no profit, ROI or projected rent is inferred.");
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
}
