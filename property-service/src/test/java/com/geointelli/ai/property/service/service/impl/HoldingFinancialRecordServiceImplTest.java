package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.dto.FinancialRecordDTO;
import com.geointelli.ai.property.service.dto.FinancialRecordQuery;
import com.geointelli.ai.property.service.dto.FinancialRecordRequest;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import com.geointelli.ai.property.service.entity.FinancialRecordType;
import com.geointelli.ai.property.service.entity.HoldingFinancialRecord;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.FinancialRecordMapper;
import com.geointelli.ai.property.service.repository.HoldingFinancialRecordRepository;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Tests actual cash records, partial corrections and owner/parent scoping without external services. */
class HoldingFinancialRecordServiceImplTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final HoldingFinancialRecordRepository records = mock(HoldingFinancialRecordRepository.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final HoldingFinancialRecordServiceImpl service = new HoldingFinancialRecordServiceImpl(
        records, portfolios, holdings, currentUser, new FinancialRecordMapper(), VALIDATION.getValidator());
    private PortfolioHolding holding;
    private HoldingFinancialRecord record;

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
        when(portfolios.findOwnedForUpdate(10L, 7L)).thenReturn(Optional.of(portfolio));
        when(portfolios.findByIdAndUser_Id(10L, 7L)).thenReturn(Optional.of(portfolio));
        holding = new PortfolioHolding();
        holding.setId(20L);
        holding.setPortfolio(portfolio);
        holding.setMonthlyRent(new BigDecimal("3000"));
        when(holdings.findByIdAndPortfolio_Id(20L, 10L)).thenReturn(Optional.of(holding));
        record = new HoldingFinancialRecord();
        record.setId(30L);
        record.setHolding(holding);
        record.setCategory(FinancialRecordCategory.RENT);
        record.setAmount(new BigDecimal("2500"));
        record.setTransactionDate(LocalDate.of(2025, 1, 10));
        record.setDescription("Original");
        record.setCreatedAt(LocalDateTime.of(2025, 1, 10, 12, 0));
        when(records.findByIdAndHolding_Id(30L, 20L)).thenReturn(Optional.of(record));
        when(records.saveAndFlush(any())).thenAnswer(call -> {
            HoldingFinancialRecord value = call.getArgument(0);
            if (value.getId() == null) value.setId(31L);
            return value;
        });
    }

    private FinancialRecordRequest request() {
        FinancialRecordRequest request = new FinancialRecordRequest();
        request.setCategory(FinancialRecordCategory.RENT);
        request.setAmount(new BigDecimal("2500"));
        request.setTransactionDate(LocalDate.of(2025, 1, 10));
        return request;
    }

    @Test
    void getsActualRecordAfterCheckingTheWholeOwnershipChain() {
        FinancialRecordDTO result = service.get(10L, 20L, 30L);
        assertEquals(30L, result.id());
        assertEquals(20L, result.holdingId());
        assertEquals(new BigDecimal("2500"), result.amount());
        verify(portfolios).findByIdAndUser_Id(10L, 7L);
        verify(holdings).findByIdAndPortfolio_Id(20L, 10L);
        verify(records).findByIdAndHolding_Id(30L, 20L);
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void detailsRejectForeignPortfolioBeforeChildLookups() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.get(99L, 20L, 30L)).getStatusCode());
        verifyNoInteractions(holdings, records);
    }

    @Test
    void detailsRejectWrongHoldingAndCrossHoldingRecord() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.get(10L, 99L, 30L)).getStatusCode());
        verifyNoInteractions(records);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.get(10L, 20L, 99L)).getStatusCode());
    }

    @Test
    void detailsRequireBearerIdentityAndPositiveRecordId() {
        assertThrows(IllegalArgumentException.class, () -> service.get(10L, 20L, 0L));
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
            () -> service.get(10L, 20L, 30L)).getStatusCode());
    }

    @Test
    void recordsActualIncomeWithoutChangingProjectedHoldingFacts() {
        FinancialRecordRequest request = request();
        request.setDescription("  January rent received  ");
        FinancialRecordDTO result = service.create(10L, 20L, request);
        assertEquals(31L, result.id());
        assertEquals(20L, result.holdingId());
        assertEquals(10L, result.portfolioId());
        assertEquals(FinancialRecordType.INCOME, result.type());
        assertEquals("USD", result.currency());
        assertEquals("USER_REPORTED_ACTUAL", result.source());
        assertEquals("January rent received", result.description());
        assertEquals(new BigDecimal("3000"), holding.getMonthlyRent());
        verify(holdings, never()).save(any());
        verify(portfolios).findOwnedForUpdate(10L, 7L);
    }

    @Test
    void expenseDirectionComesFromCategoryRatherThanAmountSign() {
        FinancialRecordRequest request = request();
        request.setCategory(FinancialRecordCategory.REPAIRS);
        assertEquals(FinancialRecordType.EXPENSE, service.create(10L, 20L, request).type());
    }

    @Test
    void allCategoriesHaveConsistentDirections() {
        for (FinancialRecordCategory category : FinancialRecordCategory.values()) {
            boolean income = category == FinancialRecordCategory.RENT || category == FinancialRecordCategory.OTHER_INCOME;
            assertEquals(income ? FinancialRecordType.INCOME : FinancialRecordType.EXPENSE, category.getType());
        }
    }

    @Test
    void missingCreateFieldsAreRejectedBeforePersisting() {
        assertThrows(IllegalArgumentException.class,
            () -> service.create(10L, 20L, new FinancialRecordRequest()));
        verify(records, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "10.001", "1000000000000"})
    void rejectsInvalidAmounts(String amount) {
        FinancialRecordRequest request = request();
        request.setAmount(new BigDecimal(amount));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, request));
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void futureTransactionsAreNotActuals() {
        FinancialRecordRequest request = request();
        request.setTransactionDate(LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, request));
    }

    @Test
    void onlyUsdIsAccepted() {
        FinancialRecordRequest request = request();
        request.setCurrency("EUR");
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, request));
    }

    @Test
    void missingBearerIdentityDoesNotAccessRepositories() {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
            () -> service.list(10L, 20L, new FinancialRecordQuery())).getStatusCode());
        verifyNoInteractions(records, portfolios, holdings);
    }

    @Test
    void allOperationsRejectForeignPortfolioBeforeLookingUpChildren() {
        assertThrows(ResponseStatusException.class, () -> service.create(99L, 20L, request()));
        assertThrows(ResponseStatusException.class, () -> service.list(99L, 20L, new FinancialRecordQuery()));
        assertThrows(ResponseStatusException.class, () -> service.update(99L, 20L, 30L, request()));
        assertThrows(ResponseStatusException.class, () -> service.delete(99L, 20L, 30L));
        verifyNoInteractions(records, holdings);
    }

    @Test
    void wrongHoldingCannotBeUsedWithAnOwnedPortfolio() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.create(10L, 99L, request()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(records);
    }

    @Test
    void wrongHoldingCannotBeUsedForReadUpdateOrDelete() {
        assertThrows(ResponseStatusException.class, () -> service.list(10L, 99L, new FinancialRecordQuery()));
        assertThrows(ResponseStatusException.class, () -> service.update(10L, 99L, 30L, request()));
        assertThrows(ResponseStatusException.class, () -> service.delete(10L, 99L, 30L));
        verifyNoInteractions(records);
    }

    @Test
    void recordMustBelongToRequestedHolding() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.update(10L, 20L, 99L, request()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(records).findByIdAndHolding_Id(99L, 20L);
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void missingRecordCannotBeDeleted() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.delete(10L, 20L, 99L)).getStatusCode());
        verify(records, never()).delete(any());
    }

    @Test
    void correctionPreservesOmittedFactsAndOriginalCreationTime() {
        FinancialRecordRequest patch = new FinancialRecordRequest();
        patch.setAmount(new BigDecimal("2400"));
        FinancialRecordDTO result = service.update(10L, 20L, 30L, patch);
        assertEquals(new BigDecimal("2400"), result.amount());
        assertEquals(record.getCreatedAt(), result.createdAt());
        assertEquals(LocalDate.of(2025, 1, 10), result.transactionDate());
        assertEquals("Original", result.description());
        assertEquals(FinancialRecordCategory.RENT, result.category());
    }

    @Test
    void correctionOfCategoryUpdatesIncomeExpenseDirection() {
        FinancialRecordRequest patch = new FinancialRecordRequest();
        patch.setCategory(FinancialRecordCategory.INSURANCE);
        assertEquals(FinancialRecordType.EXPENSE, service.update(10L, 20L, 30L, patch).type());
    }

    @Test
    void descriptionCanBeExplicitlyCleared() {
        FinancialRecordRequest patch = new FinancialRecordRequest();
        patch.setDescription(null);
        assertNull(service.update(10L, 20L, 30L, patch).description());
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "amount", "currency", "transactionDate"})
    void requiredFactsCannotBeCleared(String field) throws Exception {
        FinancialRecordRequest patch = new ObjectMapper().registerModule(new JavaTimeModule())
            .readValue("{\"" + field + "\":null}", FinancialRecordRequest.class);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 20L, 30L, patch));
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOverlongDescription() {
        FinancialRecordRequest request = request();
        request.setDescription("x".repeat(2001));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, request));
    }

    @Test
    void optionalPeriodCanCoverFutureDaysWithoutCreatingFutureTransactions() {
        FinancialRecordRequest request = request();
        request.setTransactionDate(LocalDate.now());
        request.setPeriodStart(LocalDate.now());
        request.setPeriodEnd(LocalDate.now().plusMonths(1));
        assertEquals(request.getPeriodEnd(), service.create(10L, 20L, request).periodEnd());
    }

    @Test
    void rejectsUnpairedOrReversedPeriods() {
        FinancialRecordRequest request = request();
        request.setPeriodStart(LocalDate.of(2025, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, request));
        request.setPeriodEnd(LocalDate.of(2024, 12, 31));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, request));
    }

    @Test
    void patchValidatesPeriodAgainstExistingOtherEndpointBeforeMutation() {
        record.setPeriodStart(LocalDate.of(2025, 1, 1));
        record.setPeriodEnd(LocalDate.of(2025, 1, 31));
        FinancialRecordRequest patch = new FinancialRecordRequest();
        patch.setPeriodStart(LocalDate.of(2025, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 20L, 30L, patch));
        assertEquals(LocalDate.of(2025, 1, 1), record.getPeriodStart());
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void validPartialPeriodCorrectionRetainsTheOtherEndpoint() {
        record.setPeriodStart(LocalDate.of(2025, 1, 1));
        record.setPeriodEnd(LocalDate.of(2025, 1, 31));
        FinancialRecordRequest patch = new FinancialRecordRequest();
        patch.setPeriodEnd(LocalDate.of(2025, 2, 10));
        FinancialRecordDTO result = service.update(10L, 20L, 30L, patch);
        assertEquals(LocalDate.of(2025, 1, 1), result.periodStart());
        assertEquals(LocalDate.of(2025, 2, 10), result.periodEnd());
    }

    @Test
    void periodMustBeClearedTogether() {
        record.setPeriodStart(LocalDate.of(2025, 1, 1));
        record.setPeriodEnd(LocalDate.of(2025, 1, 31));
        FinancialRecordRequest patch = new FinancialRecordRequest();
        patch.setPeriodStart(null);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 20L, 30L, patch));
        patch.setPeriodEnd(null);
        FinancialRecordDTO result = service.update(10L, 20L, 30L, patch);
        assertNull(result.periodStart());
        assertNull(result.periodEnd());
    }

    @Test
    void listsWithInclusiveFiltersAndStableSorting() {
        FinancialRecordQuery query = new FinancialRecordQuery();
        query.setFromDate(LocalDate.of(2025, 1, 1));
        query.setToDate(LocalDate.of(2025, 1, 31));
        query.setCategory(FinancialRecordCategory.RENT);
        PageRequest page = PageRequest.of(0, 20);
        when(records.findRecords(eq(20L), eq(query.getFromDate()), eq(query.getToDate()),
            eq(query.getCategory()), any())).thenReturn(new PageImpl<>(List.of(record), page, 1));
        PortfolioPageDTO<FinancialRecordDTO> result = service.list(10L, 20L, query);
        assertEquals(1, result.totalElements());
        assertEquals(30L, result.content().get(0).id());
        ArgumentCaptor<PageRequest> paging = ArgumentCaptor.forClass(PageRequest.class);
        verify(records).findRecords(eq(20L), eq(query.getFromDate()), eq(query.getToDate()), eq(query.getCategory()), paging.capture());
        assertEquals("transactionDate: DESC,id: DESC", paging.getValue().getSort().toString().replace(", ", ","));
    }

    @Test
    void missingDataStaysEmpty() {
        when(records.findRecords(eq(20L), isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertTrue(service.list(10L, 20L, new FinancialRecordQuery()).content().isEmpty());
    }

    @Test
    void rejectsInvalidPagingAndDateRange() {
        FinancialRecordQuery query = new FinancialRecordQuery();
        query.setPage(-1);
        assertThrows(IllegalArgumentException.class, () -> service.list(10L, 20L, query));
        query.setPage(0);
        query.setSize(101);
        assertThrows(IllegalArgumentException.class, () -> service.list(10L, 20L, query));
        query.setSize(20);
        query.setFromDate(LocalDate.of(2025, 2, 1));
        query.setToDate(LocalDate.of(2025, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.list(10L, 20L, query));
        verify(records, never()).findRecords(anyLong(), any(), any(), any(), any());
    }

    @Test
    void deletesOnlyTheActualRecord() {
        service.delete(10L, 20L, 30L);
        verify(records).delete(record);
        verify(holdings, never()).delete(any());
        verify(portfolios, never()).delete(any());
    }

    @Test
    void invalidIdsAndNullRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(0L, 20L, request()));
        assertThrows(IllegalArgumentException.class, () -> service.list(10L, null, new FinancialRecordQuery()));
        assertThrows(IllegalArgumentException.class, () -> service.delete(10L, 20L, -1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(10L, 20L, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(10L, 20L, null));
    }
}
