package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.ActivityCardDTO;
import com.geointelli.ai.property.service.dto.ActivityCardDTO.ActivityItem;
import com.geointelli.ai.property.service.dto.ActivityCardDTO.ActivityType;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.AuditableEntity;
import com.geointelli.ai.property.service.entity.Favorite;
import com.geointelli.ai.property.service.entity.Portfolio;
import com.geointelli.ai.property.service.entity.PortfolioHolding;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.repository.FavoriteRepository;
import com.geointelli.ai.property.service.repository.PortfolioHoldingRepository;
import com.geointelli.ai.property.service.repository.PortfolioRepository;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserDashboardActivityService;

import lombok.RequiredArgsConstructor;

/**
 * Derives the current user's recent activity from the audit timestamps of the records they own.
 * Five bounded, owner-scoped queries (one per record type) are merged in memory and cut to the
 * requested limit. Reading {@code limit} rows per table ordered by {@code updated_at} is sufficient:
 * a row's {@code updated_at} is never earlier than its {@code created_at}, so every row that could
 * contribute one of the newest {@code limit} items is among them.
 */
@Service
@RequiredArgsConstructor
// Not read-only: resolving the current user may insert their row on a first login (same as the other cards).
@Transactional
public class UserDashboardActivityServiceImpl implements UserDashboardActivityService {

    private final CurrentUserService currentUserService;
    private final FavoriteRepository favoriteRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;

    @Override
    public ActivityCardDTO getActivityCard(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        Long userId = currentUserService.getCurrentUserEntity().getId();
        Pageable newest = PageRequest.of(0, limit);

        List<ActivityItem> items = new ArrayList<>();
        for (Favorite f : favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, newest)) {
            Long propertyId = f.getProperty().getId();
            items.add(new ActivityItem(ActivityType.FAVORITE_ADDED, f.getCreatedAt(), f.getId(),
                "property " + propertyId, propertyId, null));
        }
        for (SavedSearch s : savedSearchRepository.findByUser_IdOrderByUpdatedAtDesc(userId, newest)) {
            addCreatedAndUpdated(items, s, ActivityType.SAVED_SEARCH_CREATED, ActivityType.SAVED_SEARCH_UPDATED,
                s.getId(), s.getName(), null, null);
        }
        for (AlertRule r : alertRuleRepository.findByUser_IdOrderByUpdatedAtDesc(userId, newest)) {
            Long propertyId = r.getProperty() == null ? null : r.getProperty().getId();
            addCreatedAndUpdated(items, r, ActivityType.ALERT_RULE_CREATED, ActivityType.ALERT_RULE_UPDATED,
                r.getId(), r.getName(), propertyId, null);
        }
        for (Portfolio p : portfolioRepository.findByUser_IdOrderByUpdatedAtDesc(userId, newest)) {
            addCreatedAndUpdated(items, p, ActivityType.PORTFOLIO_CREATED, ActivityType.PORTFOLIO_UPDATED,
                p.getId(), p.getName(), null, null);
        }
        for (PortfolioHolding h : portfolioHoldingRepository.findByPortfolio_User_IdOrderByUpdatedAtDesc(userId, newest)) {
            Long propertyId = h.getProperty().getId();
            addCreatedAndUpdated(items, h, ActivityType.HOLDING_ADDED, ActivityType.HOLDING_UPDATED,
                h.getId(), "property " + propertyId + " in " + h.getPortfolio().getName(), propertyId,
                h.getPortfolio().getId());
        }

        List<ActivityItem> recent = items.stream()
            .sorted(Comparator.comparing(ActivityItem::occurredAt).reversed())
            .limit(limit)
            .toList();

        ActivityCardDTO card = new ActivityCardDTO();
        card.setScope(ActivityCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(ActivityCardDTO.STATUS_OK);
        card.setError(null);
        card.setLimit(limit);
        card.setRecent(recent);
        return card;
    }

    /**
     * Adds the "created" item of a record and, when the record was modified after its creation, the
     * "updated" item too. Both use the timestamps stored on the row; nothing is synthesized.
     */
    private static void addCreatedAndUpdated(List<ActivityItem> items, AuditableEntity record,
                                             ActivityType createdType, ActivityType updatedType,
                                             Long entityId, String label, Long propertyId, Long portfolioId) {
        items.add(new ActivityItem(createdType, record.getCreatedAt(), entityId, label, propertyId, portfolioId));
        if (record.getUpdatedAt() != null && record.getUpdatedAt().isAfter(record.getCreatedAt())) {
            items.add(new ActivityItem(updatedType, record.getUpdatedAt(), entityId, label, propertyId, portfolioId));
        }
    }
}
