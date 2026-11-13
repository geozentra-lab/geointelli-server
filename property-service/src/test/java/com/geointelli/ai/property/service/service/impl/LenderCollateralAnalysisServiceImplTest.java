package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse.Estimate;
import com.geointelli.ai.property.service.dto.ValuationResponse.Method;
import com.geointelli.ai.property.service.dto.ValuationResponse.Range;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reason;
import com.geointelli.ai.property.service.entity.LenderApplication;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.LenderApplicationRepository;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import com.geointelli.ai.property.service.service.ValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** LTV rules of SCRUM-180 with the organization, the application and the stored valuation mocked. */
class LenderCollateralAnalysisServiceImplTest {

    private static final Long ACTOR = 7L;
    private static final Long ORG = 42L;
    private static final Long APP = 15L;
    private static final Long PROPERTY = 100L;

    private final LenderOrganizationService organizations = mock(LenderOrganizationService.class);
    private final LenderApplicationRepository applications = mock(LenderApplicationRepository.class);
    private final ValuationService valuations = mock(ValuationService.class);
    private final LenderCollateralAnalysisServiceImpl service =
            new LenderCollateralAnalysisServiceImpl(organizations, applications, valuations);

    private LenderApplication application;

    @BeforeEach
    void setup() {
        application = new LenderApplication();
        application.setId(APP);
        application.setOrganizationId(ORG);
        application.setPropertyId(PROPERTY);
        application.setRequestedAmount(new BigDecimal("320000.00"));
        application.setApprovedAmount(new BigDecimal("300000.00"));
        when(applications.findByIdAndOrganizationId(APP, ORG)).thenReturn(Optional.of(application));
    }

