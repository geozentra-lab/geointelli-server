package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.AlertsCardDTO;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserDashboardAlertsService;

import lombok.RequiredArgsConstructor;

/**
 * Reads the current user's alert rules through the existing alert-rule repository (SCRUM-67
 * storage). Nothing is evaluated or delivered, and the alert outbox (notifications) is never read.
 */
@Service
@RequiredArgsConstructor
// Not read-only: resolving the current user may insert their row on a first login (same as SavedSearchServiceImpl).
@Transactional
public class UserDashboardAlertsServiceImpl implements UserDashboardAlertsService {
    private final AlertRuleRepository alertRuleRepository;
    private final CurrentUserService currentUserService;

    /** {@inheritDoc} */
    @Override
    public AlertsCardDTO getAlertsCard(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        // Owner comes from the bearer token (401 without one); every query below is filtered by this id.
        Long userId = currentUserService.getCurrentUserEntity().getId();

        long count = alertRuleRepository.countByUser_Id(userId);
        long pausedCount = alertRuleRepository.countEffectivelyPausedByUserId(userId);
        List<AlertsCardDTO.RecentAlertRule> recent = alertRuleRepository
            .findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
            .stream().map(UserDashboardAlertsServiceImpl::toRecent).toList();

        AlertsCardDTO card = new AlertsCardDTO();
        card.setScope(AlertsCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(AlertsCardDTO.STATUS_OK);
        card.setError(null);
        card.setCount(count);
        card.setActiveCount(count - pausedCount);
        card.setPausedCount(pausedCount);
        card.setLimit(limit);
        card.setRecent(recent);
        return card;
    }

    /** Summary fields only; effectivePaused is computed the same way as in AlertRuleMapper. */
    private static AlertsCardDTO.RecentAlertRule toRecent(AlertRule rule) {
        SavedSearch search = rule.getSavedSearch();
        return new AlertsCardDTO.RecentAlertRule(
            rule.getId(),
            rule.getName(),
            rule.getEventType(),
            rule.getProperty() == null ? null : rule.getProperty().getId(),
            search == null ? null : search.getId(),
            rule.getCadence(),
            rule.isPaused(),
            rule.isPaused() || (search != null && search.isPaused()),
            rule.getCreatedAt(),
            rule.getUpdatedAt());
    }
}
