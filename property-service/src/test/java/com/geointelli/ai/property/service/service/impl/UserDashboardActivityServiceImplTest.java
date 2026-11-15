package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.geointelli.ai.property.service.dto.ActivityCardDTO;
import com.geointelli.ai.property.service.dto.ActivityCardDTO.ActivityItem;
import com.geointelli.ai.property.service.dto.ActivityCardDTO.ActivityType;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.AuditableEntity;
import com.geointelli.ai.property.service.entity.Favorite;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.repository.FavoriteRepository;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Verifies how the five owned record types are merged, ordered and cut into the recent-activity card. */
class UserDashboardActivityServiceImplTest {
    private static final long OWNER = 7L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 9, 0);

    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final FavoriteRepository favorites = mock(FavoriteRepository.class);
    private final SavedSearchRepository searches = mock(SavedSearchRepository.class);
    private final AlertRuleRepository rules = mock(AlertRuleRepository.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
    private final UserDashboardActivityServiceImpl service = new UserDashboardActivityServiceImpl(
        currentUser, favorites, searches, rules, portfolios, holdings);

    @BeforeEach
    void owner() {
        User user = new User();
        user.setId(OWNER);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
    }

    @Test
    void mergesEveryRecordTypeNewestFirstAndCutsToTheLimit() {
        Portfolio portfolio = stamped(portfolio(30L, "Activity card test"), 4, 4);
        when(favorites.findByUserIdOrderByCreatedAtDesc(eq(OWNER), any())).thenReturn(List.of(favorite(10L, 4L, 1)));
        when(searches.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any()))
            .thenReturn(List.of(stamped(search(20L, "Miami homes"), 2, 6)));
        when(rules.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any()))
            .thenReturn(List.of(stamped(rule(40L, "New Miami matches", null), 3, 3)));
        when(portfolios.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any())).thenReturn(List.of(portfolio));
        when(holdings.findByPortfolio_User_IdOrderByUpdatedAtDesc(eq(OWNER), any()))
            .thenReturn(List.of(stamped(holding(50L, portfolio, 4L), 5, 5)));

        ActivityCardDTO card = service.getActivityCard(5);

        assertEquals(ActivityCardDTO.SCOPE_CURRENT_USER, card.getScope());
        assertEquals(ActivityCardDTO.STATUS_OK, card.getStatus());
        assertNull(card.getError());
        assertEquals(5, card.getLimit());
        assertNotNull(card.getGeneratedAt());
        assertEquals(List.of(ActivityType.SAVED_SEARCH_UPDATED, ActivityType.HOLDING_ADDED,
                ActivityType.PORTFOLIO_CREATED, ActivityType.ALERT_RULE_CREATED, ActivityType.SAVED_SEARCH_CREATED),
            types(card));
        ActivityItem holding = card.getRecent().get(1);
        assertEquals(new ActivityItem(ActivityType.HOLDING_ADDED, at(5), 50L, "property 4 in Activity card test",
            4L, 30L), holding);
        assertNull(card.getRecent().get(3).propertyId(), "a rule on a saved search has no property");

        assertEquals(ActivityType.FAVORITE_ADDED, service.getActivityCard(6).getRecent().get(5).type());
    }

    @Test
    void addsAnUpdatedItemOnlyWhenTheRecordChangedAfterItWasCreated() {
        when(searches.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any())).thenReturn(List.of(
            stamped(search(21L, "Renamed"), 1, 3),
            stamped(search(20L, "Never changed"), 2, 2)));

        ActivityCardDTO card = service.getActivityCard(5);

        assertEquals(List.of(
                new ActivityItem(ActivityType.SAVED_SEARCH_UPDATED, at(3), 21L, "Renamed", null, null),
                new ActivityItem(ActivityType.SAVED_SEARCH_CREATED, at(2), 20L, "Never changed", null, null),
                new ActivityItem(ActivityType.SAVED_SEARCH_CREATED, at(1), 21L, "Renamed", null, null)),
            card.getRecent());
    }

    /** On insert the two audit columns read the clock separately; such a gap is not a modification. */
    @Test
    void ignoresAnUpdatedAtWithinTheCreationToleranceOfTheInsert() {
        Duration tolerance = UserDashboardActivityServiceImpl.CREATION_TOLERANCE;
        SavedSearch inserted = search(20L, "Clock gap on insert");
        inserted.setCreatedAt(at(1));
        inserted.setUpdatedAt(at(1).plus(tolerance));
        SavedSearch edited = search(21L, "Edited");
        edited.setCreatedAt(at(1));
        edited.setUpdatedAt(at(1).plus(tolerance).plusNanos(1_000));
        when(searches.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any())).thenReturn(List.of(edited, inserted));

        ActivityCardDTO card = service.getActivityCard(5);

        assertEquals(List.of(ActivityType.SAVED_SEARCH_UPDATED, ActivityType.SAVED_SEARCH_CREATED,
            ActivityType.SAVED_SEARCH_CREATED), types(card));
        assertEquals(List.of(21L, 21L, 20L), card.getRecent().stream().map(ActivityItem::entityId).toList());
    }

    @Test
    void ordersEqualTimesByNewestRecordThenByTypeSoTheCardIsStable() {
        Portfolio portfolio = stamped(portfolio(11L, "Same id as a search"), 1, 1);
        when(searches.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any())).thenReturn(List.of(
            stamped(search(10L, "Older"), 1, 1),
            stamped(search(11L, "Newer"), 1, 1)));
        when(portfolios.findByUser_IdOrderByUpdatedAtDesc(eq(OWNER), any())).thenReturn(List.of(portfolio));

        ActivityCardDTO card = service.getActivityCard(5);

        assertEquals(List.of(11L, 11L, 10L), card.getRecent().stream().map(ActivityItem::entityId).toList());
        assertEquals(List.of(ActivityType.SAVED_SEARCH_CREATED, ActivityType.PORTFOLIO_CREATED,
            ActivityType.SAVED_SEARCH_CREATED), types(card));
    }

    @Test
    void readsAtMostLimitRowsPerRecordTypeForTheOwnerWithTiesBrokenByNewestId() {
        service.getActivityCard(3);

        Pageable expected = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "id"));
        verify(favorites).findByUserIdOrderByCreatedAtDesc(OWNER, expected);
        verify(searches).findByUser_IdOrderByUpdatedAtDesc(OWNER, expected);
        verify(rules).findByUser_IdOrderByUpdatedAtDesc(OWNER, expected);
        verify(portfolios).findByUser_IdOrderByUpdatedAtDesc(OWNER, expected);
        verify(holdings).findByPortfolio_User_IdOrderByUpdatedAtDesc(OWNER, expected);
    }

    @Test
    void returnsAnEmptyCardWhenTheUserOwnsNothing() {
        ActivityCardDTO card = service.getActivityCard(5);

        assertEquals(ActivityCardDTO.STATUS_OK, card.getStatus());
        assertTrue(card.getRecent().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 21})
    void rejectsALimitOutsideOneToTwentyWithoutReading(int limit) {
        assertThrows(IllegalArgumentException.class, () -> service.getActivityCard(limit));
        verifyNoInteractions(currentUser, favorites, searches, rules, portfolios, holdings);
    }

    private static List<ActivityType> types(ActivityCardDTO card) {
        return card.getRecent().stream().map(ActivityItem::type).toList();
    }

    private static LocalDateTime at(int minute) {
        return START.plusMinutes(minute);
    }

    private static <T extends AuditableEntity> T stamped(T record, int createdMinute, int updatedMinute) {
        record.setCreatedAt(at(createdMinute));
        record.setUpdatedAt(at(updatedMinute));
        return record;
    }

    private static Property property(long id) {
        Property property = new Property();
        property.setId(id);
        return property;
    }

    private static Favorite favorite(long id, long propertyId, int minute) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setProperty(property(propertyId));
        return stamped(favorite, minute, minute);
    }

    private static SavedSearch search(long id, String name) {
        SavedSearch search = new SavedSearch();
        search.setId(id);
        search.setName(name);
        return search;
    }

    private static AlertRule rule(long id, String name, Property property) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setName(name);
        rule.setProperty(property);
        return rule;
    }

    private static Portfolio portfolio(long id, String name) {
        Portfolio portfolio = new Portfolio();
        portfolio.setId(id);
        portfolio.setName(name);
        return portfolio;
    }

    private static PortfolioHolding holding(long id, Portfolio portfolio, long propertyId) {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setId(id);
        holding.setPortfolio(portfolio);
        holding.setProperty(property(propertyId));
        return holding;
    }
}
