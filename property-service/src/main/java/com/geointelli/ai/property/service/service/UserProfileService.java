package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.dto.UserPreferencesDTO;
import com.geointelli.ai.property.service.dto.UserProfileUpdateRequest;

/** Manages the current user's local profile and research preferences. */
public interface UserProfileService {
    /**
     * Updates supplied profile fields without changing identity-provider credentials.
     * @param request fields to update; null fields are ignored
     * @return the updated profile
     */
    UserDTO updateProfile(UserProfileUpdateRequest request);

    /**
     * Reads saved preferences, or empty defaults if none have been saved.
     * @return the current user's preferences
     */
    UserPreferencesDTO getPreferences();

    /**
     * Replaces the current user's preferences.
     * @param request complete preferences; omitted budgets are cleared and omitted lists become empty
     * @return the saved preferences
     * @throws IllegalArgumentException if the minimum budget exceeds the maximum
     */
    UserPreferencesDTO updatePreferences(UserPreferencesDTO request);
}
