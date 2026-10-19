package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.FavoriteDTO;
import com.geointelli.ai.property.service.entity.Favorite;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.FavoriteMapper;
import com.geointelli.ai.property.service.repository.FavoriteRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.FavoriteService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final PropertyRepository propertyRepository;
    private final FavoriteMapper favoriteMapper;
    private final CurrentUserService currentUserService;

    @Override
    public FavoriteDTO addFavorite(Long propertyId){
        User currentUser = currentUserService.getCurrentUserEntity();
        if(favoriteRepository.existsByUserIdAndPropertyId(currentUser.getId(), propertyId)) {
            throw new IllegalStateException("property is already a favorite");
        }
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> 
                    new RuntimeException("Property not found"));

        Favorite favorite = new Favorite();
        favorite.setProperty(property);
        favorite.setUser(currentUser);                   
        return favoriteMapper.toDTO(favoriteRepository.save(favorite));
    }

    @Override
    public void removeFavorite(Long propertyId) {
        User user = currentUserService.getCurrentUserEntity();
        Favorite favorite = favoriteRepository.findByUserIdAndPropertyId(user.getId() ,propertyId)
                .orElseThrow(() -> new RuntimeException("Favorite not found"));
        favoriteRepository.delete(favorite);        
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(Long propertyId) {
        return favoriteRepository.existsByUserIdAndPropertyId(
            currentUserService.getCurrentUser().getId(),
            propertyId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteDTO> getMyFavorites() {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(currentUserService.getCurrentUser().getId())
            .stream().map(favoriteMapper::toDTO).toList();
    }
}
