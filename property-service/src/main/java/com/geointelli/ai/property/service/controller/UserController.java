package com.geointelli.ai.property.service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.dto.UserPreferencesDTO;
import com.geointelli.ai.property.service.dto.UserProfileUpdateRequest;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Exposes the profile and preferences of the current user. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final CurrentUserService currentUserService;
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser() {
        return ResponseEntity.ok(currentUserService.getCurrentUser());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(request));
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<UserPreferencesDTO> getPreferences() {
        return ResponseEntity.ok(userProfileService.getPreferences());
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<UserPreferencesDTO> updatePreferences(@Valid @RequestBody UserPreferencesDTO request) {
        return ResponseEntity.ok(userProfileService.updatePreferences(request));
    }
}
