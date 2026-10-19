package com.geointelli.ai.property.service.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {
    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    public UserDTO getCurrentUser() {
        Jwt jwt = getCurrentJwt();
        return userService.getOrCreateUser(jwt);
    }

    @Override
    public String getCurrentKeycloakUserId() {
        return getCurrentJwt().getSubject();
    }

    public Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "A bearer token is required to identify the current user");
        }
        return jwt;
    }

    @Override
    public User getCurrentUserEntity() {
        Jwt jwt = getCurrentJwt();
        userService.getOrCreateUser(jwt);
        String keycloakId = jwt.getSubject();
        return userRepository.findByKeycloakUserId(keycloakId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    
}
