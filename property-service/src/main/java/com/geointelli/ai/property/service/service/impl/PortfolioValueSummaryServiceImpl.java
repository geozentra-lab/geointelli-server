package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.PortfolioMetricDTO;
import com.geointelli.ai.property.service.dto.PortfolioValuationDTO;
import com.geointelli.ai.property.service.dto.PortfolioValueSummaryDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
import com.geointelli.ai.property.service.service.PortfolioValuationService;
import com.geointelli.ai.property.service.service.PortfolioValueSummaryService;

/** Adds only supported values; acquisition prices, original loans and projected amortization are not substitutes. */
@Service
public class PortfolioValueSummaryServiceImpl implements PortfolioValueSummaryService {
    private final PortfolioSummaryRepository repository;
    private final PortfolioValuationService valuations;
    public PortfolioValueSummaryServiceImpl(PortfolioSummaryRepository repository, PortfolioValuationService valuations) {
        this.repository=repository; this.valuations=valuations;
    }
    @Override
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW, isolation=Isolation.REPEATABLE_READ)
    public PortfolioValueSummaryDTO summarize(Long portfolioId, Long userId, LocalDate asOfDate) {
        List<PortfolioHoldingSnapshot> holdings=repository.holdings(portfolioId,userId);
        Set<Long> propertyIds=new HashSet<>();
        for (PortfolioHoldingSnapshot holding:holdings) propertyIds.add(holding.propertyId());
        Map<Long,PortfolioValuationDTO> values=propertyIds.isEmpty() ? Map.of() : valuations.latestValues(propertyIds,asOfDate);
        BigDecimal value=BigDecimal.ZERO, debt=BigDecimal.ZERO, equity=BigDecimal.ZERO;
        long valueCount=0, debtCount=0, equityCount=0;
        LocalDate oldest=null, newest=null, oldestValue=null, newestValue=null;
        Set<String> sources=new TreeSet<>();
        LocalDateTime updated=null;
        for (PortfolioHoldingSnapshot holding:holdings) {
            BigDecimal share=holding.share().movePointLeft(2);
            PortfolioValuationDTO quote=values.get(holding.propertyId());
            BigDecimal propertyValue=valid(quote,asOfDate) ? quote.amount() : null;
            if (propertyValue!=null) {
                sources.add(quote.source());
                if (oldestValue==null || quote.valueDate().isBefore(oldestValue)) oldestValue=quote.valueDate();
                if (newestValue==null || quote.valueDate().isAfter(newestValue)) newestValue=quote.valueDate();
            }
            BigDecimal balance=null;
            if (holding.financingType()==HoldingFinancingType.CASH) balance=BigDecimal.ZERO;
            else if (holding.outstandingBalance()!=null && holding.outstandingBalance().signum()>=0
                    && holding.balanceDate()!=null && !holding.balanceDate().isAfter(asOfDate)) {
                balance=holding.outstandingBalance();
                if (oldest==null || holding.balanceDate().isBefore(oldest)) oldest=holding.balanceDate();
                if (newest==null || holding.balanceDate().isAfter(newest)) newest=holding.balanceDate();
            }
            BigDecimal allocatedValue=propertyValue==null ? null : propertyValue.multiply(share).setScale(2,RoundingMode.HALF_UP);
            BigDecimal allocatedDebt=balance==null ? null : balance.multiply(share).setScale(2,RoundingMode.HALF_UP);
            if (allocatedValue!=null) { value=value.add(allocatedValue); valueCount++; }
            if (allocatedDebt!=null) { debt=debt.add(allocatedDebt); debtCount++; }
            if (allocatedValue!=null && allocatedDebt!=null) { equity=equity.add(allocatedValue.subtract(allocatedDebt)); equityCount++; }
            if (holding.updatedAt()!=null && (updated==null || holding.updatedAt().isAfter(updated))) updated=holding.updatedAt();
        }
        long total=holdings.size();
        return new PortfolioValueSummaryDTO("USD",asOfDate,metric(value,valueCount,total),metric(debt,debtCount,total),
            metric(equity,equityCount,total),oldest,newest,updated,oldestValue,newestValue,List.copyOf(sources),valueCount==0 ? "UNAVAILABLE_PENDING_SCRUM_58" : "SUPPORTED_VALUATION_SERVICE",
            "OWNER_REPORTED_FINANCING","Valuations require SCRUM-58; missing values remain unavailable. "
            + "Reported balances are used as recorded, not amortized to today. CASH financing contributes zero debt. "
            + "Owner amounts are rounded HALF_UP to cents per holding. All amounts apply current ownership share; equity requires value and debt for the same holding.");
    }
    private boolean valid(PortfolioValuationDTO quote, LocalDate date) {
        return quote!=null && quote.amount()!=null && quote.amount().signum()>=0 && "USD".equals(quote.currency())
            && quote.valueDate()!=null && !quote.valueDate().isAfter(date) && quote.source()!=null && !quote.source().isBlank();
    }
    private PortfolioMetricDTO metric(BigDecimal known, long covered, long total) {
        BigDecimal rounded=known.setScale(2,RoundingMode.HALF_UP);
        String status=total==0 ? "EMPTY" : covered==total ? "COMPLETE" : covered==0 ? "UNAVAILABLE" : "PARTIAL";
        return new PortfolioMetricDTO(status,covered==total ? rounded : null,rounded,covered,total);
    }
}
