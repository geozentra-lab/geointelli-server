package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.SavedSearchesCardDTO;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserDashboardSavedSearchesService;

import lombok.RequiredArgsConstructor;

/**
 * Reads the current user's saved searches through the existing saved-search repository (SCRUM-66
 * storage); no saved search is executed, nothing is scheduled or notified.
 */
@Service
@RequiredArgsConstructor
// Not read-only: resolving the current user may insert their row on a first login (same as SavedSearchServiceImpl).
@Transactional
public class UserDashboardSavedSearchesServiceImpl implements UserDashboardSavedSearchesService {
    private final SavedSearchRepository savedSearchRepository;
    private final CurrentUserService currentUserService;

    /** {@inheritDoc} */
    @Override
    public SavedSearchesCardDTO getSavedSearchesCard(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        // Owner comes from the bearer token (401 without one); every query below is filtered by this id.
        Long userId = currentUserService.getCurrentUserEntity().getId();

        long count = savedSearchRepository.countByUser_Id(userId);
        List<SavedSearchesCardDTO.RecentSavedSearch> recent = savedSearchRepository
            .findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
            .stream().map(UserDashboardSavedSearchesServiceImpl::toRecent).toList();

        SavedSearchesCardDTO card = new SavedSearchesCardDTO();
        card.setScope(SavedSearchesCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(SavedSearchesCardDTO.STATUS_OK);
        card.setError(null);
        card.setCount(count);
        card.setLimit(limit);
        card.setRecent(recent);
        return card;
    }

    /** Summary fields only: identity, name and monitoring state. The stored filters are not copied. */
    private static SavedSearchesCardDTO.RecentSavedSearch toRecent(SavedSearch search) {
        return new SavedSearchesCardDTO.RecentSavedSearch(
            search.getId(),
            search.getName(),
            search.getCadence(),
            search.isPaused(),
            search.getCreatedAt(),
            search.getUpdatedAt());
    }
}
