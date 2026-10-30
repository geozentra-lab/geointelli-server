package com.geointelli.ai.property.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.PortfolioFinancialSummaryDTO;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PortfolioValueSummaryService;
import com.geointelli.ai.property.service.service.PortfolioFinancialSummaryService;
import com.geointelli.ai.property.service.service.PortfolioExposureSummaryService;
class PortfolioSummaryServiceImplTest {
    final PortfolioRepository portfolios=mock(PortfolioRepository.class);
    final CurrentUserService current=mock(CurrentUserService.class);
    final PortfolioValueSummaryService values=mock(PortfolioValueSummaryService.class);
    final PortfolioFinancialSummaryService financials=mock(PortfolioFinancialSummaryService.class);
    final PortfolioExposureSummaryService exposure=mock(PortfolioExposureSummaryService.class);
    final LocalDate today=LocalDate.of(2026,10,26);
    final PortfolioSummaryServiceImpl service=new PortfolioSummaryServiceImpl(portfolios,current,values,financials,exposure,
        Clock.fixed(Instant.parse("2026-10-26T23:30:00Z"),ZoneOffset.ofHours(9)));
    @BeforeEach void setup() {
        User user=new User(); user.setId(8L); when(current.getCurrentUserEntity()).thenReturn(user);
        Portfolio portfolio=new Portfolio(); portfolio.setId(1L); portfolio.setName("Rentals");
        when(portfolios.findByIdAndUser_Id(1L,8L)).thenReturn(Optional.of(portfolio));
    }
    @Test void defaultsUseUtcYearToDate() {
        var result=service.summarize(1L,null,null);
        assertEquals(today,result.asOfDate()); assertEquals(LocalDate.of(2026,1,1),result.fromDate());
        verify(values).summarize(1L,8L,today); verify(financials).summarize(1L,8L,LocalDate.of(2026,1,1),today);
    }
    @Test void missingOrForeignPortfolioDoesNotReadAnyComponents() {
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.summarize(2L,null,null)).getStatusCode().value());
        verifyNoInteractions(values,financials,exposure);
    }
    @Test void invalidAndFutureDatesAre400BeforeAggregateQueries() {
        assertThrows(IllegalArgumentException.class,()->service.summarize(1L,today,today.minusDays(1)));
        assertThrows(IllegalArgumentException.class,()->service.summarize(1L,null,today.plusDays(1)));
        verifyNoInteractions(values,financials,exposure);
    }
    @Test void invalidIdIsRejectedBeforeResolvingUser() {
        assertThrows(IllegalArgumentException.class,()->service.summarize(0L,null,null)); verifyNoInteractions(current);
    }
    @Test void historicalCashPeriodStillUsesCurrentHoldingsAsOfDate() {
        var to=LocalDate.of(2025,12,31); var result=service.summarize(1L,null,to);
        assertEquals(LocalDate.of(2025,1,1),result.fromDate()); assertEquals(today,result.asOfDate());
        verify(exposure).summarize(1L,8L,today);
    }
    @Test void sectionFailurePreservesOtherSectionsAndSanitizesErrors() {
        when(values.summarize(anyLong(),anyLong(),any())).thenThrow(new IllegalStateException("secret SQL"));
        PortfolioFinancialSummaryDTO financial=mock(PortfolioFinancialSummaryDTO.class);
        when(financials.summarize(anyLong(),anyLong(),any(),any())).thenReturn(financial);
        var result=service.summarize(1L,null,null); assertEquals("PARTIAL",result.status());
        assertSame(financial,result.financials()); assertNull(result.valuationAndDebt());
        assertEquals("SECTION_UNAVAILABLE",result.errors().get(0).code()); verify(exposure).summarize(1L,8L,today);
    }
    @Test void allFailedSectionsReturn503() {
        when(values.summarize(anyLong(),anyLong(),any())).thenThrow(new IllegalStateException());
        when(financials.summarize(anyLong(),anyLong(),any(),any())).thenThrow(new IllegalStateException());
        when(exposure.summarize(anyLong(),anyLong(),any())).thenThrow(new IllegalStateException());
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.summarize(1L,null,null)).getStatusCode().value());
    }
}