    private static ValuationResponse valuation(String status, String value, List<Reason> reasons) {
        Estimate estimate = value == null ? null : new Estimate(new BigDecimal(value),
                new Range(new BigDecimal("370000.00"), new BigDecimal("430000.00")),
                new Range(new BigDecimal("350000.00"), new BigDecimal("450000.00")), "high");
        Method method = new Method("avm", "v3", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 31), null);
        return new ValuationResponse(status, reasons, estimate, null, null, null, method, null, null);
    }

    @Test
    void supportedValuationGivesAvailableLtvWithValuationDateAndConfidence() {
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("supported", "400000.00", List.of()));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.applicationId()).isEqualTo(APP);
        assertThat(result.organizationId()).isEqualTo(ORG);
        assertThat(result.propertyId()).isEqualTo(PROPERTY);
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.loanAmount()).isEqualByComparingTo("300000.00");
        assertThat(result.loanAmountBasis()).isEqualTo(LenderCollateralAnalysisDTO.BASIS_APPROVED);
        assertThat(result.collateral().valuationStatus()).isEqualTo("supported");
        assertThat(result.collateral().value()).isEqualByComparingTo("400000.00");
        assertThat(result.collateral().confidence()).isEqualTo("high");
        assertThat(result.collateral().valuationDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.collateral().range80().low()).isEqualByComparingTo("370000.00");
        assertThat(result.ltv().status()).isEqualTo(LenderCollateralAnalysisDTO.LTV_AVAILABLE);
        assertThat(result.ltv().percent()).isEqualByComparingTo("75.00");
        assertThat(result.ltv().reasons()).isEmpty();
        assertThat(result.disclaimer()).isEqualTo(LenderCollateralAnalysisDTO.DISCLAIMER);
        verify(organizations).requireAccess(ACTOR, ORG, false);
    }

    @Test
    void fundedAmountWinsOverApprovedAndRequested() {
        application.setFundedAmount(new BigDecimal("280000.00"));
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("supported", "400000.00", List.of()));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.loanAmountBasis()).isEqualTo(LenderCollateralAnalysisDTO.BASIS_FUNDED);
        assertThat(result.ltv().percent()).isEqualByComparingTo("70.00");
    }

    @Test
    void requestedAmountIsUsedWhenNothingIsApproved() {
        application.setApprovedAmount(null);
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("supported", "400000.00", List.of()));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.loanAmountBasis()).isEqualTo(LenderCollateralAnalysisDTO.BASIS_REQUESTED);
        assertThat(result.loanAmount()).isEqualByComparingTo("320000.00");
        assertThat(result.ltv().percent()).isEqualByComparingTo("80.00");
    }

    @Test
    void percentIsRoundedToTwoDecimals() {
        application.setApprovedAmount(new BigDecimal("100000.00"));
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("supported", "300000.00", List.of()));

        assertThat(service.analyze(ACTOR, ORG, APP).ltv().percent()).isEqualByComparingTo("33.33");
    }

    @Test
    void limitedValuationGivesLimitedLtvWithModelReasons() {
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("limited", "400000.00",
                List.of(new Reason("few_comparables", "Few comparable sales"))));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.ltv().status()).isEqualTo(LenderCollateralAnalysisDTO.LTV_LIMITED);
        assertThat(result.ltv().percent()).isEqualByComparingTo("75.00");
        assertThat(result.ltv().reasons()).containsExactly("few_comparables");
        assertThat(result.collateral().reasons()).extracting(Reason::code).containsExactly("few_comparables");
    }

    @Test
    void unsupportedValuationGivesUnavailableLtvWithoutPercent() {
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("unsupported", null,
                List.of(new Reason("not_modelled", "Not modelled"))));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.collateral().valuationStatus()).isEqualTo("unsupported");
        assertThat(result.collateral().value()).isNull();
        assertThat(result.ltv().status()).isEqualTo(LenderCollateralAnalysisDTO.LTV_UNAVAILABLE);
        assertThat(result.ltv().percent()).isNull();
        assertThat(result.ltv().reasons()).containsExactly(LenderCollateralAnalysisDTO.VALUATION_UNAVAILABLE);
        assertThat(result.loanAmount()).isEqualByComparingTo("300000.00");
    }

    @Test
    void nonPositiveValueGivesInvalidValuation() {
        when(valuations.getValuation(PROPERTY)).thenReturn(valuation("supported", "0.00", List.of()));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.ltv().status()).isEqualTo(LenderCollateralAnalysisDTO.LTV_UNAVAILABLE);
        assertThat(result.ltv().percent()).isNull();
        assertThat(result.ltv().reasons()).containsExactly(LenderCollateralAnalysisDTO.INVALID_VALUATION);
    }

    @Test
    void applicationWithoutPropertyIsUnavailableAndSkipsValuation() {
        application.setPropertyId(null);

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.collateral()).isNull();
        assertThat(result.ltv().status()).isEqualTo(LenderCollateralAnalysisDTO.LTV_UNAVAILABLE);
        assertThat(result.ltv().reasons()).containsExactly(LenderCollateralAnalysisDTO.NO_COLLATERAL_PROPERTY);
        verifyNoInteractions(valuations);
    }

    @Test
    void deletedPropertyIsUnavailableInsteadOf404() {
        when(valuations.getValuation(PROPERTY)).thenThrow(new PropertyNotFoundException("Property not found: 100"));

        LenderCollateralAnalysisDTO result = service.analyze(ACTOR, ORG, APP);

        assertThat(result.collateral()).isNull();
        assertThat(result.ltv().percent()).isNull();
        assertThat(result.ltv().reasons()).containsExactly(LenderCollateralAnalysisDTO.COLLATERAL_PROPERTY_NOT_FOUND);
    }

    @Test
    void nonMemberGets404BeforeAnyRead() {
        when(organizations.requireAccess(ACTOR, ORG, false))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        assertThatThrownBy(() -> service.analyze(ACTOR, ORG, APP))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(applications, valuations);
    }

    @Test
    void applicationOfAnotherOrganizationGets404() {
        when(applications.findByIdAndOrganizationId(APP, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(ACTOR, 99L, APP))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(valuations);
    }

    @Test
    void nonPositiveApplicationIdIsRejected() {
        assertThatThrownBy(() -> service.analyze(ACTOR, ORG, 0L)).isInstanceOf(IllegalArgumentException.class);
        verify(applications, never()).findByIdAndOrganizationId(anyLong(), any());
        verifyNoInteractions(valuations);
    }
}
