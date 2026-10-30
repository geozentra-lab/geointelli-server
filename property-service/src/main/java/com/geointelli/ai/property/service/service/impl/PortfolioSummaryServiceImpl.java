package com.geointelli.ai.property.service.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.PortfolioExposureSummaryDTO;
import com.geointelli.ai.property.service.dto.PortfolioFinancialSummaryDTO;
import com.geointelli.ai.property.service.dto.PortfolioSummaryDTO;
import com.geointelli.ai.property.service.dto.PortfolioSummaryErrorDTO;
import com.geointelli.ai.property.service.dto.PortfolioValueSummaryDTO;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioExposureSummaryService;
import com.geointelli.ai.property.service.service.PortfolioFinancialSummaryService;
import com.geointelli.ai.property.service.service.PortfolioSummaryService;
import com.geointelli.ai.property.service.service.PortfolioValueSummaryService;

/** Authorizes first, then isolates component transactions so a failed query cannot poison successful sections. */
@Service
public class PortfolioSummaryServiceImpl implements PortfolioSummaryService {
    private static final Logger log=LoggerFactory.getLogger(PortfolioSummaryServiceImpl.class);
    private final PortfolioRepository portfolios;
    private final CurrentUserService currentUser;
    private final PortfolioValueSummaryService values;
    private final PortfolioFinancialSummaryService financials;
    private final PortfolioExposureSummaryService exposure;
    private final Clock clock;
    public PortfolioSummaryServiceImpl(PortfolioRepository portfolios, CurrentUserService currentUser,
            PortfolioValueSummaryService values, PortfolioFinancialSummaryService financials,
            PortfolioExposureSummaryService exposure, @Qualifier("portfolioSummaryClock") Clock clock) {
        this.portfolios=portfolios; this.currentUser=currentUser; this.values=values;
        this.financials=financials; this.exposure=exposure; this.clock=clock;
    }
    @Override
    public PortfolioSummaryDTO summarize(Long portfolioId, LocalDate fromDate, LocalDate toDate) {
        if (portfolioId==null || portfolioId<=0) throw new IllegalArgumentException("portfolioId must be positive");
        Long userId=currentUser.getCurrentUserEntity().getId();
        Instant generatedAt=clock.instant();
        LocalDate today=generatedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate to=toDate==null ? today : toDate;
        LocalDate from=fromDate==null ? to.withDayOfYear(1) : fromDate;
        if (from.isAfter(to) || to.isAfter(today)) throw new IllegalArgumentException("fromDate must not follow toDate; toDate must not be in the future");
        Portfolio portfolio=portfolios.findByIdAndUser_Id(portfolioId,userId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Portfolio not found"));
        List<PortfolioSummaryErrorDTO> errors=new ArrayList<>();
        PortfolioValueSummaryDTO value=null;
        PortfolioFinancialSummaryDTO financial=null;
        PortfolioExposureSummaryDTO occupancy=null;
        try { value=values.summarize(portfolioId,userId,today); }
        catch (RuntimeException failure) { failed(errors,"valuationAndDebt",failure); }
        try { financial=financials.summarize(portfolioId,userId,from,to); }
        catch (RuntimeException failure) { failed(errors,"financials",failure); }
        try { occupancy=exposure.summarize(portfolioId,userId,today); }
        catch (RuntimeException failure) { failed(errors,"occupancyAndExposure",failure); }
        if (errors.size()==3) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Portfolio summary is temporarily unavailable");
        return new PortfolioSummaryDTO(portfolioId,portfolio.getName(),errors.isEmpty() ? "AVAILABLE" : "PARTIAL",
            generatedAt,today,from,to,value,financial,occupancy,List.copyOf(errors));
    }
    private void failed(List<PortfolioSummaryErrorDTO> errors, String section, RuntimeException failure) {
        errors.add(new PortfolioSummaryErrorDTO(section,"SECTION_UNAVAILABLE"));
        log.warn("Portfolio summary section {} failed: {}",section,failure.getClass().getSimpleName());
    }
}
