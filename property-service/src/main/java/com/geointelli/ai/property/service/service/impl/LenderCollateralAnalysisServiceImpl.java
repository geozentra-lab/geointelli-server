package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO;
import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO.Collateral;
import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO.Ltv;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.entity.LenderApplication;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.LenderApplicationRepository;
import com.geointelli.ai.property.service.service.LenderCollateralAnalysisService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.service.ValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Divides the loan amount by the stored model value of the linked property. Not transactional on purpose: the
 * valuation reads supporting comparables in their own transaction and handles their failures, which must not mark
 * a shared transaction rollback-only.
 */
@Service
@RequiredArgsConstructor
public class LenderCollateralAnalysisServiceImpl implements LenderCollateralAnalysisService {

    private final LenderOrganizationService organizations;
    private final LenderApplicationRepository applications;
    private final ValuationService valuations;

    @Override
    public LenderCollateralAnalysisDTO analyze(Long actor, Long organizationId, Long applicationId) {
        // Membership first, then the application must belong to that organization: otherwise the same 404
        organizations.requireAccess(actor, organizationId, false);
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        LenderApplication application = applications.findByIdAndOrganizationId(applicationId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        // Loan amount: funded, else approved, else requested
        BigDecimal loanAmount;
        String basis;
        if (application.getFundedAmount() != null) {
            loanAmount = application.getFundedAmount();
            basis = LenderCollateralAnalysisDTO.BASIS_FUNDED;
        } else if (application.getApprovedAmount() != null) {
            loanAmount = application.getApprovedAmount();
            basis = LenderCollateralAnalysisDTO.BASIS_APPROVED;
        } else {
            loanAmount = application.getRequestedAmount();
            basis = LenderCollateralAnalysisDTO.BASIS_REQUESTED;
        }

        Long propertyId = application.getPropertyId();
        if (propertyId == null) {
            return result(application, loanAmount, basis, null,
                    unavailable(LenderCollateralAnalysisDTO.NO_COLLATERAL_PROPERTY));
        }

        // Collateral: the stored valuation, unchanged
        ValuationResponse valuation;
        try {
            valuation = valuations.getValuation(propertyId);
        } catch (PropertyNotFoundException e) {
            return result(application, loanAmount, basis, null,
                    unavailable(LenderCollateralAnalysisDTO.COLLATERAL_PROPERTY_NOT_FOUND));
        }
        Collateral collateral = collateral(valuation);

        // LTV: only from a supported or limited positive value
        BigDecimal value = collateral.value();
        Ltv ltv;
        if ("unsupported".equals(collateral.valuationStatus()) || value == null) {
            ltv = unavailable(LenderCollateralAnalysisDTO.VALUATION_UNAVAILABLE);
        } else if (value.signum() <= 0) {
            ltv = unavailable(LenderCollateralAnalysisDTO.INVALID_VALUATION);
        } else {
            BigDecimal percent = loanAmount.multiply(BigDecimal.valueOf(100)).divide(value, 2, RoundingMode.HALF_UP);
            if ("limited".equals(collateral.valuationStatus())) {
                ltv = new Ltv(LenderCollateralAnalysisDTO.LTV_LIMITED, percent, codes(collateral.reasons()));
            } else {
                ltv = new Ltv(LenderCollateralAnalysisDTO.LTV_AVAILABLE, percent, List.of());
            }
        }
        return result(application, loanAmount, basis, collateral, ltv);
    }

    /** Copies the stored valuation without changing it; value fields stay null when there is no estimate. */
    private static Collateral collateral(ValuationResponse valuation) {
        ValuationResponse.Estimate estimate = valuation.estimate();
        ValuationResponse.Method method = valuation.method();
        List<ValuationResponse.Reason> reasons = valuation.reasons() == null ? List.of() : valuation.reasons();
        return new Collateral(valuation.status(),
                estimate == null ? null : estimate.value(),
                estimate == null ? null : estimate.range80(),
                estimate == null ? null : estimate.range90(),
                estimate == null ? null : estimate.confidence(),
                method == null ? null : method.predictionDate(),
                method == null ? null : method.modelVersion(),
                reasons);
    }

    private static List<String> codes(List<ValuationResponse.Reason> reasons) {
        return reasons.stream().map(ValuationResponse.Reason::code).toList();
    }

    private static Ltv unavailable(String reason) {
        return new Ltv(LenderCollateralAnalysisDTO.LTV_UNAVAILABLE, null, List.of(reason));
    }

    private static LenderCollateralAnalysisDTO result(LenderApplication application, BigDecimal loanAmount,
                                                      String basis, Collateral collateral, Ltv ltv) {
        return new LenderCollateralAnalysisDTO(application.getId(), application.getOrganizationId(),
                application.getPropertyId(), application.getCurrency(), loanAmount, basis, collateral, ltv,
                LenderCollateralAnalysisDTO.DISCLAIMER);
    }
}
