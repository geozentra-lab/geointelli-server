package com.geointelli.ai.property.service.mapper;

import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.dto.HoldingFinancingDTO;
import com.geointelli.ai.property.service.dto.HoldingOccupancyDTO;
import com.geointelli.ai.property.service.dto.PortfolioDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingDTO;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.entity.Property;

/** Copies stored facts without calculating valuation, returns, loan amortization or income. */
@Component
public class PortfolioMapper {
    public PortfolioDTO toDTO(Portfolio portfolio, long holdingCount) {
        return new PortfolioDTO(portfolio.getId(), portfolio.getName(), portfolio.getDescription(), "USD",
            holdingCount, portfolio.getCreatedAt(), portfolio.getUpdatedAt());
    }

    public PortfolioHoldingDTO toDTO(PortfolioHolding holding) {
        Property property = holding.getProperty();
        HoldingFinancingDTO financing = new HoldingFinancingDTO();
        financing.setType(holding.getFinancingType());
        financing.setLoanAmount(holding.getLoanAmount());
        financing.setOutstandingBalance(holding.getOutstandingBalance());
        financing.setBalanceAsOfDate(holding.getBalanceAsOfDate());
        financing.setInterestRatePercent(holding.getInterestRatePercent());
        financing.setLoanTermMonths(holding.getLoanTermMonths());
        financing.setMonthlyPayment(holding.getMonthlyPayment());
        HoldingOccupancyDTO occupancy = new HoldingOccupancyDTO();
        occupancy.setStatus(holding.getOccupancyStatus());
        occupancy.setMonthlyRent(holding.getMonthlyRent());
        occupancy.setLeaseStartDate(holding.getLeaseStartDate());
        occupancy.setLeaseEndDate(holding.getLeaseEndDate());
        return new PortfolioHoldingDTO(holding.getId(), holding.getPortfolio().getId(), property.getId(),
            property.getFolio(), property.getCounty() == null ? null : property.getCounty().getId(), "USD",
            holding.getAcquisitionDate(), holding.getPurchasePrice(), holding.getAcquisitionCosts(),
            holding.getOwnershipSharePercent(), financing, occupancy, holding.getCreatedAt(), holding.getUpdatedAt());
    }
}
