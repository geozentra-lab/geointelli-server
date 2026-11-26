package com.geointelli.ai.property.service.service;


import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.dto.UserSuggestionDTO;

public interface UserService {
    UserDTO getOrCreateUser(Jwt jwt);

    /**
     * The account that uses this email, ignoring case (the newest one when old rows share it). An account exists
     * here once its owner has signed in to GeoZentra at least once.
     * @throws IllegalArgumentException (400) for a missing or malformed email
     * @throws org.springframework.web.server.ResponseStatusException 404 when no account uses it
     */
    UserDTO findByEmail(String email);

    /**
     * Up to {@code size} accounts whose email, full name or last name starts with {@code q}, case ignored, by email;
     * the newest row of each email only. For suggestions while an admin types.
     * @throws IllegalArgumentException (400) when {@code q} has under 2 or over 254 characters, or {@code size} is
     *         outside 1 to 25
     */
    List<UserSuggestionDTO> search(String q, int size);
}
