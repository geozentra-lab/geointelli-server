package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.dto.HoldingFinancingDTO;
import com.geointelli.ai.property.service.dto.HoldingOccupancyDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingDTO;
import com.geointelli.ai.property.service.dto.PortfolioHoldingRequest;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.PortfolioMapper;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Checks holding identity, ownership and reported financing/occupancy semantics. */
class PortfolioHoldingServiceImplTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PortfolioHoldingServiceImpl service = new PortfolioHoldingServiceImpl(
        portfolios, holdings, properties, currentUser, new PortfolioMapper(), VALIDATION.getValidator());
    private PortfolioHolding holding;

    @AfterAll
    static void closeValidation() { VALIDATION.close(); }

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        Portfolio portfolio = new Portfolio();
        portfolio.setId(10L);
        portfolio.setUser(user);
        when(portfolios.findByIdAndUser_Id(10L, 7L)).thenReturn(Optional.of(portfolio));
        when(portfolios.findOwnedForUpdate(10L, 7L)).thenReturn(Optional.of(portfolio));
        Property property = new Property();
        property.setId(100L);
        property.setFolio("shared-folio");
        when(properties.findById(100L)).thenReturn(Optional.of(property));
        holding = new PortfolioHolding();
        holding.setId(20L);
        holding.setPortfolio(portfolio);
        holding.setProperty(property);
        holding.setPurchasePrice(new BigDecimal("250000"));
        when(holdings.findByIdAndPortfolio_Id(20L, 10L)).thenReturn(Optional.of(holding));
        when(holdings.saveAndFlush(any())).thenAnswer(call -> {
            PortfolioHolding saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(21L);
            return saved;
        });
    }

    private PortfolioHoldingRequest createRequest() {
        PortfolioHoldingRequest request = new PortfolioHoldingRequest();
        request.setPropertyId(100L);
        return request;
    }

    @Test
    void createsByInternalPropertyIdAndPreservesUnknownAmounts() {
        PortfolioHoldingDTO result = service.create(10L, createRequest());
        assertEquals(100L, result.propertyId());
        assertEquals(new BigDecimal("100"), result.ownershipSharePercent());
        assertNull(result.purchasePrice());
        assertNull(result.financing().getOutstandingBalance());
        assertEquals(HoldingFinancingType.UNKNOWN, result.financing().getType());
        assertEquals(HoldingOccupancyStatus.UNKNOWN, result.occupancy().getStatus());
        verify(properties).findById(100L);
        verify(properties, never()).save(any());
    }

    @Test
    void rejectsDuplicatePropertyInSamePortfolio() {
        when(holdings.existsByPortfolio_IdAndProperty_Id(10L, 100L)).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(10L, createRequest()));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(holdings, never()).saveAndFlush(any());
    }

    @Test
    void missingPropertyIs404() {
        PortfolioHoldingRequest request = createRequest();
        request.setPropertyId(999L);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.create(10L, request)).getStatusCode());
    }

    @Test
    void foreignPortfolioIsCheckedBeforePropertiesOrHoldings() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.create(99L, createRequest())).getStatusCode());
        verifyNoInteractions(properties, holdings);
    }

    @Test
    void everyHoldingOperationScopesToTheParentOwner() {
        assertThrows(ResponseStatusException.class, () -> service.list(99L, 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.get(99L, 20L));
        assertThrows(ResponseStatusException.class, () -> service.update(99L, 20L, new PortfolioHoldingRequest()));
        assertThrows(ResponseStatusException.class, () -> service.delete(99L, 20L));
        verifyNoInteractions(properties, holdings);
    }

    @Test
    void aHoldingCannotBeReadUnderAnotherPortfolio() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.get(10L, 999L)).getStatusCode());
        verify(holdings).findByIdAndPortfolio_Id(999L, 10L);
    }

    @Test
    void returnsAnEmptyHoldingPage() {
        when(holdings.findByPortfolio_Id(eq(10L), any())).thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.list(10L, 0, 20).content().isEmpty());
    }

    @Test
    void patchesOnlySuppliedFields() {
        PortfolioHoldingRequest request = new PortfolioHoldingRequest();
        request.setOwnershipSharePercent(new BigDecimal("50"));
        PortfolioHoldingDTO result = service.update(10L, 20L, request);
        assertEquals(new BigDecimal("250000"), result.purchasePrice());
        assertEquals(new BigDecimal("50"), result.ownershipSharePercent());
    }

    @Test
    void jsonNullClearsOptionalFieldsAndSections() throws Exception {
        holding.setMonthlyPayment(new BigDecimal("1200"));
        holding.setFinancingType(HoldingFinancingType.FINANCED);
        holding.setOccupancyStatus(HoldingOccupancyStatus.RENTED);
        holding.setMonthlyRent(new BigDecimal("1800"));
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        PortfolioHoldingRequest request = json.readValue(
            "{\"purchasePrice\":null,\"financing\":null,\"occupancy\":null}", PortfolioHoldingRequest.class);
        PortfolioHoldingDTO result = service.update(10L, 20L, request);
        assertNull(result.purchasePrice());
        assertNull(result.financing().getMonthlyPayment());
        assertNull(result.occupancy().getMonthlyRent());
        assertEquals(HoldingFinancingType.UNKNOWN, result.financing().getType());
        assertEquals(HoldingOccupancyStatus.UNKNOWN, result.occupancy().getStatus());
    }

    @Test
    void omittedNestedSectionsRetainTheirValues() {
        holding.setFinancingType(HoldingFinancingType.FINANCED);
        holding.setMonthlyPayment(new BigDecimal("1200"));
        PortfolioHoldingDTO result = service.update(10L, 20L, new PortfolioHoldingRequest());
        assertEquals(new BigDecimal("1200"), result.financing().getMonthlyPayment());
    }

    @Test
    void nestedFinancingReplacementClearsPreviouslySuppliedFields() {
        holding.setMonthlyPayment(new BigDecimal("1200"));
        PortfolioHoldingRequest request = new PortfolioHoldingRequest();
        HoldingFinancingDTO finance = new HoldingFinancingDTO();
        finance.setType(HoldingFinancingType.CASH);
        request.setFinancing(finance);
        PortfolioHoldingDTO result = service.update(10L, 20L, request);
        assertNull(result.financing().getMonthlyPayment());
        assertEquals(HoldingFinancingType.CASH, result.financing().getType());
    }

    @Test
    void internalPropertyIdIsImmutable() {
        PortfolioHoldingRequest request = new PortfolioHoldingRequest();
        request.setPropertyId(101L);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 20L, request));
        assertEquals(100L, holding.getProperty().getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100.01", "25.555"})
    void rejectsInvalidOwnershipShare(String value) {
        PortfolioHoldingRequest request = createRequest();
        request.setOwnershipSharePercent(new BigDecimal(value));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void cannotClearOwnershipShareOrPropertyId() {
        PortfolioHoldingRequest request = new PortfolioHoldingRequest();
        request.setOwnershipSharePercent(null);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 20L, request));
        PortfolioHoldingRequest identity = new PortfolioHoldingRequest();
        identity.setPropertyId(null);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 20L, identity));
    }

    @Test
    void requiresPropertyIdOnCreate() {
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, new PortfolioHoldingRequest()));
    }

    @Test
    void rejectsFutureAcquisitionAndNegativeMoney() {
        PortfolioHoldingRequest request = createRequest();
        request.setAcquisitionDate(LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
        request.setAcquisitionDate(null);
        request.setPurchasePrice(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void rejectsMoneyBeyondDatabasePrecision() {
        PortfolioHoldingRequest request = createRequest();
        request.setPurchasePrice(new BigDecimal("1000000000000"));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void storesReportedFinancingWithoutCalculatingPayments() {
        PortfolioHoldingRequest request = createRequest();
        HoldingFinancingDTO financing = new HoldingFinancingDTO();
        financing.setType(HoldingFinancingType.FINANCED);
        financing.setLoanAmount(new BigDecimal("200000"));
        financing.setOutstandingBalance(new BigDecimal("195000"));
        financing.setBalanceAsOfDate(LocalDate.now().minusDays(1));
        financing.setInterestRatePercent(new BigDecimal("6.5"));
        financing.setLoanTermMonths(360);
        request.setFinancing(financing);
        PortfolioHoldingDTO result = service.create(10L, request);
        assertEquals(new BigDecimal("195000"), result.financing().getOutstandingBalance());
        assertNull(result.financing().getMonthlyPayment());
    }

    @Test
    void balanceRequiresAnAsOfDateAndViceVersa() {
        PortfolioHoldingRequest request = createRequest();
        HoldingFinancingDTO financing = new HoldingFinancingDTO();
        request.setFinancing(financing);
        financing.setOutstandingBalance(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
        financing.setOutstandingBalance(null);
        financing.setBalanceAsOfDate(LocalDate.now());
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void cashCannotContainLoanData() {
        PortfolioHoldingRequest request = createRequest();
        HoldingFinancingDTO financing = new HoldingFinancingDTO();
        financing.setType(HoldingFinancingType.CASH);
        financing.setLoanAmount(BigDecimal.TEN);
        request.setFinancing(financing);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void nestedValidationRejectsUnsupportedLoanTermsAndRates() {
        PortfolioHoldingRequest request = createRequest();
        HoldingFinancingDTO financing = new HoldingFinancingDTO();
        financing.setInterestRatePercent(new BigDecimal("101"));
        request.setFinancing(financing);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
        financing.setInterestRatePercent(null);
        financing.setLoanTermMonths(601);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void preservesNullRentForRentedPropertyWithoutRentData() {
        PortfolioHoldingRequest request = createRequest();
        HoldingOccupancyDTO occupancy = new HoldingOccupancyDTO();
        occupancy.setStatus(HoldingOccupancyStatus.RENTED);
        request.setOccupancy(occupancy);
        assertNull(service.create(10L, request).occupancy().getMonthlyRent());
    }

    @Test
    void rejectsReversedLeaseDatesAndRentForNonRentedProperty() {
        PortfolioHoldingRequest request = createRequest();
        HoldingOccupancyDTO occupancy = new HoldingOccupancyDTO();
        occupancy.setStatus(HoldingOccupancyStatus.RENTED);
        occupancy.setLeaseStartDate(LocalDate.of(2025, 2, 1));
        occupancy.setLeaseEndDate(LocalDate.of(2025, 1, 1));
        request.setOccupancy(occupancy);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
        occupancy.setLeaseStartDate(null);
        occupancy.setLeaseEndDate(null);
        occupancy.setStatus(HoldingOccupancyStatus.VACANT);
        occupancy.setMonthlyRent(BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void rejectsExplicitNullNestedEnums() {
        PortfolioHoldingRequest request = createRequest();
        HoldingOccupancyDTO occupancy = new HoldingOccupancyDTO();
        occupancy.setStatus(null);
        request.setOccupancy(occupancy);
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, request));
    }

    @Test
    void deletesOnlyTheHolding() {
        service.delete(10L, 20L);
        verify(holdings).delete(holding);
        verify(properties, never()).delete(any());
        verify(portfolios, never()).delete(any());
    }

    @Test
    void rejectsInvalidHoldingIdsAndPageSizes() {
        assertThrows(IllegalArgumentException.class, () -> service.get(10L, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.delete(10L, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(10L, 0, 101));
    }
}
