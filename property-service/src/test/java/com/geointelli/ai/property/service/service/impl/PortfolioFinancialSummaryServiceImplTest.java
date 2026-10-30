package com.geointelli.ai.property.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.repository.PortfolioFinancialAggregate;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
class PortfolioFinancialSummaryServiceImplTest {
    final PortfolioSummaryRepository repository=mock(PortfolioSummaryRepository.class);
    final PortfolioFinancialSummaryServiceImpl service=new PortfolioFinancialSummaryServiceImpl(repository);
    final LocalDate from=LocalDate.of(2026,10,1), to=LocalDate.of(2026,10,26);
    PortfolioFinancialAggregate group(long id,String share,FinancialRecordCategory category,String amount,long count) {
        return new PortfolioFinancialAggregate(id,new BigDecimal(share),category,"USD",new BigDecimal(amount),count,
            LocalDateTime.of(2026,10,26,0,0));
    }
    @Test void sumsActualsByCategoryAndOwnershipWithoutDoublingShares() {
        when(repository.holdingCount(1L,8L)).thenReturn(3L);
        when(repository.financials(1L,8L,from,to)).thenReturn(List.of(
            group(10,"50",FinancialRecordCategory.RENT,"3000",2),
            group(10,"50",FinancialRecordCategory.MORTGAGE_PAYMENT,"1000",1),
            group(20,"100",FinancialRecordCategory.REPAIRS,"2000",1)));
        var result=service.summarize(1L,8L,from,to);
        assertEquals(new BigDecimal("1500.00"),result.income()); assertEquals(new BigDecimal("2500.00"),result.expenses());
        assertEquals(new BigDecimal("-1000.00"),result.cashFlow()); assertEquals(4,result.recordCount());
        assertEquals(2,result.holdingsWithRecords()); assertEquals(3,result.totalHoldings());
        assertEquals(3,result.categories().size());
    }
    @Test void missingRecordsAreEmptyNotProjectedRent() {
        when(repository.holdingCount(1L,8L)).thenReturn(2L); when(repository.financials(1L,8L,from,to)).thenReturn(List.of());
        var result=service.summarize(1L,8L,from,to); assertEquals("EMPTY",result.status());
        assertEquals(new BigDecimal("0.00"),result.cashFlow()); assertEquals(0,result.holdingsWithRecords());
        assertTrue(result.categories().isEmpty());
    }
    @Test void rejectsMixedCurrenciesInsteadOfSilentlyCombiningThem() {
        when(repository.financials(1L,8L,from,to)).thenReturn(List.of(new PortfolioFinancialAggregate(10L,BigDecimal.TEN,
            FinancialRecordCategory.RENT,"EUR",BigDecimal.TEN,1L,null)));
        assertThrows(IllegalStateException.class,()->service.summarize(1L,8L,from,to));
    }
    @Test void rejectsReversedDatesBeforeQuerying() {
        assertThrows(IllegalArgumentException.class,()->service.summarize(1L,8L,to,from)); verifyNoInteractions(repository);
    }
    @Test void roundedDisplayedTotalsStillSatisfyCashFlowEquation() {
        when(repository.financials(1L,8L,from,to)).thenReturn(List.of(group(10,"50",FinancialRecordCategory.RENT,"0.01",1),
            group(10,"10",FinancialRecordCategory.REPAIRS,"0.01",1)));
        var result=service.summarize(1L,8L,from,to);
        assertEquals(result.income().subtract(result.expenses()),result.cashFlow());
        assertEquals(new BigDecimal("0.01"),result.income());
    }
}
