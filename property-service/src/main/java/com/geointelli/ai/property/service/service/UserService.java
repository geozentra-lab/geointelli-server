package com.geointelli.ai.property.service.service;


import org.springframework.security.oauth2.jwt.Jwt;

import com.geointelli.ai.property.service.dto.UserDTO;

public interface UserService {
    UserDTO getOrCreateUser(Jwt jwt);

    /**
     * The account that uses this email, ignoring case (the newest one when old rows share it). An account exists
     * here once its owner has signed in to GeoZentra at least once.
     * @throws IllegalArgumentException (400) for a missing or malformed email
     * @throws org.springframework.web.server.ResponseStatusException 404 when no account uses it
     */
    UserDTO findByEmail(String email);
}
