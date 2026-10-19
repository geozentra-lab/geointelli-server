package com.geointelli.ai.property.service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.FavoriteDTO;
import com.geointelli.ai.property.service.service.FavoriteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping("/{propertyId}/favorite")
    public ResponseEntity<FavoriteDTO> addFavorite(@PathVariable Long propertyId) {
        return ResponseEntity.ok(favoriteService.addFavorite(propertyId));
    }

    @DeleteMapping("/{propertyId}/favorite")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long propertyId) {
        favoriteService.removeFavorite(propertyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{propertyId}/favorite")
    public ResponseEntity<Boolean> isFavorite(@PathVariable Long propertyId) {
        return ResponseEntity.ok(favoriteService.isFavorite(propertyId));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<FavoriteDTO>> getMyFavorites() {
        return ResponseEntity.ok(favoriteService.getMyFavorites());
    }
}    