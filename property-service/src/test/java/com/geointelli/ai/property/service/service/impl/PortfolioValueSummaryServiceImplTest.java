package com.geointelli.ai.property.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.dto.PortfolioValuationDTO;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
import com.geointelli.ai.property.service.service.PortfolioValuationService;

class PortfolioValueSummaryServiceImplTest {
    final PortfolioSummaryRepository repository=mock(PortfolioSummaryRepository.class);
    final PortfolioValuationService valuations=mock(PortfolioValuationService.class);
    final PortfolioValueSummaryServiceImpl service=new PortfolioValueSummaryServiceImpl(repository,valuations);
    final LocalDate today=LocalDate.of(2026,10,26);
    PortfolioHoldingSnapshot holding(long id,String share,HoldingFinancingType type,String balance,LocalDate date) {
        return new PortfolioHoldingSnapshot(id,id,new BigDecimal(share),type,balance==null ? null : new BigDecimal(balance),date,
            HoldingOccupancyStatus.UNKNOWN,null,null,null,null,null,LocalDateTime.of(2026,10,1,0,0));
    }
    PortfolioValuationDTO quote(String amount) { return new PortfolioValuationDTO(new BigDecimal(amount),"USD",today,"test-provider"); }
    @Test void allocatesValueAndDebtOnceAndComputesSameHoldingEquity() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"50",HoldingFinancingType.FINANCED,"200000",today),
            holding(20,"100",HoldingFinancingType.CASH,null,null)));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of(10L,quote("400000"),20L,quote("300000")));
        var result=service.summarize(1L,8L,today);
        assertEquals(new BigDecimal("500000.00"),result.value().total());
        assertEquals(new BigDecimal("100000.00"),result.debt().total());
        assertEquals(new BigDecimal("400000.00"),result.equity().total());
        assertEquals(List.of("test-provider"),result.valuationSources()); assertEquals(today,result.oldestBalanceDate());
    }
    @Test void missingValuationsRemainNullWhileReportedDebtIsAvailable() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"50",HoldingFinancingType.FINANCED,"200000",today)));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of());
        var result=service.summarize(1L,8L,today);
        assertNull(result.value().total()); assertEquals("UNAVAILABLE",result.value().status());
        assertNull(result.equity().total()); assertEquals(new BigDecimal("100000.00"),result.debt().total());
    }
    @Test void originalLoanOrMissingBalanceNeverMeansZeroDebt() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"100",HoldingFinancingType.FINANCED,null,null)));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of(10L,quote("400000")));
        var result=service.summarize(1L,8L,today);
        assertNull(result.debt().total()); assertNull(result.equity().total()); assertEquals(1,result.value().coveredHoldings());
    }
    @Test void equityCoverageRequiresValueAndDebtOnTheSameHolding() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"100",HoldingFinancingType.UNKNOWN,null,null),
            holding(20,"100",HoldingFinancingType.CASH,null,null)));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of(10L,quote("400000")));
        var result=service.summarize(1L,8L,today);
        assertEquals("PARTIAL",result.value().status()); assertEquals("PARTIAL",result.debt().status());
        assertEquals(0,result.equity().coveredHoldings()); assertNull(result.equity().total());
    }
    @Test void futureBalancesAndForeignCurrencyValuationsAreUncovered() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"100",HoldingFinancingType.FINANCED,"100",today.plusDays(1))));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of(10L,new PortfolioValuationDTO(BigDecimal.TEN,"EUR",today,"provider")));
        var result=service.summarize(1L,8L,today); assertNull(result.value().total()); assertNull(result.debt().total());
    }
    @Test void emptyPortfolioReturnsZeroTotalsAndNoProviderCall() {
        when(repository.holdings(1L,8L)).thenReturn(List.of());
        var result=service.summarize(1L,8L,today); assertEquals("EMPTY",result.value().status());
        assertEquals(new BigDecimal("0.00"),result.debt().total()); verifyNoInteractions(valuations);
    }
    @Test void futureValuationIsNotCurrentEvidence() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"100",HoldingFinancingType.CASH,null,null)));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of(10L,new PortfolioValuationDTO(BigDecimal.TEN,"USD",today.plusDays(1),"provider")));
        assertNull(service.summarize(1L,8L,today).value().total());
    }
    @Test void negativeEquityIsPreserved() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(10,"100",HoldingFinancingType.FINANCED,"20",today)));
        when(valuations.latestValues(anySet(),eq(today))).thenReturn(Map.of(10L,quote("10")));
        assertEquals(new BigDecimal("-10.00"),service.summarize(1L,8L,today).equity().total());
    }
}
