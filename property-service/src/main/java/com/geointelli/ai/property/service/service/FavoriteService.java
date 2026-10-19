package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.FavoriteDTO;

public interface FavoriteService {
    FavoriteDTO addFavorite(Long propertyId);
    void removeFavorite(Long propertyId);
    boolean isFavorite(Long propertyId);
    List<FavoriteDTO> getMyFavorites();
}
