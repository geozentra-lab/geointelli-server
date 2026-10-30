package com.geointelli.ai.property.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
class PortfolioExposureSummaryServiceImplTest {
    final PortfolioSummaryRepository repository=mock(PortfolioSummaryRepository.class);
    final PortfolioExposureSummaryServiceImpl service=new PortfolioExposureSummaryServiceImpl(repository);
    final LocalDate today=LocalDate.of(2026,10,26);
    PortfolioHoldingSnapshot holding(long id,HoldingOccupancyStatus status,Long county,Long state,String share) {
        return new PortfolioHoldingSnapshot(id,id,new BigDecimal(share),HoldingFinancingType.UNKNOWN,null,null,status,
            county,county==null ? null : "County",state,state==null ? null : "FL",state==null ? null : "Florida",null);
    }
    @Test void rentalRateExcludesOwnerOccupiedAndUnknownAndShowsUnknownGeography() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(1,HoldingOccupancyStatus.RENTED,31L,1L,"50"),
            holding(2,HoldingOccupancyStatus.VACANT,31L,1L,"100"),holding(3,HoldingOccupancyStatus.OWNER_OCCUPIED,31L,1L,"100"),
            holding(4,HoldingOccupancyStatus.UNKNOWN,null,null,"100")));
        var result=service.summarize(1L,8L,today);
        assertEquals(new BigDecimal("50.00"),result.rentalOccupancyPercent()); assertEquals(3,result.knownOccupancyHoldings());
        assertEquals(2,result.rentalHoldings()); assertEquals(2,result.states().size());
        var unknown=result.counties().stream().filter(g->g.unknown()).findFirst().orElseThrow();
        assertEquals(new BigDecimal("25.00"),unknown.holdingPercent());
        var known=result.counties().stream().filter(g->!g.unknown()).findFirst().orElseThrow();
        assertEquals(3,known.holdingCount()); assertEquals(new BigDecimal("2.5000"),known.ownershipEquivalentHoldings());
    }
    @Test void noRentalHoldingsHasNullRateRatherThanZeroOccupancy() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(1,HoldingOccupancyStatus.UNKNOWN,31L,1L,"100")));
        assertNull(service.summarize(1L,8L,today).rentalOccupancyPercent());
    }
    @Test void emptyPortfolioHasEmptyBreakdowns() {
        when(repository.holdings(1L,8L)).thenReturn(List.of());
        var result=service.summarize(1L,8L,today); assertEquals("EMPTY",result.status());
        assertNull(result.rentalOccupancyPercent()); assertTrue(result.states().isEmpty()); assertTrue(result.counties().isEmpty());
        assertEquals(0,result.occupancyCounts().get(HoldingOccupancyStatus.RENTED));
    }
    @Test void sameCountyNamesInDifferentStatesRemainSeparate() {
        when(repository.holdings(1L,8L)).thenReturn(List.of(holding(1,HoldingOccupancyStatus.RENTED,1L,1L,"100"),
            holding(2,HoldingOccupancyStatus.RENTED,2L,2L,"100")));
        assertEquals(2,service.summarize(1L,8L,today).counties().size());
    }
}
