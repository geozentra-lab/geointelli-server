package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.dto.RecommendationResponseDTO;
import com.geointelli.ai.property.service.dto.RecommendationResponseDTO.Status;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.entity.UserPreferences;
import com.geointelli.ai.property.service.repository.UserPreferencesRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PropertySearchService;

/** Checks owner isolation, supported evidence and cold-start behavior without external providers. */
class RecommendationServiceImplTest {
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
    private final PropertySearchService search = mock(PropertySearchService.class);
    private final RecommendationServiceImpl service = new RecommendationServiceImpl(currentUser, preferences, search);
    private UserPreferences saved;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        saved = new UserPreferences();
        saved.setUserId(7L);
        County county = new County();
        county.setId(31L);
        saved.setTargetCounties(List.of(county));
        when(preferences.findById(7L)).thenReturn(Optional.of(saved));
        when(search.findRecommendationCandidates(List.of(31L), 20)).thenReturn(List.of(card(1L), card(2L)));
    }

    @Test
    void matchesOwnCountiesAndReturnsExplainedStableRanks() {
        RecommendationResponseDTO result = service.recommend(20);
        assertEquals(Status.READY, result.status());
        assertEquals(List.of(31L), result.appliedCountyIds());
        assertEquals(List.of(1, 2), result.content().stream().map(item -> item.rank()).toList());
        assertTrue(result.content().get(0).reasons().get(0).contains("Miami-Dade"));
        assertEquals("UNKNOWN", result.content().get(0).listingStatus());
        assertEquals("NOT_REQUESTED", result.content().get(0).budgetMatch());
        assertNull(result.sourceAsOf());
        verify(preferences).findById(7L);
        verify(search).findRecommendationCandidates(List.of(31L), 20);
    }

    @Test
    void budgetAndFreeTextGoalsAreNotPretendedToMatch() {
        saved.setMinBudget(new BigDecimal("100000"));
        saved.setMaxBudget(new BigDecimal("400000"));
        saved.setInvestmentGoals(List.of("rental income"));
        RecommendationResponseDTO result = service.recommend(20);
        assertEquals(Status.PARTIAL, result.status());
        assertEquals(saved.getMaxBudget(), result.maxBudget());
        assertEquals("UNKNOWN", result.content().get(0).budgetMatch());
        assertEquals("UNKNOWN", result.content().get(0).goalMatch());
        assertTrue(result.limitations().stream().anyMatch(text -> text.startsWith("Budget was not applied")));
    }

    @Test
    void missingPreferencesNeverFallsBackToEveryProperty() {
        when(preferences.findById(7L)).thenReturn(Optional.empty());
        assertEquals(Status.NO_PREFERENCES, service.recommend(20).status());
        verifyNoInteractions(search);
    }

    @Test
    void budgetOnlyPreferencesRequireLocation() {
        saved.setTargetCounties(List.of());
        saved.setMaxBudget(BigDecimal.TEN);
        assertEquals(Status.LOCATION_REQUIRED, service.recommend(20).status());
        verifyNoInteractions(search);
    }

    @Test
    void emptyCountiesRemainEmpty() {
        when(search.findRecommendationCandidates(List.of(31L), 20)).thenReturn(List.of());
        RecommendationResponseDTO result = service.recommend(20);
        assertEquals(Status.NO_MATCHES, result.status());
        assertTrue(result.content().isEmpty());
    }

    @Test
    void changingAuthenticatedUserReadsOnlyTheirPreferences() {
        User other = new User();
        other.setId(8L);
        when(currentUser.getCurrentUserEntity()).thenReturn(other);
        when(preferences.findById(8L)).thenReturn(Optional.empty());
        assertEquals(Status.NO_PREFERENCES, service.recommend(20).status());
        verify(preferences, never()).findById(7L);
        verifyNoInteractions(search);
    }

    @Test
    void missingIdentityIsRejectedBeforeReadingPreferences() {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThrows(ResponseStatusException.class, () -> service.recommend(20));
        verifyNoInteractions(preferences, search);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, Integer.MAX_VALUE})
    void rejectsUnboundedRequests(int limit) {
        assertThrows(IllegalArgumentException.class, () -> service.recommend(limit));
        verifyNoInteractions(currentUser, preferences, search);
    }

    private PropertySearchItem card(long id) {
        return new PropertySearchItem(id, null, 31L, "Miami-Dade", "FL", "Florida", null, null, null,
            null, null, null, null, null, null, null, null, null);
    }
}
