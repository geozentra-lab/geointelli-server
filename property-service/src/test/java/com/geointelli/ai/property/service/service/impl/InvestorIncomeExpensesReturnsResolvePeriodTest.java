package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.InvestorIncomeExpensesReturnsCardDTO.Period;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.InvestmentAnalysisService;
import com.geointelli.ai.property.service.service.PortfolioFinancialSummaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The period check used by the composed investor dashboard (SCRUM-107), on 2026-11-23 (current month 2026-11). */
public class InvestorIncomeExpensesReturnsResolvePeriodTest {

    CurrentUserService currentUserService = mock(CurrentUserService.class);
    PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
    PortfolioHoldingRepository portfolioHoldingRepository = mock(PortfolioHoldingRepository.class);
    PortfolioFinancialSummaryService portfolioFinancialSummaryService = mock(PortfolioFinancialSummaryService.class);
    InvestmentAnalysisService investmentAnalysisService = mock(InvestmentAnalysisService.class);

    InvestorIncomeExpensesReturnsServiceImpl service = new InvestorIncomeExpensesReturnsServiceImpl(
            currentUserService, portfolioRepository, portfolioHoldingRepository, portfolioFinancialSummaryService,
            investmentAnalysisService, Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));

    private void assertRejected(YearMonth fromMonth, YearMonth toMonth, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.resolvePeriod(fromMonth, toMonth));
        assertEquals(message, e.getMessage());
    }

    @Nested
    @DisplayName("Test Resolve Period")
    class ResolvePeriod {

        @Test
        @DisplayName("When no month is given, then return the last 12 complete months without reading data")
        void whenNoMonth_thenLastTwelveCompleteMonths() {
            // Act
            Period period = service.resolvePeriod(null, null);

            // Assert
            assertEquals(new Period(YearMonth.of(2025, 11), YearMonth.of(2026, 10), LocalDate.of(2025, 11, 1),
                    LocalDate.of(2026, 10, 31), 12, "UTC"), period);
            verifyNoInteractions(currentUserService, portfolioRepository, portfolioHoldingRepository,
                    portfolioFinancialSummaryService, investmentAnalysisService);
        }

        @Test
        @DisplayName("When both months are given, then return those whole months")
        void whenBothMonths_thenThoseMonths() {
            // Act
            Period period = service.resolvePeriod(YearMonth.of(2026, 1), YearMonth.of(2026, 3));

            // Assert
            assertEquals(new Period(YearMonth.of(2026, 1), YearMonth.of(2026, 3), LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 31), 3, "UTC"), period);
        }

        @Test
        @DisplayName("When the months break a rule, then throw with the SCRUM-104 message")
        void whenInvalidMonths_thenThrow() {
            assertRejected(YearMonth.of(2026, 1), null, "fromMonth and toMonth must be given together");
            assertRejected(YearMonth.of(2026, 3), YearMonth.of(2026, 1), "fromMonth must not be after toMonth");
            assertRejected(YearMonth.of(2026, 1), YearMonth.of(2026, 11),
                    "toMonth must be a complete month before 2026-11");
            assertRejected(YearMonth.of(1899, 12), YearMonth.of(1900, 1), "fromMonth must not be before 1900-01");
            assertRejected(YearMonth.of(2000, 1), YearMonth.of(2026, 1),
                    "The period must not be longer than 120 months");
        }
    }
}
