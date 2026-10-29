package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.PortfolioExposureSummaryDTO;
import com.geointelli.ai.property.service.dto.PortfolioGeographyDTO;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.repository.PortfolioHoldingSnapshot;
import com.geointelli.ai.property.service.repository.PortfolioSummaryRepository;
import com.geointelli.ai.property.service.service.PortfolioExposureSummaryService;

/** Counts holdings rather than parcels, units or tenants; UNKNOWN remains an explicit category. */
@Service
public class PortfolioExposureSummaryServiceImpl implements PortfolioExposureSummaryService {
    private final PortfolioSummaryRepository repository;
    public PortfolioExposureSummaryServiceImpl(PortfolioSummaryRepository repository) { this.repository=repository; }
    @Override
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW, isolation=Isolation.REPEATABLE_READ)
    public PortfolioExposureSummaryDTO summarize(Long portfolioId, Long userId, LocalDate asOfDate) {
        List<PortfolioHoldingSnapshot> holdings=repository.holdings(portfolioId,userId);
        Map<HoldingOccupancyStatus,Long> counts=new EnumMap<>(HoldingOccupancyStatus.class);
        for (HoldingOccupancyStatus status:HoldingOccupancyStatus.values()) counts.put(status,0L);
        Map<String,GeographyGroup> states=new TreeMap<>(), counties=new TreeMap<>();
        LocalDateTime updated=null;
        for (PortfolioHoldingSnapshot holding:holdings) {
            HoldingOccupancyStatus occupancy=holding.occupancy()==null ? HoldingOccupancyStatus.UNKNOWN : holding.occupancy();
            counts.merge(occupancy,1L,Long::sum);
            String stateKey=holding.stateId()==null ? "UNKNOWN" : "STATE:"+holding.stateId();
            String countyKey=holding.countyId()==null ? "UNKNOWN" : "COUNTY:"+holding.countyId();
            states.computeIfAbsent(stateKey,key->new GeographyGroup(holding,false)).add(holding.share());
            counties.computeIfAbsent(countyKey,key->new GeographyGroup(holding,true)).add(holding.share());
            if (holding.updatedAt()!=null && (updated==null || holding.updatedAt().isAfter(updated))) updated=holding.updatedAt();
        }
        long total=holdings.size(), known=total-counts.get(HoldingOccupancyStatus.UNKNOWN);
        long rented=counts.get(HoldingOccupancyStatus.RENTED);
        long rental=rented+counts.get(HoldingOccupancyStatus.VACANT);
        return new PortfolioExposureSummaryDTO(total==0 ? "EMPTY" : "RECORDED",asOfDate,total,counts,known,rental,
            rental==0 ? null : percent(rented,rental),"HOLDING_COUNT",geography(states,total),geography(counties,total),updated,
            "Current owner-reported occupancy, not historical occupancy or a lease-date inference. "
            + "Rental occupancy = RENTED / (RENTED + VACANT); excludes OWNER_OCCUPIED and UNKNOWN. "
            + "Geographic percentages use all holdings, including unknown geography; they are not value-weighted. "
            + "ownershipEquivalentHoldings is the sum of ownership fractions.");
    }
    private List<PortfolioGeographyDTO> geography(Map<String,GeographyGroup> groups, long total) {
        List<PortfolioGeographyDTO> result=new ArrayList<>();
        for (GeographyGroup group:groups.values()) {
            PortfolioHoldingSnapshot h=group.holding;
            result.add(new PortfolioGeographyDTO(h.stateId(),h.stateCode(),h.stateName(),
                group.county ? h.countyId() : null,group.county ? h.countyName() : null,
                group.county ? h.countyId()==null : h.stateId()==null,
                group.count,percent(group.count,total),group.equivalent.setScale(4,RoundingMode.HALF_UP)));
        }
        return List.copyOf(result);
    }
    private BigDecimal percent(long count, long total) {
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    }
    private static class GeographyGroup {
        private final PortfolioHoldingSnapshot holding;
        private final boolean county;
        private long count;
        private BigDecimal equivalent=BigDecimal.ZERO;
        GeographyGroup(PortfolioHoldingSnapshot holding, boolean county) { this.holding=holding; this.county=county; }
        void add(BigDecimal share) { count++; equivalent=equivalent.add(share.movePointLeft(2)); }
    }
}
