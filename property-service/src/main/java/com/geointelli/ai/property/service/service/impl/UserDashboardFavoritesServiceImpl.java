package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.FavoritesCardDTO;
import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Favorite;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.repository.FavoriteRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserDashboardFavoritesService;

import lombok.RequiredArgsConstructor;

/** Reads the current user's favorites through the existing favorite repository; no valuation, no notification. */
@Service
@RequiredArgsConstructor
// Not read-only: resolving the current user may insert their row on a first login (same as SavedSearchServiceImpl).
@Transactional
public class UserDashboardFavoritesServiceImpl implements UserDashboardFavoritesService {
    private final FavoriteRepository favoriteRepository;
    private final CurrentUserService currentUserService;

    /** {@inheritDoc} */
    @Override
    public FavoritesCardDTO getFavoritesCard(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        // Owner comes from the bearer token (401 without one); every query below is filtered by this id.
        Long userId = currentUserService.getCurrentUser().getId();

        long count = favoriteRepository.countByUserId(userId);
        List<FavoritesCardDTO.RecentFavorite> recent = favoriteRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
            .stream().map(UserDashboardFavoritesServiceImpl::toRecent).toList();

        FavoritesCardDTO card = new FavoritesCardDTO();
        card.setScope(FavoritesCardDTO.SCOPE_CURRENT_USER);
        card.setGeneratedAt(LocalDateTime.now());
        card.setStatus(FavoritesCardDTO.STATUS_OK);
        card.setError(null);
        card.setCount(count);
        card.setLimit(limit);
        card.setRecent(recent);
        return card;
    }

    /** Display fields only, read from the property the favorite points to; a missing relation yields null, not a failure. */
    private static FavoritesCardDTO.RecentFavorite toRecent(Favorite favorite) {
        Property property = favorite.getProperty();
        Address address = property == null ? null : property.getAddress();
        County county = property == null ? null : property.getCounty();
        return new FavoritesCardDTO.RecentFavorite(
            property == null ? null : property.getId(),
            property == null ? null : property.getFolio(),
            address == null ? null : address.getAddress(),
            county == null ? null : county.getName(),
            county == null || county.getState() == null ? null : county.getState().getCode(),
            favorite.getCreatedAt());
    }
}
