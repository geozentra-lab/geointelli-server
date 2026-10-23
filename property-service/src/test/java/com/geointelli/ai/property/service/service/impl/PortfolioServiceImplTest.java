package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.PortfolioDTO;
import com.geointelli.ai.property.service.dto.PortfolioPageDTO;
import com.geointelli.ai.property.service.dto.PortfolioRequest;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.PortfolioMapper;
import com.geointelli.ai.property.service.repository.PortfolioHoldingCount;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Exercises owner scoping, partial updates and deletion without connecting to an application database. */
class PortfolioServiceImplTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PortfolioServiceImpl service = new PortfolioServiceImpl(
        portfolios, holdings, currentUser, new PortfolioMapper(), VALIDATION.getValidator());
    private Portfolio portfolio;

    @AfterAll
    static void closeValidation() { VALIDATION.close(); }

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        portfolio = new Portfolio();
        portfolio.setId(10L);
        portfolio.setUser(user);
        portfolio.setName("Rentals");
        portfolio.setDescription("Original");
        when(portfolios.findByIdAndUser_Id(10L, 7L)).thenReturn(Optional.of(portfolio));
        when(portfolios.findOwnedForUpdate(10L, 7L)).thenReturn(Optional.of(portfolio));
        when(portfolios.saveAndFlush(any())).thenAnswer(call -> {
            Portfolio saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(11L);
            return saved;
        });
    }

    @Test
    void createsForTheAuthenticatedOwnerAndTrimsValues() {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("  Florida rentals  ");
        request.setDescription("  Long term  ");
        PortfolioDTO result = service.create(request);
        assertEquals("Florida rentals", result.name());
        assertEquals("Long term", result.description());
        assertEquals("USD", result.currency());
        assertEquals(0, result.holdingCount());
        ArgumentCaptor<Portfolio> saved = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolios).saveAndFlush(saved.capture());
        assertEquals(7L, saved.getValue().getUser().getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsInvalidNames(String name) {
        PortfolioRequest request = new PortfolioRequest();
        request.setName(name);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(portfolios, never()).saveAndFlush(any());
    }

    @Test
    void requiresNameOnCreation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new PortfolioRequest()));
    }

    @Test
    void rejectsOverlongNamesAndDescriptions() {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("n".repeat(121));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setName("Valid");
        request.setDescription("d".repeat(2001));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void getsOnlyAnOwnedPortfolioWithItsCount() {
        when(holdings.countByPortfolio_Id(10L)).thenReturn(3L);
        assertEquals(3, service.get(10L).holdingCount());
        verify(portfolios).findByIdAndUser_Id(10L, 7L);
        verify(portfolios, never()).findById(anyLong());
    }

    @Test
    void foreignAndMissingPortfoliosReturn404() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.get(99L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(holdings);
    }

    @Test
    void patchPreservesOmittedFields() {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("  New name  ");
        PortfolioDTO result = service.update(10L, request);
        assertEquals("New name", result.name());
        assertEquals("Original", result.description());
        verify(portfolios).findOwnedForUpdate(10L, 7L);
    }

    @Test
    void explicitNullClearsDescriptionWithoutClearingName() {
        PortfolioRequest request = new PortfolioRequest();
        request.setDescription(null);
        PortfolioDTO result = service.update(10L, request);
        assertNull(result.description());
        assertEquals("Rentals", result.name());
    }

    @Test
    void explicitNullCannotClearName() {
        PortfolioRequest request = new PortfolioRequest();
        request.setName(null);
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, request));
        assertEquals("Rentals", portfolio.getName());
    }

    @Test
    void deletesChildrenBeforeTheOwnedPortfolio() {
        service.delete(10L);
        InOrder order = inOrder(portfolios, holdings);
        order.verify(portfolios).findOwnedForUpdate(10L, 7L);
        order.verify(holdings).deleteForPortfolio(10L);
        order.verify(portfolios).delete(portfolio);
    }

    @Test
    void listsOwnedPortfoliosAndLoadsCountsInOneQuery() {
        when(portfolios.findByUser_Id(eq(7L), any())).thenReturn(new PageImpl<>(List.of(portfolio)));
        PortfolioHoldingCount count = mock(PortfolioHoldingCount.class);
        when(count.getPortfolioId()).thenReturn(10L);
        when(count.getHoldingCount()).thenReturn(2L);
        when(holdings.countForPortfolios(List.of(10L))).thenReturn(List.of(count));
        PortfolioPageDTO<PortfolioDTO> result = service.list(0, 20);
        assertEquals(1, result.totalElements());
        assertEquals(2, result.content().get(0).holdingCount());
        ArgumentCaptor<PageRequest> paging = ArgumentCaptor.forClass(PageRequest.class);
        verify(portfolios).findByUser_Id(eq(7L), paging.capture());
        assertEquals("id: DESC", paging.getValue().getSort().toString());
        verify(holdings, never()).countByPortfolio_Id(anyLong());
    }

    @Test
    void emptyPageStaysEmptyWithoutCountQueries() {
        when(portfolios.findByUser_Id(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.list(0, 20).content().isEmpty());
        verifyNoInteractions(holdings);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void rejectsInvalidPagination(int page, int size) {
        assertThrows(IllegalArgumentException.class, () -> service.list(page, size));
        verify(portfolios, never()).findByUser_Id(anyLong(), any());
    }

    @Test
    void requiresABearerIdentifiedUser() {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.list(0, 20));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(holdings);
    }

    @Test
    void rejectsInvalidIdsBeforeLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.get(null));
        assertThrows(IllegalArgumentException.class, () -> service.delete(0L));
    }
}
